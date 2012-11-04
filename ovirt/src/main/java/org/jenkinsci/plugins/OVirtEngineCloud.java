package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import javax.xml.bind.JAXBException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.Templates;

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
            this.client = new OVirtEngineClient(this.url, this.username, this.password);
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
    
    private Collection<Template> getAvaiableTemplates() throws IOException, JAXBException, OVirtEngineException
    {
        OVirtEngineClient cl = this.getClient();
        return cl.get("templates", Templates.class).getTemplates();
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud>
    {

        @Override
        public String getDisplayName()
        {
            return "oVirt Engine";
        }
        public FormValidation doTestConnection(
                @QueryParameter("url") final String url,
                @QueryParameter("username") final String username,
                @QueryParameter("password") final String password)
        {
            try
            {
                OVirtEngineClient client = new OVirtEngineClient(url, username, password);
                client.isConnective();
                return FormValidation.ok("Success");
            } catch (Exception e)
            {
                OVirtEnginePlugin.getLogger().log(Level.SEVERE, null, e);
                return FormValidation.error("Client error <"+e.getClass().getName()+">: "+e.getMessage());
            }
        }
    }
    
}