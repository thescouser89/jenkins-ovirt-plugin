package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineEntityNotFound;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineRequestFailed;
import org.kohsuke.stapler.DataBoundConstructor;
import org.ovirt.engine.api.model.VM;


public class OVirtEngineSlave extends Slave {
    
    protected static final Logger LOGGER = OVirtEnginePlugin.getLogger();
    private static final String REMOTE_FS = "/var/lib/jenkins";
    
    private String id;
    
    //@DataBoundConstructor
    public OVirtEngineSlave(String name, String nodeDescription, String label)
            throws FormException, IOException
    {
        super(name, nodeDescription, REMOTE_FS, 1, Mode.NORMAL, label, null, RetentionStrategy.INSTANCE);
        id = null;
    }
    
    @DataBoundConstructor
    public OVirtEngineSlave(String name, String nodeDescription,
                            String remoteFS, int numExecutors,
                            String labelString, ComputerLauncher launcher,
                            List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, Mode.NORMAL, labelString, launcher, RetentionStrategy.INSTANCE, nodeProperties);
        id = null;
    }
    
    @Override
    public OVirtEngineComputer createComputer() {
        return new OVirtEngineComputer(this);
    }
    
      
    public void remove() throws OVirtEngineRequestFailed
    {
        if(isExist())
        {
            getClient().deleteVm(name);
        }
    }
    
    public boolean isExist() throws OVirtEngineRequestFailed
    {
        try
        {
            getClient().getVm(name);
        }catch (OVirtEngineEntityNotFound ex)
        {
            return false;
        }
        return true;
    }
    
    public String getId() throws OVirtEngineRequestFailed
    {
        if(id == null)
        {
            id = getClient().getVm(name).getId();
        }
        return id;
    }
    
    public String getStatus() throws OVirtEngineRequestFailed
    {
        return getClient().getVm(name).getStatus().getState();
    }
    
    public void start() throws OVirtEngineRequestFailed
    {
        VM vm = new VM();
        vm.setId(getId());
        getClient().startVm(vm, true);
    }
    
    public void stop() throws OVirtEngineRequestFailed
    {
        VM vm = new VM();
        vm.setId(getId());
        getClient().stopVm(vm, true);
    }
    
    public String getCloudName()
    {
        Matcher m = Pattern.compile("^([^-]+).*$", Pattern.CASE_INSENSITIVE).matcher(name);
        if(!m.matches())
        {
            throw new RuntimeException("can not resolve cloud name from " + name);
        }
        return  m.group(1);
    }
    
    public OVirtEngineCloud getCloud()
    {
        return OVirtEngineCloud.get(getCloudName());
    }
    
    public OVirtEngineClient getClient()
    {
        return  getCloud().getClient();
    }
    
    public List<String> getIPs() throws OVirtEngineRequestFailed
    {
        LinkedList<String> valid = new LinkedList<String>();
        List<String> ips = getClient().getVmIPs(name);
        for(String ip: ips)
        {
            try
            {
                InetAddress inet = InetAddress.getByName(ip);
                inet.isReachable(5000); // TODO make it configurable
            }catch(UnknownHostException ex)
            {
                LOGGER.log(Level.WARNING, "Invalid ip address "+ip+" for machine "+name, ex);
                ips.remove(ip);
            }
            catch(IOException ex)
            {
                LOGGER.log(Level.WARNING, "Ip address "+ip+" for machine "+name+" is not reachable", ex);
            }
            valid.add(ip);
        }
        return valid;
    }
    
    @Extension
    public static final class DescriptorImpl extends DumbSlave.SlaveDescriptor {
        @Override
		public String getDisplayName() {
            return "Ovirt Engine";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

        @Override
        public String getConfigPage() {
            return getViewPage(DumbSlave.class, "configure-entries.jelly");
        }
        
    }
    
}