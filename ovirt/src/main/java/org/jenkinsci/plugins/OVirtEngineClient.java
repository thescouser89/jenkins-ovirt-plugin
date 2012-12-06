package org.jenkinsci.plugins;

import hudson.model.Hudson;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.WordUtils;
import org.ovirt.engine.api.model.API;
import org.ovirt.engine.api.model.Action;
import org.ovirt.engine.api.model.CPU;
import org.ovirt.engine.api.model.Cluster;
import org.ovirt.engine.api.model.Clusters;
import org.ovirt.engine.api.model.GuestInfo;
import org.ovirt.engine.api.model.IP;
import org.ovirt.engine.api.model.ObjectFactory;
import org.ovirt.engine.api.model.Status;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.Templates;
import org.ovirt.engine.api.model.VM;
import org.ovirt.engine.api.model.VMs;
import org.ovirt.engine.api.model.VmStates;


class OVirtEngineException extends Exception 
{

    public OVirtEngineException(String message) {
        super(message);
    }
}
class OvirtEngineRequestFailed extends OVirtEngineException
{

    public OvirtEngineRequestFailed(String message) {
        super(message);
    }
}
class OVirtEngineTimeout extends OVirtEngineException
{

    public OVirtEngineTimeout(int time) {
        super("Timeout expired: "+String.valueOf(time));
    }
    
}


class OVirtEngineEntityNotFound extends OvirtEngineRequestFailed
{

    public OVirtEngineEntityNotFound(String message) {
        super(message);
    }
    
}


public class OVirtEngineClient
{
    private static final String CONTENT_TYPE = "application/xml";
    private static final String CHARSET = "utf-8"; // TODO: should be configurable
    private static final String HEADER_ACCEPT = "Accept";
    
    private UsernamePasswordCredentials cred;
    private String url;
    private String cloudName;
    private transient HttpClient client;
    private transient JAXBContext context;
    
    private static final String API_PATH = "";
    private static final String TEMPLATES_PATH = "templates";
    private static final String VMS_PATH = "vms";
    private static final String CLUSTERS_PATH = "clusters";
    private static final String TEMPLATE_PATH = TEMPLATES_PATH+"/{id}";
    private static final String VM_PATH = VMS_PATH+"/{id}";
    private static final String CLUSTER_PATH = CLUSTERS_PATH+"/{id}";
    
    private static final Map<Class<?>, String> OBJECT_LOCATORS = new HashMap<Class<?>, String>();
    static  {
        OBJECT_LOCATORS.put(API.class, API_PATH);
        OBJECT_LOCATORS.put(Templates.class, TEMPLATES_PATH);
        OBJECT_LOCATORS.put(Template.class, TEMPLATE_PATH);
        OBJECT_LOCATORS.put(VMs.class, VMS_PATH);
        OBJECT_LOCATORS.put(VM.class, VM_PATH);
        OBJECT_LOCATORS.put(Clusters.class, CLUSTERS_PATH);
        OBJECT_LOCATORS.put(Cluster.class, CLUSTER_PATH);
    }
    
    private static final int WAIT_TIMEOUT = 300000; // ms

    public OVirtEngineClient(String url, String user, String passwd, String cloudName)
    {
        if(url.endsWith("/")){
            this.url = url.substring(0, url.length() - 1);
        }
        this.url = url;
        this.cloudName = cloudName;
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
    
    private <T>T unmarshal(InputStream in, Class<T> cls) throws JAXBException
    {
        //OVirtEnginePlugin.getLogger().fine(in);
        //Unmarshaller u = this.getContext().createUnmarshaller();
        Unmarshaller u = JAXBContext.newInstance(cls).createUnmarshaller();
        JAXBElement<T> elm = (JAXBElement<T>)u.unmarshal(new StreamSource(in), cls);
        return elm.getValue();
    }

    public String marshal(Object obj) throws Exception
    {
        Marshaller m = JAXBContext.newInstance(obj.getClass()).createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter out = new StringWriter();
        ObjectFactory fact = new ObjectFactory();
        String name = obj.getClass().getSimpleName();
        name = WordUtils.capitalizeFully(name);
        Method method = fact.getClass().getMethod("create"+name, obj.getClass());
        JAXBElement elm = (JAXBElement) method.invoke(fact, obj);
        m.marshal(elm, out);
        return out.toString();
    }

    private void checkRespond(HttpMethodBase method) throws Exception
    {
        int status = method.getStatusCode();
        if (status >= 300) // at time writing, api doesn't support redirecting
        {
            String body = method.getResponseBodyAsString();
            Logger.getLogger(OVirtEngineClient.class.getName()).log(Level.SEVERE, body);
            switch (status)
            {
                case 404:
                {
                    throw new OVirtEngineEntityNotFound(body);
                }
                default: throw new OvirtEngineRequestFailed(body); // TODO: add some inforamtion
            }
        }
    }
    
    public <T> T get(String path, Class<T> cls) throws Exception
    {
        GetMethod get = new GetMethod(this.url + '/' + path);
        try
        {
            get.addRequestHeader(HEADER_ACCEPT, CONTENT_TYPE);
            this.getClient().executeMethod(get);
            this.checkRespond(get);
            //String out = get.getResponseBodyAsString();
            return this.unmarshal(get.getResponseBodyAsStream(), cls);
        }
        finally
        {
            get.releaseConnection();
        }
    }
                
    private void post(EntityEnclosingMethod method, Object obj) throws Exception
    {
        try
        {
            method.addRequestHeader(HEADER_ACCEPT, CONTENT_TYPE);
            method.setRequestEntity(new StringRequestEntity(this.marshal(obj),
                                                        CONTENT_TYPE, CHARSET));
            this.getClient().executeMethod(method);
            this.checkRespond(method);
        }
        finally
        {
            method.releaseConnection();
        }
    }
    
    public void post(String path, Object obj) throws Exception
    {
        PostMethod post = new PostMethod(this.url + '/' + path);
        post(post, obj);
    }
    
    public void put(String path, Object obj) throws Exception
    {
        PutMethod put = new PutMethod(this.url + '/' + path);
        post(put, obj);
    }
    
    public void delete(String path) throws Exception
    {
        DeleteMethod del = new DeleteMethod(this.url + '/' + path);
        try
        {
            del.addRequestHeader(HEADER_ACCEPT, CONTENT_TYPE);
            this.getClient().executeMethod(del);
            this.checkRespond(del);
            String out = del.getResponseBodyAsString();
            OVirtEnginePlugin.getLogger().fine(out);
        }
        finally
        {
            del.releaseConnection();
        }
    }
    
    public void delete(Object obj) throws Exception
    {
        delete(composePath(obj));
    }
    
    public <T>T query(String path, String q, Class<T> cls) throws Exception
    {
        String query = path + "?search=" + q; // TODO: here should be escaped query
        //String query = path + "?search=" + URIUtil.encodeQuery(q);
        return this.get(query, cls);
    }
    
    
    public <T>T get(Class<T> cls) throws Exception
    {
        return this.get(OBJECT_LOCATORS.get(cls), cls);
    }
    
    public void isConnective() throws Exception
    {
        API api = this.get(API.class);
        // TODO: verify required permissions
    }
    
    public <T>T getElement(Object obj) throws Exception
    {
        return (T) this.get(composePath(obj), obj.getClass());
    }

    public <T>T getElement(String name, Class<?> cls) throws Exception
    {
        Object elms = this.query(OBJECT_LOCATORS.get(cls), "name%3D"+name, cls);
        List col = (List)getAttribute(cls.getSimpleName(), elms);
        if (col.isEmpty())
        {
            throw new OVirtEngineEntityNotFound(name);
        }
        return (T) col.get(0);
    }
    
    public List getCollection(Class<?> cls) throws Exception
    {
        Object elms = this.get(OBJECT_LOCATORS.get(cls), cls);
        return (List) getAttribute(cls.getSimpleName(), elms);
    }
    
    public <T>T getElementById(String id, Class<T> cls) throws Exception
    {
        return get(OBJECT_LOCATORS.get(cls).replaceAll("[{]id[}]", id), cls);
    }
    
    public Template getTemplate(Template tm) throws Exception
    {
        return getElement(tm);
    }
    
    public Template getTemplate(String name) throws Exception
    {
        return getElement(name, Templates.class);
    }
    
    public List<Template> getTemplates() throws Exception
    {
        return getCollection(Templates.class);
    }
    
    public void postElement(Object obj) throws Exception
    {
        this.post(composePath(obj), obj);
    }
    
    public void putElement(Object obj) throws Exception
    {
        this.put(composePath(obj), obj);
    }
    
    public VM getVm(VM vm) throws Exception
    {
        return this.get(composePath(VM_PATH, vm), VM.class);
    }
    
    public void createElement(Class<?> collection, Object obj) throws Exception
    {
        post(OBJECT_LOCATORS.get(collection), obj);
    }
    
    @SuppressWarnings("SleepWhileInLoop")
    public VM createVm(VM vm) throws Exception
    {
        Cluster cl = vm.getCluster();
        if (cl == null)
        {
            cl = (Cluster) getCollection(Clusters.class).get(0);
        }
        vm.setCluster(cl);
        createElement(VMs.class, vm);
        /*for (VM a: (List<VM>)getCollection(VMs.class))
        {
            if (a.getName().equals(vm.getName()))
            {
                vm = a;
                break;
            }
        }*/
        vm = getElement(vm.getName(), VMs.class);
        return waitForStatus(vm, "down", WAIT_TIMEOUT);
    }
    
    public void startVm(VM vm, boolean wait) throws Exception
    {
        if(wait){
            toogleVm("start", vm, "up");
        }
        else
        {
            toogleVm("start", vm);
        }
    }
    
    public void stopVm(VM vm, boolean wait) throws Exception
    {
        if(wait)
        {
            toogleVm("stop", vm, "down");
        }
        else
        {
            toogleVm("stop", vm);
        }
    }
    
    private void toogleVm(String action, VM vm) throws Exception
    {
        post(composePath(vm)+'/'+action, vm);
    }
    
    private void toogleVm(String action, VM vm, String expectedStatus) throws Exception
    {
        toogleVm(action, vm);
        waitForStatus(vm, expectedStatus, WAIT_TIMEOUT);
    }
    
    public <T>T waitForStatus(Object obj, String status, int timeout) throws Exception
    {
        timeout = timeout / 2;
        if(timeout < 1)
        {
            timeout = 1;
        }
        for(int i = 0; i < timeout; i++)
        {
            Object got = getElement(obj);
            Status st = getAttribute("status", got);
            OVirtEnginePlugin.getLogger().fine(st.getState());
            if(st.getState().equals(status))
            {
                return (T) got;
            }
        }
        throw new OVirtEngineTimeout(timeout);
    }
    
    private static <T>T getAttribute(String name, Object obj) throws Exception
    {
        String funcName = "get" + WordUtils.capitalize(name);
        Method func = obj.getClass().getMethod(funcName);
        return (T) func.invoke(obj);
    }

    public static String composePath(Object obj) throws Exception
    {
        return composePath(OBJECT_LOCATORS.get(obj.getClass()), obj);
    }
    
    public static String composePath(String pathTemplate, Object obj) throws Exception
    {
        Pattern p = Pattern.compile("[{][a-z]+[}]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(pathTemplate);
        while(m.find())
        {
            String placeholder = m.group();
            placeholder = placeholder.substring(1, placeholder.length()-1);
            String value = getAttribute(placeholder, obj);
            pathTemplate = pathTemplate.replaceAll("[{]" + placeholder + "[}]", value);
        }
        return pathTemplate;
    }
    
    public OVirtEngineCloud getCloud()
    {
        return (OVirtEngineCloud) Hudson.getInstance().clouds.getByName(this.cloudName);
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