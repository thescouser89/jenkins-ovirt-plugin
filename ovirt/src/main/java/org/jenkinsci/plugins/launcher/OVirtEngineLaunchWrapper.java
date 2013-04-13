/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.launcher;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.OVirtEngineLauncher;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lukyn
 */
//@Extension
public class OVirtEngineLaunchWrapper extends OVirtEngineLauncher {
        
    private ComputerLauncher launcher;

    public OVirtEngineLaunchWrapper() {
        launcher = null;
    }
    
    //@DataBoundConstructor
    public OVirtEngineLaunchWrapper(ComputerLauncher launcher) {
        this.launcher = launcher;
    }
    
    public ComputerLauncher getLauncher() {
        return launcher;
    }
        
    //@Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher>
    {
        public DescriptorImpl()
        {
            super();
            load();
        }
        
        public DescriptorImpl(Class<? extends ComputerLauncher> clazz)
        {
            super(clazz);
            load();
        }
        @Override
        public String getDisplayName() {
            return "OVirt Engine launch wrapper";
        }

        @Override
        public ComputerLauncher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            LOGGER.fine(formData.toString());
            return super.newInstance(req, formData);
        }
        
    }
}