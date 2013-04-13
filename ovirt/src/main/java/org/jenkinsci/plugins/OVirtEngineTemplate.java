/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

/**
 *
 * @author lbednar
 */
public class OVirtEngineTemplate implements Describable<OVirtEngineTemplate>
{
    
    private String name;
    private int memory;

    public Descriptor<OVirtEngineTemplate> getDescriptor()
    {
        return (OVirtEngineTemplate.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(getClass());
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<OVirtEngineTemplate>
    {
        @Override
        public String getDisplayName()
        {
            return "oVirt Engine template";
        }
    }
}
