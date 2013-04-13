/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.launcher;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.OVirtEngineComputer;
import org.jenkinsci.plugins.OVirtEngineLauncher;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lbednar
 */
@Extension
public class SimpleSSHLauncher extends OVirtEngineLauncher
{
    
    private String username;
    private String password;
    private String urlToJavaInstaller;
    
    private int port;
    
    private String downloadCommand;

    public SimpleSSHLauncher()
    {
        username = "root";
        password = "123456";
        port = 22;
        urlToJavaInstaller = "http://download.oracle.com/otn-pub/java/jdk/6u29-b11/jdk-6u29-linux-i586.bin";
        
        downloadCommand = null;
    }
    
    @DataBoundConstructor
    public SimpleSSHLauncher(String username, String password, int port, String urlToJavaInstaller) {
        this.username = username;
        this.password = password;
        this.urlToJavaInstaller = urlToJavaInstaller;
        this.port = port;

        this.downloadCommand = null;
    }
    
    

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener)
    {
        super.afterDisconnect(computer, listener);
        OVirtEngineComputer ocomp = OVirtEngineComputer.class.cast(computer);
        try
        {
            ocomp.stop();
        }catch(IOException ex)
        {
            LOGGER.log(Level.SEVERE, "Failed to stop machine " + ocomp.getName(), ex);
            listener.getLogger().println("Failed to stop machine " + ocomp.getName());
        }
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener)
    {
        super.beforeDisconnect(computer, listener);
    }

    @Override
    public boolean isLaunchSupported()
    {
        return true;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException
    {
        OVirtEngineComputer ocomp = OVirtEngineComputer.class.cast(computer);
        LOGGER.log(Level.FINE, "Launching {0}", ocomp.getName());
        
        ocomp.start(); // make sure that vm is running
        
        String host = ocomp.getConnectiveIP(port);
        if(host == null)
        {
            throw new IOException("can not get IP listening on "+String.valueOf(port)+" for "+ocomp.getName());
        }
        
        CommandExecutor executor = new CommandExecutor(host, username, password, listener);
        
        String urlToComputer = Hudson.getInstance().getRootUrl() + ocomp.getUrl();
        
        installJava(executor, listener);
        executeAgent(executor, listener, urlToComputer);
        
    }
    
    private String getAgentUrl()
    {
        return String.format("%sjnlpJars/slave.jar", Hudson.getInstance().getRootUrl());
    }
    
    private String getDownloadCommand(CommandExecutor executor, TaskListener listener) throws IOException
    {
        if(downloadCommand != null)
        {
            return downloadCommand;
        }
        try
        {
            listener.getLogger().println("Trying use wget");
            executor.execute("which wget", false);
            downloadCommand = "wget %s -O %s";
        }catch(CommandExecutionError ex)
        {
            listener.getLogger().println("Trying use curl");
            executor.execute("which curl", false);
            downloadCommand = "curl -L %s > %s";
        }
        return downloadCommand;
    }
    
    private void downloadUrl(CommandExecutor executor, TaskListener listener, String url, String target) throws IOException
    {
        executor.execute(String.format(getDownloadCommand(executor, listener), url, target), false);
    }
    
    private void installJava(CommandExecutor executor, TaskListener listener) throws IOException
    {
        String target = "sdk.bin";
        listener.getLogger().println("Check wheter java is avaiable");
        
        try
        {
            executor.execute("which java", false);
        }catch(CommandExecutionError ex)
        {
            downloadUrl(executor, listener, urlToJavaInstaller, target);
            executor.execute("sh "+target+" -noregister", false);
        }
    }
    
    private void executeAgent(CommandExecutor executor, TaskListener listener, String computerUrl) throws IOException
    {
        String target = "slave.jar";
        String  command = "nohup java -jar %s -jnlpUrl %s/slave-agent.jnlp &";
        downloadUrl(executor, listener, getAgentUrl(), target);
        
        executor.execute(String.format(command, target, computerUrl), false);
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher>
    {
        public DescriptorImpl(){}
        
        @Override
        public String getDisplayName()
        {
            return "OVirt Engine SSH launcher";
        }

        @Override
        public ComputerLauncher newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException
        {
            LOGGER.fine(formData.toString());
            return super.newInstance(req, formData);
        }
        
    }
    
    public class CommandExecutor
    {
        private final Session session;
        private final TaskListener listener;

        public CommandExecutor(String hostname, String username, String passwd,
                final TaskListener listener) throws IOException
        {
            JSch jsch = new JSch();
            try
            {
                session = jsch.getSession(username, hostname);
            }catch(JSchException ex)
            {
                throw new IOException();
            }
            session.setDaemonThread(true);
            session.setUserInfo(new UserInfo()
            {

                public void showMessage(String msg)
                {
                    listener.getLogger().println(msg);
                }

		public boolean promptYesNo(String msg)
                {
                    listener.getLogger().println(msg);
                    listener.getLogger().println("Y");
                    return true;
                }
		
                public boolean promptPassword(String msg)
                {
                    listener.getLogger().println(msg);
                    return true;
                }

		public boolean promptPassphrase(String msg)
                {
                    listener.getLogger().println(msg);
                    return true;
                }

                public String getPassword()
                {
                    return password;
                }

                public String getPassphrase()
                {
                    return null;
                }
            });	
            this.listener = listener;
        }
        
        public String execute(String command, boolean ignoreError) throws IOException
        {
            if(!session.isConnected())
            {
                try
                {
                    session.connect();
                }catch(JSchException ex)
                {
                    SimpleSSHLauncher.LOGGER.log(Level.SEVERE, "Can not initialize session", ex);
                    listener.getLogger().println(ex.toString());
                    throw new IOException(ex.toString());
                }
            }

            listener.getLogger().println("Executing " + command);
            final StringBuilder builder = new StringBuilder();
            ChannelExec channel = null;
            
            try
            {
                channel = (ChannelExec) session.openChannel("exec");
                final OutputStream out = new OutputStream()
                {
                    @Override
                    public void write(int b) throws IOException
                    {
                        listener.getLogger().append((char) b);
                        builder.append((char) b);
                    }
		};
                channel.setCommand(command);
                channel.setOutputStream(out);
                channel.setErrStream(out);
                channel.setInputStream(null);
                InputStream in = channel.getInputStream();
                channel.connect();
                IOUtils.copy(in, out);
                if(!ignoreError && channel.getExitStatus() != 0)
                {
                    throw new CommandExecutionError(command, channel.getExitStatus());
		}
                return builder.toString();
            }
            catch(JSchException ex)
            {
                SimpleSSHLauncher.LOGGER.log(Level.SEVERE, "Can not open channel", ex);
                listener.getLogger().println(ex.toString());
                throw new IOException(ex.toString());
            }
            finally
            {
                if(channel != null)
                {
                    channel.disconnect();
                }
            }
        }
    }
    
    public class CommandExecutionError extends IOException
    {
        public final int returnCode;

        public CommandExecutionError(String command, int returnCode)
        {
            super("Execution of " + command + "failed with "+String.valueOf(returnCode));
            this.returnCode = returnCode;
        }
        
    }
}


/*
 public class OVirtComputerLauncher extends ComputerLauncher {

	private final static Logger logger = Logger
			.getLogger(OVirtComputerLauncher.class.getName());

	@DataBoundConstructor
	public OVirtComputerLauncher(final String restApiUrl, final String apiUser,
			final String apiPass, final String agentUser, final String agentPass) {
		super();
		this.restApiUrl = restApiUrl;
		this.apiUser = apiUser;
		this.apiPass = apiPass;
		this.agentUser = agentUser;
		this.agentPass = agentPass;
	}

	private final String restApiUrl;
	private final String apiUser;
	private final String apiPass;

	private final String agentUser;
	private final String agentPass;

	@Override
	public boolean isLaunchSupported() {
		return true;
	}

	@Override
	public void launch(final SlaveComputer computer, final TaskListener listener)
			throws IOException, InterruptedException {
		logger.fine("Launching");
		final OVirtComputer oVirtComputer = (OVirtComputer) computer;
		Hudson.getInstance().getPlugin(OVirtPlugin.class);
		final OVirtService service = getService();
		VM vm = service.getVm(oVirtComputer.getId());
		int cntr = 0;
		while (!(vm.getStatus() == null || StringUtils.equals(vm.getStatus()
				.getState(), "up"))
				&& cntr < 100) {
			service.launch(oVirtComputer.getId());
			Thread.sleep(5000);
			listener.getLogger().println(
					"##\tVm is starting, status is " + vm.getStatus().getState());
			vm = service.getVm(oVirtComputer.getId());
			cntr++;
		}
		listener.getLogger().println(
				"##\tVm status is " + vm.getStatus().getState());

		try {
			JSch jsch = new JSch();
			final String addr = vm.getGuestInfo().getIp().getAddress();
			listener.getLogger().println("##\tssh connectiong to " + addr);
			final Session session = createSession(listener, jsch, addr);

			executeCommand(listener, session, "rm -rf jdk1.6.0_29", true);

			try {
				executeCommand(
						listener,
						session,
						"wget http://download.oracle.com/otn-pub/java/jdk/6u29-b11/jdk-6u29-linux-i586.bin -O jdk.bin", false);
			} catch(IOException e) {
				listener.getLogger().println("##\tWget failed, trying curl...");
				executeCommand(
						listener,
						session,
						"curl -L http://download.oracle.com/otn-pub/java/jdk/6u29-b11/jdk-6u29-linux-i586.bin > jdk.bin", false);
			}

			executeCommand(listener, session, "sh jdk.bin -noregister", false);

			try {
				executeCommand(listener, session, "wget "
						+ Hudson.getInstance().getRootUrl()
						+ "jnlpJars/slave.jar -O slave.jar", false);
			} catch (IOException e) {
				listener.getLogger().println("##\tWget failed, trying curl...");
				executeCommand(
						listener,
						session,
						"curl -L "
								+ Hudson.getInstance().getRootUrl()
								+ "jnlpJars/slave.jar > slave.jar", false);
			}

			executeCommand(listener, session,
					"nohup jdk1.6.0_29/bin/java -jar slave.jar -jnlpUrl "
							+ Hudson.getInstance().getRootUrl() + "computer/"
							+ computer.getName() + "/slave-agent.jnlp &", false);

			computer.setChannel(new NullInputStream(0), new NullOutputStream(),
					listener, new Listener() {
						public void onClosed(final Channel channel, final IOException cause) {
							listener.getLogger().println("##Closing channel to "+computer.getName());
							if(cause != null) {
								
							}
							session.disconnect();
						}
					});

			listener.getLogger().println("##Disconnecting from agent");

		} catch (JSchException e) {
			listener.getLogger().println("##Problem: " + e.getMessage());
			e.printStackTrace(listener.getLogger());
		}

	}

	private OVirtService getService() {
		return new OVirtService(restApiUrl, apiUser, apiPass);
	}

	Session createSession(final TaskListener listener, JSch jsch,
			final String addr) throws JSchException {
		final Session session = jsch.getSession(agentUser, addr);
		session.setDaemonThread(true);
		session.setUserInfo(new UserInfo() {

			public void showMessage(String msg) {
				listener.getLogger().println(msg);
			}

			public boolean promptYesNo(String msg) {
				listener.getLogger().println(msg);
				listener.getLogger().println(
						"##\toVirt jenkins plugin responded Y");
				return true;
			}

			public boolean promptPassword(String msg) {
				listener.getLogger().println(msg);
				return true;
			}

			public boolean promptPassphrase(String msg) {
				listener.getLogger().println(msg);
				return true;
			}

			public String getPassword() {
				return agentPass;
			}

			public String getPassphrase() {
				return null;
			}
		});
		session.connect();
		return session;
	}

	String executeCommand(final TaskListener listener, Session session,
			String command, boolean ignoreError) throws JSchException, IOException {
		listener.getLogger().println("##" + command);
		final StringBuilder builder = new StringBuilder();
		final ChannelExec channel = (ChannelExec) session.openChannel("exec");
		try {
			final OutputStream out = new OutputStream() {

				@Override
				public void write(int b) throws IOException {
					listener.getLogger().append((char) b);
					builder.append((char) b);
				}
			};
			channel.setCommand(command);
			channel.setOutputStream(out);
			channel.setErrStream(out);
			channel.setInputStream(null);
			InputStream in = channel.getInputStream();
			channel.connect();
			IOUtils.copy(in, out);
			if(!ignoreError && channel.getExitStatus() != 0) {
				throw new IOException("Returned error");
			}
			return builder.toString();
		} finally {
			channel.disconnect();
		}
	}

	@Override
	public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
		listener.getLogger().println("## shuting down computer "+computer.getName());
		final OVirtService service = getService();
		try {
			service.shutdown(((OVirtComputer)computer).getId());
		} catch (final IOException e) {
			e.printStackTrace(listener.getLogger());
		}
	}

	@Override
	public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
		// TODO Auto-generated method stub
		super.beforeDisconnect(computer, listener);
	}

	public String getRestApiUrl() {
		return restApiUrl;
	}

	public String getApiUser() {
		return apiUser;
	}

	public String getApiPass() {
		return apiPass;
	}

	public String getAgentUser() {
		return agentUser;
	}

	public String getAgentPass() {
		return agentPass;
	}

}

 */