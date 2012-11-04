package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.Plugin;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class OVirtEnginePlugin extends Plugin implements Describable<OVirtEnginePlugin>
{

    private final static String PATH_TO_LOG = "/var/log/jenkins/ovirt.log";
    private final static String LOG_NAME = "OVirtEnginePlugin";
    
    @Override
    public void configure(StaplerRequest req, JSONObject formData) throws IOException, ServletException, FormException {
        super.configure(req, formData);
        Logger log = OVirtEnginePlugin.getLogger();
        log.setLevel(Level.ALL);
        log.addHandler(new FileHandler(PATH_TO_LOG));
    }
    
    public static Logger getLogger()
    {
        return Logger.getLogger(LOG_NAME);
    }
    
    public Descriptor<OVirtEnginePlugin> getDescriptor()
    {
        return (DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(getClass());
    }
    
    @Extension                                                                  
    public static final class DescriptorImpl extends Descriptor<OVirtEnginePlugin>
    {
        @Override
        public String getDisplayName() {
            return "oVirt Engine plugin";
        }
    }
}
