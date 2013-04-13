package org.jenkinsci.plugins;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineRequestFailed;
import org.jenkinsci.plugins.OVirtEngineProvisionStrategy.ProvisionStrategyError;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.ovirt.engine.api.model.API;
import org.ovirt.engine.api.model.ProductInfo;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.VM;
import org.ovirt.engine.api.model.Version;

//Jenkins.instance.getDescriptorList(ComputerLauncher.class)

public class OVirtEngineCloud extends Cloud implements Describable<Cloud> 
{

    protected static final Logger LOGGER = OVirtEnginePlugin.getLogger();
    
    private String url;
    private String username;
    private String password;
    
    private OVirtEngineProvisionStrategy strategy;
    private ComputerLauncher launcher;
    
    private transient OVirtEngineClient client;
    
    @DataBoundConstructor
    public OVirtEngineCloud(String name,
                            String url,
                            String username,
                            String password,
                            OVirtEngineProvisionStrategy strategy,
                            OVirtEngineLauncher launcher)
    {
        super(name);
        this.url = url;
        this.username = username;
        this.password = password;
        this.strategy = strategy;
        this.launcher = launcher;
    }
    
    public OVirtEngineProvisionStrategy getStrategy()
    {
        if(strategy == null)
        {
            strategy = OVirtEngineProvisionStrategy.DEFAULT;
        }
        return strategy;
    }
    
    public ExtensionList<OVirtEngineProvisionStrategy> getStrategies()
    {
        ExtensionList<OVirtEngineProvisionStrategy> list = Hudson.getInstance().getExtensionList(OVirtEngineProvisionStrategy.class);
        LOGGER.log(Level.FINE, "Strategy size: {0}", list.size());
        for (OVirtEngineProvisionStrategy s: list)
        {
            LOGGER.log(Level.FINE, "Strategy: {0}", s.getClass().getName());
        }
        return list;
    }
    
    public OVirtEngineClient getClient()
    {
        if(client == null)
        {
            client = new OVirtEngineClient(this.url, this.username, this.password, this.name);
        }
        return client;
    }

    public String getUrl()
    {
        return url;
    }

    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }
    
    public List<Template> getTemplates() throws OVirtEngineRequestFailed
    {
        return this.getClient().getTemplates();
    }
    
    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload)
    {
        List<PlannedNode> list = new ArrayList<PlannedNode>();

        final Template tm = getTemplate(label);
        if(tm == null)
        {
            return list;
        }

        for( ; excessWorkload > 0; excessWorkload-- )
        {
            final String machineName = generateMachineName(tm.getName());
            list.add(new PlannedNode(machineName,
                    Computer.threadPoolForRemoting.submit(new Callable<Node>()
                    {
                        public Node call() throws Exception
                        {
                            OVirtEngineSlave slave = createSlave(machineName, tm);
                            Hudson.getInstance().addNode(slave);
                            slave.toComputer().connect(false).get();
                            return slave;
                        }
                    })
                    , 1)); // TODO: make it configurable
        }
        return list;
    }

    public Template getTemplate(Label label)
    {
        String l = "";
        Set<LabelAtom> set;
        try
        {
            for(Template tm: getTemplates())
            {
                 set = Label.parse(tm.getName());
                 if(label.matches(set))
                 {
                     return tm;
                 }
            }
            
        }catch(OVirtEngineRequestFailed ex){}
        return null;
    }
    
    @Override
    public boolean canProvision(Label label)
    {
        LOGGER.log(Level.SEVERE, "Label: {0}", label.getExpression());
        if(getTemplate(label) != null)
        {
            return true;
        }
        return false;
    }
    
    public static OVirtEngineCloud get()
    {
        return Hudson.getInstance().clouds.get(OVirtEngineCloud.class);
    }

    public static OVirtEngineCloud get(String name)
    {
        return (OVirtEngineCloud) Hudson.getInstance().clouds.getByName(name);
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud>
    {
        @Override
        public String getDisplayName()
        {
            return "oVirt Engine";
        }
        
        public FormValidation doCheckName(@QueryParameter("name") final String name)
        {
            String regex = "[._a-z0-9]+";
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name);
            if(m.matches())
            {
                return FormValidation.ok();
            }
            return FormValidation.error("Cloud name allows only: "+regex);
        }
        
        public FormValidation doTestConnection(
                @QueryParameter("url") final String url,
                @QueryParameter("username") final String username,
                @QueryParameter("password") final String password)
        {
            try
            {
                OVirtEngineClient client = new OVirtEngineClient(url, username, password, null);
                API api = client.get(API.class);
                ProductInfo info = api.getProductInfo();
                Version version = info.getVersion();
                String msg = String.format(
                        "Successfully connected to: %s (%d.%d)",
                        info.getName(), version.getMajor(), version.getMinor());
                return FormValidation.ok(msg);
            } catch (Exception e)
            {
                OVirtEngineCloud.LOGGER.log(Level.SEVERE, null, e);
                return FormValidation.error("Client error <"+e.getClass().getName()+">: "+e.getMessage());
            }
        }
        
        public DescriptorExtensionList<OVirtEngineProvisionStrategy, ?> getStrategies()
        {
            return Hudson.getInstance().getDescriptorList(OVirtEngineProvisionStrategy.class);
        }
        
        public DescriptorExtensionList<ComputerLauncher, ?> getLaunchers()
        {
            return Hudson.getInstance().getDescriptorList(ComputerLauncher.class);
        }
        
    }
    
    private String generateMachineName(String templateName)
    {
        String machineName = String.format("%s-%s-%s", getClient().getCloud().name, templateName, UUID.randomUUID().toString());
        if(machineName.length() > 54) // BUG in oVirt guest agent
        {
            machineName = machineName.substring(0, 54);
        }
        return machineName;
    }
        
    public OVirtEngineSlave createSlave(String name, Template tmp) throws OVirtEngineRequestFailed, ProvisionStrategyError, FormException, IOException
    {
        VM vm = new VM();
        vm.setTemplate(tmp);
        vm.setName(name);
        getStrategy().configureVm(this, vm);
        vm = getClient().createVm(vm);
        OVirtEngineSlave slave = new OVirtEngineSlave(vm.getName(), vm.getDescription(), tmp.getName());
        slave.setLauncher(launcher);
        slave.getDescriptor().save();
        return slave;
    }

    public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String templateName) throws Exception
    {
        checkPermission(PROVISION);
        if(templateName == null) {
            sendError("The 'templateName' query parameter is missing", req, rsp);
            return;
        }
        Template tmp = getClient().getTemplate(templateName);

        OVirtEngineSlave node = createSlave(generateMachineName(tmp.getName()), tmp);
        Hudson.getInstance().addNode(node);
        rsp.sendRedirect2(req.getContextPath()+"/computer/"+node.getNodeName());
    }
    
}