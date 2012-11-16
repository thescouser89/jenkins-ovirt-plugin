package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.ovirt.engine.api.model.API;
import org.ovirt.engine.api.model.ProductInfo;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.Templates;
import org.ovirt.engine.api.model.Version;

public class OVirtEngineCloud extends Cloud
{

    private String url;
    private String username;
    private String password;
    
    private transient OVirtEngineClient client;
    
    @DataBoundConstructor
    public OVirtEngineCloud(String name,
                            String url,
                            String username,
                            String password)
    {
        super(name);
        this.url = url;
        this.username = username;
        this.password = password;                
    }
    
    public OVirtEngineClient getClient()
    {
        if(this.client == null)
        {
            this.client = new OVirtEngineClient(this.url, this.username, this.password, this.name);
        }
        return this.client;
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
    
    public List<Template> getTemplates() throws Exception
    {
        return this.getClient().get("templates", Templates.class).getTemplates();
    }
    
    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean canProvision(Label label)
    {
        String msg = "Label: " + label.getExpression();
        System.out.println(msg);
        OVirtEnginePlugin.getLogger().severe(msg);
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
                OVirtEnginePlugin.getLogger().log(Level.SEVERE, null, e);
                return FormValidation.error("Client error <"+e.getClass().getName()+">: "+e.getMessage());
            }
        }
    }
    
        public OVirtEngineSlave createSlave(Template tmp) throws Exception
        {
            return OVirtEngineSlave.provision(client, tmp);
        }
        
        public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String templateName) throws Exception {
        //checkPermission(PROVISION);
        if(templateName == null) {
            sendError("The 'templateName' query parameter is missing",req,rsp);
            return;
        }
        Template tmp = client.getTemplate(templateName);
        
        OVirtEngineSlave node = createSlave(tmp);
        Hudson.getInstance().addNode(node);
        rsp.sendRedirect2(req.getContextPath()+"/computer/"+node.getNodeName());
    }
    
}