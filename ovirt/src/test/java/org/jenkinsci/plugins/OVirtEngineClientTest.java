package org.jenkinsci.plugins;

import org.junit.Before;
import org.junit.Test;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.Templates;
import org.ovirt.engine.api.model.VM;
import org.ovirt.engine.api.model.VMs;


public class OVirtEngineClientTest
{

    private static final String URL = "https://lb-rhset1.rhev.lab.eng.brq.redhat.com/api";
    private static final String USER = "admin@internal";
    private static final String PASS = "123456";
    private OVirtEngineClient client;
    
    @Before
    public void setUp()
    {
        client = new OVirtEngineClient(URL, USER, PASS);
    }
    
    @Test
    public void testConnection() throws Exception
    {
        client.isConnective();
    }
    
    @Test
    public void testing() throws Exception
    {
        VMs vms = client.get("vms", VMs.class);
        for(VM vm: vms.getVMs())
        {
            System.out.println("VM name: " + vm.getName());
        }

        Templates tmps = client.get("templates", Templates.class);
        for(Template tmp: tmps.getTemplates())
        {
            System.out.println("Template name: " + tmp.getName());
        }
    }
}