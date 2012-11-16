/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;

/**
 *
 * @author lukyn
 */
public class OVirtEngineLauncher extends ComputerLauncher {

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        super.launch(computer, listener);
    }
    
    
}
