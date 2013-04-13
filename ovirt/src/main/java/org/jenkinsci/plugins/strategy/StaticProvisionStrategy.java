/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.strategy;

import hudson.Extension;
import hudson.model.Descriptor;
import java.util.List;
import java.util.logging.Level;
import org.jenkinsci.plugins.OVirtEngineClient;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineEntityNotFound;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineException;
import org.jenkinsci.plugins.OVirtEngineCloud;
import org.jenkinsci.plugins.OVirtEngineProvisionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;
import org.ovirt.engine.api.model.Cluster;
import org.ovirt.engine.api.model.Clusters;
import org.ovirt.engine.api.model.VM;

/**
 *
 * @author lbednar
 */
@Extension
public class StaticProvisionStrategy extends OVirtEngineProvisionStrategy
{
    
    private String clusterName;
    private String dataCenterName;

    public StaticProvisionStrategy() {
        clusterName = "Default";
        dataCenterName = "Default";
    }
    
    @DataBoundConstructor
    public StaticProvisionStrategy(String clusterName, String dataCenterName)
    {
        this.clusterName = clusterName;
        this.dataCenterName = dataCenterName;
    }
    
    protected Cluster selectCluster(OVirtEngineCloud cloud, VM vm)throws OVirtEngineException
    {
        OVirtEngineClient client = cloud.getClient();
        Cluster cluster = vm.getCluster();
        if (cluster == null)
        {
            try
            {
                cluster = client.getCluster(clusterName);
            }catch(OVirtEngineEntityNotFound ex)
            {
                List<Cluster> clusters = client.getCollection(Clusters.class);
                if(clusters.isEmpty())
                {
                    throw new ProvisionStrategyError("no cluster found in " + cloud.name);
                }
                cluster = clusters.get(0);
            }
            
        }
        return cluster;
    }
    
    @Override
    public void configureVm(OVirtEngineCloud cloud, VM vm)throws ProvisionStrategyError
    {
        try
        {
            vm.setCluster(selectCluster(cloud, vm));
        }catch(OVirtEngineException ex)
        {
            LOGGER.log(Level.SEVERE, "Can not select cluster", ex);
            throw new ProvisionStrategyError(ex.toString());
        }
        // TODO: add datacenter
    }
    
    

    @Extension
    public static final class DescriptorImpl extends Descriptor<OVirtEngineProvisionStrategy>
    {
        public DescriptorImpl(){}
        
        public DescriptorImpl(Class <? extends OVirtEngineProvisionStrategy> clazz)
        {
            super(clazz);
        }

        public Class<? extends OVirtEngineProvisionStrategy> getClazz() {
            return clazz;
        }
        
        @Override
        public String getDisplayName() {
            return "Use Static resources";
        }
    }
}
