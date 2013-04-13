package org.jenkinsci.plugins;

import java.io.IOException;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.Templates;
import org.ovirt.engine.api.model.VM;
import org.ovirt.engine.api.model.VMs;


public class OVirtEngineClientTest
{
    // TODO: move it into System.getProperty()
    private static final String URL = "https://lb-rhset1.rhev.lab.eng.brq.redhat.com/api";
    private static final String USER = "admin@internal";
    private static final String PASS = "123456";
    private OVirtEngineClient client;
    
    private static final String BLANK_TEMPLATE = "00000000-0000-0000-0000-000000000000";
    
    @BeforeClass
    public static void setUpClass() throws IOException
    {
        Logger log = OVirtEnginePlugin.getLogger();
        log.setLevel(Level.ALL);
        //log.addHandler(new FileHandler(PATH_TO_LOG));
        log.addHandler(new FileHandler("/tmp/a.log"));
    }
    
    @Before
    public void setUp()
    {
        client = new OVirtEngineClient(URL, USER, PASS, null);
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
    
    @Test
    public void testPathComposer() throws Exception
    {
        Template t = new Template();
        t.setId(BLANK_TEMPLATE);
        String path = OVirtEngineClient.composePath("templates/{id}", t);
        System.out.println("Got path: " + path);
        if (!("templates/"+BLANK_TEMPLATE).equals(path))
        {
            throw new Exception("It doesn't match");
        }
    }
    
    @Test
    public void testGetBlankTemplate() throws Exception
    {
        final String blank = "Blank";
        Template tm = new Template();
        tm.setId(BLANK_TEMPLATE);
        //tm = client.getTemplate(tm);
        tm = client.getElement(tm);
        System.out.println("Expected "+blank+": " + tm.getName());
        if (!blank.equals(tm.getName()))
        {
            throw new Exception("It doesn't match");
        }
        tm = client.getElement(blank, Templates.class);
        System.out.println("Expected "+blank+": " + tm.getName());
        if (!blank.equals(tm.getName()))
        {
            throw new Exception("It doesn't match");
        }
    }
    
    @Test
    public void testGetCollection() throws Exception
    {
        for (Template tm: client.getTemplates())
        {
            System.out.println("Template name: " + tm.getName());
        }
        for (VM vm: (List<VM>)client.getCollection(VMs.class))
        {
            System.out.println("Template name: " + vm.getName());
        }
    }
    
    @Test
    public void testMarshall() throws Exception
    {
        VM vm = new VM();
        vm.setName("mashine");
        Template tm = new Template();
        tm.setName("rhel6");
        vm.setTemplate(tm);
        System.out.println(client.marshal(vm));
    
    }
/*
    @Test
    @SuppressWarnings("SleepWhileInLoop")
    public void testCreateVm() throws Exception
    {
        String machine = "masina";
        VM vm;
        try
        {
            vm = client.getElement(machine, VMs.class);
            client.deleteVm(vm.getName());
        }catch (OVirtEngineEntityNotFound ex){}
        vm = new VM();
        vm.setName(machine);
        Template tm = new Template();
        //tm.setName("rhel");
        tm.setName("jenkins-slave");
        vm.setTemplate(tm);
        vm = client.createVm(vm);
        VM vmToStart = new VM();
        vmToStart.setId(vm.getId());
        client.startVm(vmToStart, true);
        for( String ip : client.getVmIPs(machine))
        {
            System.out.println("vm ip: " + ip);
        }
        client.stopVm(vmToStart, true);
        client.deleteVm(machine);
    }*/

    /*
    @Test
    public void testLoadStrategies() throws Exception
    {
        ExtensionList<OVirtEngineProvisionStrategy> list = Jenkins.getInstance().getExtensionList(OVirtEngineProvisionStrategy.class);
        System.out.println("Extension list: " + list.toString());
    }
*/
}