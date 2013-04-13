/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins;

import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.OVirtEngineClient.OVirtEngineRequestFailed;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author lukyn
 */
public class OVirtEngineComputer extends SlaveComputer
{

    protected static final Logger LOGGER = OVirtEnginePlugin.getLogger();
    
    public OVirtEngineComputer(OVirtEngineSlave slave)
    {
        super(slave);
    }

    public OVirtEngineComputer(Slave slave) {
        super(slave);
    }
    
    public boolean isRunning() throws IOException
    {
        String status;
        try
        {
            status = getNode().getStatus();
        }catch(OVirtEngineRequestFailed ex)
        {
            LOGGER.log(Level.SEVERE, "can not get status of computer: " + getNode().getNodeName(), ex);
            throw new IOException(ex.toString());
        }
        return status != null && status.compareTo("up") == 0;
    }
    
    public String getConnectiveIP(int port)
    {
        try
        {
            for(String ip: getIPs())
            {
                if(isConnective(ip, port))
                {
                    return ip;
                }
            }
        }catch(IOException ex){}
        return null;
    }
    
    public boolean isConnective(String host, int port)
    {
        Socket socket = null;
        try {
            InetSocketAddress addr = new InetSocketAddress(host, port);
            socket = new Socket();
            socket.connect(addr, 3000);
            return socket.isConnected();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, getNode().getNodeName()+" is not connective on "+host+":"+String.valueOf(port) , ex);
        }
        finally
        {
            if(socket != null && socket.isConnected())
            {
                try
                {
                    socket.close();
                }catch(IOException ex){}
            }
        }
        return false;
    }
    
    public List<String> getIPs() throws IOException
    {
        try
        {
            return getNode().getIPs();
        }catch(OVirtEngineRequestFailed ex)
        {
            LOGGER.log(Level.SEVERE, "can not get IPs of computer: " + getNode().getNodeName(), ex);
            throw new IOException(ex.toString());
        }
    }
    
    public void start() throws IOException
    {
        String status;
        OVirtEngineSlave slave = this.getNode();
        if(slave != null)
        {
            try
            {
                status = slave.getStatus();
                if(status.compareTo("down") == 0 ||
                    status.compareTo("paused") == 0) // TODO: add another states
                {
                    slave.start();
                }
            }catch(OVirtEngineRequestFailed ex)
            {
                LOGGER.log(Level.SEVERE, "can not start computer: " + slave.getNodeName(), ex);
                throw new IOException(ex.toString());
            }
        }
    }
    
    public void stop() throws IOException
    {
        OVirtEngineSlave slave = this.getNode();
        try
        {
            if(slave != null && slave.getStatus().compareTo("down") != 0)
            {
                slave.stop();
            }
        }catch(OVirtEngineRequestFailed ex)
        {
            LOGGER.log(Level.SEVERE, "can not stop computer: " + slave.getNodeName(), ex);
            throw new IOException(ex.toString());
        }
    }

    private void remove() throws IOException
    {
        OVirtEngineSlave slave = getNode();
        if(slave == null)
        {
            return;
        }
        try
        {
            if(slave.isExist())
            {
                stop();
                slave.remove();
            }
        }catch(OVirtEngineRequestFailed ex)
        {
            LOGGER.log(Level.SEVERE, "can not remove computer: " + slave.getNodeName(), ex);
            throw new IOException(ex.toString());
        }

    }
    
    @Override
    public HttpResponse doDoDelete() throws IOException
    {
        checkPermission(DELETE);
        try {
            remove();
        }catch (Exception ex)
        {
            LOGGER.log(Level.SEVERE, "can not remove slave from cloud", ex);
            throw new IOException(ex.toString());
        }
        return super.doDoDelete();
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
        return true; //NOTE: unix only
    }
    
    @Override
    public OVirtEngineSlave getNode()
    {
        return (OVirtEngineSlave)super.getNode();
    }

    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    @Override
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        JSONObject json = req.getSubmittedForm();
        /*json.getJSONObject("launcher").put("host", "moje.pekna.ip.adresa");*/
        LOGGER.fine(json.toString());
        super.doConfigSubmit(req, rsp);
    }
    
}