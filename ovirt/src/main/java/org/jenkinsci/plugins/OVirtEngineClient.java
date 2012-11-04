package org.jenkinsci.plugins;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.ovirt.engine.api.model.API;
import org.ovirt.engine.api.model.Action;
import org.ovirt.engine.api.model.CPU;
import org.ovirt.engine.api.model.GuestInfo;
import org.ovirt.engine.api.model.IP;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.VM;
import org.ovirt.engine.api.model.VMs;
import org.ovirt.engine.api.model.VmStates;


class OVirtEngineException extends Exception {}


public class OVirtEngineClient
{
    private static final String CONTENT_TYPE = "application/xml";
    private static final String CHARSET = "utf-8"; // TODO: should be configurable
    
    private UsernamePasswordCredentials cred;
    private String url;
    private transient HttpClient client;
    private transient JAXBContext context;

    public OVirtEngineClient(String url, String user, String passwd)
    {
        if(url.endsWith("/")){
            this.url = url.substring(0, url.length() - 1);
        }
        this.url = url;
        this.cred = new UsernamePasswordCredentials(user, passwd);
        this.client = null;
    }
    
    /*public DefaultHttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }*/
    
    private HttpClient getClient()
    {
        if(this.client == null)
        {
            this.client = new HttpClient();
            this.client.getParams().setAuthenticationPreemptive(true);
            this.client.getState().setCredentials(AuthScope.ANY, this.cred);
        }
        return this.client;
    }
    
    private JAXBContext getContext() throws JAXBException
    {
        if(this.context == null)
        {
            this.context = JAXBContext.newInstance(
                    VM.class,
                    VMs.class,
                    Template.class,
                    CPU.class,
                    GuestInfo.class,
                    VmStates.class,
                    IP.class,
                    Action.class,
                    API.class);
        }
        return this.context;
    }
    
    private <T>T unmarshal(String in, Class<T> cls) throws JAXBException
    {
        OVirtEnginePlugin.getLogger().fine(in);
        //Unmarshaller u = this.getContext().createUnmarshaller();
        Unmarshaller u = JAXBContext.newInstance(cls).createUnmarshaller();
        JAXBElement<T> elm = (JAXBElement<T>)u.unmarshal(new StreamSource(new StringReader(in)), cls);
        return elm.getValue();
    }

    private String marshal(Object obj) throws JAXBException
    {
        Marshaller m = this.getContext().createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter out = new StringWriter();
        m.marshal(obj, out);
        return out.toString();
    }

    private void checkRespond(HttpMethodBase method) throws IOException
    {
        int status = method.getStatusCode();
        String body = method.getResponseBodyAsString();
        if (status > 300) // at time writing, api doesn't support redirecting
        {
            Logger.getLogger(OVirtEngineClient.class.getName()).log(Level.SEVERE, body);
        }
    }
    
    public <T> T get(String path, Class<T> cls) throws IOException, JAXBException, OVirtEngineException
    {
        GetMethod get = new GetMethod(this.url + '/' + path);
        try
        {
            get.addRequestHeader("Accept", CONTENT_TYPE);
            this.getClient().executeMethod(get);
            this.checkRespond(get);
            String out = get.getResponseBodyAsString();
            return this.unmarshal(out, cls);
        }
        finally
        {
            get.releaseConnection();
        }
    }
    
    public void post(String path, Object obj) throws UnsupportedEncodingException, JAXBException, IOException
    {
        PostMethod post = new PostMethod(this.url + '/' + path);
        try
        {
            post.addRequestHeader("Accept", CONTENT_TYPE);
            post.setRequestEntity(new StringRequestEntity(this.marshal(obj),
                                                        CONTENT_TYPE, CHARSET));
            this.getClient().executeMethod(post);
            this.checkRespond(post);
        }
        finally
        {
            post.releaseConnection();
        }
    }
    
    public <T>T query(String path, String q, Class<T> cls) throws IOException, JAXBException, OVirtEngineException
    {
        return this.get(path + "?search=\"" + q + "\"", cls);
    }
    
    public void isConnective() throws Exception
    {
        API api = this.get("", API.class);
        // TODO: verify required permissions
    }
   /* 
    public class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[] { tm }, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }
    */
}