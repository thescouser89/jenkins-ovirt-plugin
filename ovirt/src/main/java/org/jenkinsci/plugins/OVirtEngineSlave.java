package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundConstructor;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.VM;


public class OVirtEngineSlave extends Slave {
    
/*    public OVirtEngineSlave(String name, String nodeDescription,
                            String remoteFS, int numExecutors,
                            String labelString, ComputerLauncher launcher,
                            List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, Mode.NORMAL, labelString, launcher, RetentionStrategy.INSTANCE, nodeProperties);
    }*/
    
    @DataBoundConstructor
    public OVirtEngineSlave(String name, String nodeDescription,
                            String remoteFS, int numExecutors,
                            String labelString, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {

        super(name, nodeDescription, remoteFS, numExecutors, Mode.EXCLUSIVE,
                labelString, new OVirtEngineLauncher(),
                RetentionStrategy.INSTANCE, nodeProperties);
    }
    
    @Override
    public OVirtEngineComputer createComputer() {
        return (OVirtEngineComputer) super.createComputer();
    }
    
    public static OVirtEngineSlave provision(OVirtEngineClient client, Template tm) throws Exception
    {
        VM vm = new VM();
        vm.setTemplate(tm);
        vm.setName(client.getCloud().name+'-'+tm.getName()+"-instance"); // TODO: create unique name
        vm = client.createVm(vm);
        OVirtEngineSlave slave = new OVirtEngineSlave(vm.getName(), vm.getDescription(), "/var/lib/jenkins", 1, null, null);
        return slave;
    }
    
    public void start() throws Exception
    {
        OVirtEngineClient client = getClient();
        VM vm = client.getElement(name, VM.class);
        client.startVm(vm, true);
    }
    
    public void stop() throws Exception
    {
        OVirtEngineClient client = getClient();
        VM vm = client.getElement(name, VM.class);
        client.stopVm(vm, true);
    }
    
    public OVirtEngineCloud getCloud()
    {
        Matcher m = Pattern.compile("^([^-]+)", Pattern.CASE_INSENSITIVE).matcher(name);
        return OVirtEngineCloud.get(m.group(0));
    }
    
    public OVirtEngineClient getClient()
    {
        return  getCloud().getClient();
    }
    
    @Extension
    public static final class DescriptorImpl extends Slave.SlaveDescriptor {
        @Override
		public String getDisplayName() {
            return "Ovirt Engine";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
    
}