/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import java.util.logging.Logger;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineException;
import org.jenkinsci.plugins.strategy.StaticProvisionStrategy;
import org.ovirt.engine.api.model.VM;

/**
 *
 * @author lbednar
 */
public abstract class OVirtEngineProvisionStrategy
                extends AbstractDescribableImpl<OVirtEngineProvisionStrategy>
                implements Describable<OVirtEngineProvisionStrategy>, ExtensionPoint
{
    protected static final Logger LOGGER = OVirtEnginePlugin.getLogger();
    
    public static final OVirtEngineProvisionStrategy DEFAULT = new StaticProvisionStrategy();

    public abstract void configureVm(OVirtEngineCloud cloud, VM vm) throws ProvisionStrategyError;

    public static class ProvisionStrategyError extends OVirtEngineException
    {

        public ProvisionStrategyError(String message) {
            super(message);
        }
        
    }
}


