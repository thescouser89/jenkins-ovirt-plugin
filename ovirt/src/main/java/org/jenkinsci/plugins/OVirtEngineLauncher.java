/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins;

import hudson.slaves.ComputerLauncher;
import java.util.logging.Logger;

/**
 *
 * @author lukyn
 */
public abstract class OVirtEngineLauncher extends ComputerLauncher {
    
    protected static final Logger LOGGER = OVirtEnginePlugin.getLogger();
    
}