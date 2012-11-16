/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins;

import hudson.slaves.SlaveComputer;

/**
 *
 * @author lukyn
 */
public class OVirtEngineComputer extends SlaveComputer {

    public OVirtEngineComputer(OVirtEngineSlave slave) {
        super(slave);
    }
    
    public void start()
    {
        OVirtEngineSlave slave = this.getNode();
    }
    
    public OVirtEngineCloud getCloud()
    {
        return getNode().getCloud();
    }
    
    public OVirtEngineClient getClient()
    {
        return getNode().getClient();
    }

    @Override
    public Boolean isUnix() {
        return true; //NOTE: unix only now
    }
    
    @Override
    public OVirtEngineSlave getNode()
    {
        return (OVirtEngineSlave)super.getNode();
    }
    

}
