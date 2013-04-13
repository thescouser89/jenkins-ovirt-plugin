package org.jenkinsci.plugins;

import hudson.model.Hudson;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
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
import org.apache.commons.httpclient.Header;
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
import org.apache.commons.lang.WordUtils;
import org.ovirt.engine.api.model.API;
import org.ovirt.engine.api.model.Action;
import org.ovirt.engine.api.model.Cluster;
import org.ovirt.engine.api.model.Clusters;
import org.ovirt.engine.api.model.GuestInfo;
import org.ovirt.engine.api.model.IP;
import org.ovirt.engine.api.model.IPs;
import org.ovirt.engine.api.model.ObjectFactory;
import org.ovirt.engine.api.model.Status;
import org.ovirt.engine.api.model.Template;
import org.ovirt.engine.api.model.Templates;
import org.ovirt.engine.api.model.VM;
import org.ovirt.engine.api.model.VMs;





public class OVirtEngineClient
{
    
    protected static final Logger LOGGER = OVirtEnginePlugin.getLogger();
    
    private static final String CONTENT_TYPE = "application/xml";
    private static final String CHARSET = "utf-8"; // TODO: should be configurable
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    
    private UsernamePasswordCredentials cred;
    private String url;
    private String cloudName;
    private transient HttpClient client;
    
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
        
    private <T>T unmarshal(InputStream in, Class<T> cls) throws IOException
    {
        try
        {
            Unmarshaller u = JAXBContext.newInstance(cls).createUnmarshaller();
            JAXBElement<T> elm = (JAXBElement<T>)u.unmarshal(new StreamSource(in), cls);
            return elm.getValue();
        }catch(JAXBException ex)
        {
            LOGGER.log(Level.SEVERE, "can not unmarshal input to "+cls.getName(), ex);
            throw new IOException(ex.toString());
        }
    }

    public String marshal(Object obj) throws IOException
    {
        Exception caughtex;
        try
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
        catch(NoSuchMethodException ex)
        {
            caughtex = ex;
        }
        catch(IllegalAccessException ex)
        {
            caughtex = ex;
        }
        catch(InvocationTargetException ex)
        {
            caughtex = ex;
        }
        catch(JAXBException ex)
        {
            LOGGER.log(Level.SEVERE, "can not marshal object", ex);
            throw new IOException(ex.toString());
        }
        LOGGER.log(Level.SEVERE, "can not execute proper factory method for "+obj.getClass().getName(), caughtex);
        throw new IOException(caughtex.toString());
    }

    private void checkRespond(HttpMethodBase method) throws OVirtEngineRequestFailed
    {
        int status = method.getStatusCode();
        if (status >= 300) // at time writing, api doesn't support redirecting
        {   
            String body;
            try
            {
                body = method.getResponseBodyAsString();
            }catch(IOException ex)
            {
                LOGGER.log(Level.SEVERE, "Can not read body of response", ex);
                body = ex.toString();
            }
            switch (status)
            {
                case 404:
                {
                    throw new OVirtEngineEntityNotFound(body);
                }
                default: throw new OVirtEngineRequestFailed(body); // TODO: add some inforamtion
            }
        }
    }
    
    public synchronized <T> T get(String path, Class<T> cls) throws OVirtEngineRequestFailed
    {
        String completedUrl = this.url + '/' + path;
        LOGGER.log(Level.FINE, "GET {0}", completedUrl);
        GetMethod get = new GetMethod(completedUrl);
        try
        {
            get.addRequestHeader(HEADER_ACCEPT, CONTENT_TYPE);
            this.getClient().executeMethod(get);
            
            if(!get.getResponseHeader(HEADER_CONTENT_TYPE).getValue().contains(CONTENT_TYPE))
            {
                this.checkRespond(get);
            }
            return this.unmarshal(get.getResponseBodyAsStream(), cls);
        }
        catch(IOException ex)
        {
            LOGGER.log(Level.SEVERE, "GET method failed", ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
        finally
        {
            get.releaseConnection();
        }
    }
                
    private synchronized void post(EntityEnclosingMethod method, Object obj)throws OVirtEngineRequestFailed
    {
        String body;
        try
        {
            body = marshal(obj);
            method.addRequestHeader(HEADER_ACCEPT, CONTENT_TYPE);
            method.setRequestEntity(new StringRequestEntity(body,
                                                        CONTENT_TYPE, CHARSET));
            this.getClient().executeMethod(method);
            this.checkRespond(method);
        }
        catch(IOException ex)
        {
            LOGGER.log(Level.SEVERE, "POST method failed", ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
        finally
        {
            method.releaseConnection();
        }
    }
    
    public void post(String path, Object obj) throws OVirtEngineRequestFailed
    {
        String completedUrl = this.url + '/' + path;
        LOGGER.log(Level.FINE, "POST {0}", completedUrl);
        PostMethod post = new PostMethod(completedUrl);
        post(post, obj);
    }
    
    public void put(String path, Object obj) throws OVirtEngineRequestFailed
    {
        String completedUrl = this.url + '/' + path;
        LOGGER.log(Level.FINE, "PUT {0}", completedUrl);
        PutMethod put = new PutMethod(completedUrl);
        post(put, obj);
    }
    
    public synchronized void delete(String path) throws OVirtEngineRequestFailed
    {
        String completedUrl = this.url + '/' + path;
        LOGGER.log(Level.FINE, "DELETE {0}", completedUrl);
        DeleteMethod del = new DeleteMethod(completedUrl);
        try
        {
            del.addRequestHeader(HEADER_ACCEPT, CONTENT_TYPE);
            this.getClient().executeMethod(del);
            this.checkRespond(del);
        }
        catch(IOException ex)
        {
            LOGGER.log(Level.SEVERE, "DELETE method failed", ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
        finally
        {
            del.releaseConnection();
        }
    }
    
    public void delete(Object obj)throws OVirtEngineRequestFailed
    {
        try
        {
            delete(composePath(obj));
        }catch(OVirtEngineReflectionError ex)
        {
            LOGGER.log(Level.SEVERE, "can not compose path to object: "+obj.getClass().getName(), ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public <T>T query(String path, String q, Class<T> cls) throws OVirtEngineRequestFailed
    {
        String query = path + "?search=" + q; // TODO: here should be escaped query
        //String query = path + "?search=" + URIUtil.encodeQuery(q);
        return this.get(query, cls);
    }
    
    
    public <T>T get(Class<T> cls) throws OVirtEngineRequestFailed
    {
        return this.get(OBJECT_LOCATORS.get(cls), cls);
    }
    
    public void isConnective() throws OVirtEngineRequestFailed
    {
        API api = this.get(API.class);
        // TODO: verify required permissions
    }
    
    public <T>T getElement(Object obj) throws OVirtEngineRequestFailed
    {
        try
        {
            return (T) this.get(composePath(obj), obj.getClass());
        }catch(OVirtEngineReflectionError ex)
        {
            LOGGER.log(Level.SEVERE, "can not compose path to element: " + obj.getClass().getName(), ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }

    public <T>T getElement(String name, Class<?> cls) throws OVirtEngineRequestFailed
    {
        try
        {
            Object elms = this.query(OBJECT_LOCATORS.get(cls), "name%3D"+name, cls);
            List col = (List)getAttribute(cls.getSimpleName(), elms);
            if (col.isEmpty())
            {
                throw new OVirtEngineEntityNotFound(name);
            }
            return (T) col.get(0);
        }catch(OVirtEngineReflectionError ex)
        {
            LOGGER.log(Level.SEVERE, "can not compose path element: " + cls.getName(), ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public List getCollection(Class<?> cls) throws OVirtEngineRequestFailed
    {
        try
        {
            Object elms = this.get(OBJECT_LOCATORS.get(cls), cls);
            return (List) getAttribute(cls.getSimpleName(), elms);
        }catch(OVirtEngineAttributeResolutionError ex)
        {
            LOGGER.log(Level.SEVERE, "can not get getter for: " + cls.getName(), ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public <T>T getElementById(String id, Class<T> cls) throws OVirtEngineRequestFailed
    {
        return get(OBJECT_LOCATORS.get(cls).replaceAll("[{]id[}]", id), cls);
    }
    
    public Template getTemplate(Template tm) throws OVirtEngineRequestFailed
    {
        return getElement(tm);
    }
    
    public Template getTemplate(String name) throws OVirtEngineRequestFailed
    {
        return getElement(name, Templates.class);
    }
    
    public List<Template> getTemplates() throws OVirtEngineRequestFailed
    {
        return getCollection(Templates.class);
    }
    
    public VM getVm(String name) throws OVirtEngineRequestFailed
    {
        return (VM)getElement(name, VMs.class);
    }
    
    public VM getVm(VM vm) throws OVirtEngineRequestFailed
    {
        return getElement(vm);
    }
    
    public Cluster getCluster(String name) throws OVirtEngineRequestFailed
    {
        return (Cluster)getElement(name, Clusters.class);
    }
    
    public void postElement(Object obj) throws OVirtEngineRequestFailed
    {
        try {
            this.post(composePath(obj), obj);
        } catch (OVirtEngineReflectionError ex) {
            LOGGER.log(Level.SEVERE, "can not compose path to element: " + obj.getClass().getName(), ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public void putElement(Object obj) throws OVirtEngineRequestFailed
    {
        try {
            this.put(composePath(obj), obj);
        } catch (OVirtEngineReflectionError ex) {
            LOGGER.log(Level.SEVERE, "can not compose path to element: " + obj.getClass().getName(), ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public void createElement(Class<?> collection, Object obj) throws OVirtEngineRequestFailed
    {
        post(OBJECT_LOCATORS.get(collection), obj);
    }
    
    public VM createVm(VM vm) throws OVirtEngineRequestFailed
    {
        Cluster cl = vm.getCluster();
        if (cl == null)
        {
            cl = (Cluster) getCollection(Clusters.class).get(0);
        }
        vm.setCluster(cl);
        createElement(VMs.class, vm);
        vm = getElement(vm.getName(), VMs.class);
        try
        {
            return waitForStatus(vm, "down", WAIT_TIMEOUT);
        }catch(OVirtEngineTimeout ex)
        {
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public void deleteElement(Object elm) throws OVirtEngineRequestFailed
    {
        delete(elm);
        while(true) // TODO: add timeout
        {
            try
            {
                sleep(1000);
                getElement(elm);
            }catch(OVirtEngineEntityNotFound ex)
            {
                break;
            }
        }
    }
        
    public void deleteVm(String name) throws OVirtEngineRequestFailed
    {
        deleteElement(getVm(name));
    }
    
    public void startVm(VM vm, boolean wait) throws OVirtEngineRequestFailed
    {
        if(wait){
            toogleVm("start", vm, "up");
        }
        else
        {
            toogleVm("start", vm);
        }
    }
    
    public void stopVm(VM vm, boolean wait) throws OVirtEngineRequestFailed
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
    
    private void toogleVm(String action, VM vm) throws OVirtEngineRequestFailed
    {
        Action actionElement = new Action();
        actionElement.setVm(vm);
        try
        {
            post(composePath(vm)+'/'+action, actionElement);
        }catch(OVirtEngineReflectionError ex)
        {
            LOGGER.log(Level.SEVERE, "can not compose path to vm", ex);
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    private void toogleVm(String action, VM vm, String expectedStatus) throws OVirtEngineRequestFailed
    {
        toogleVm(action, vm);
        try
        {
            waitForStatus(vm, expectedStatus, WAIT_TIMEOUT);
        }catch(OVirtEngineTimeout ex)
        {
            throw new OVirtEngineRequestFailed(ex.toString());
        }
    }
    
    public <T>T waitForStatus(Object obj, String status, int timeout) throws OVirtEngineTimeout
    {
        if(timeout < 1)
        {
            timeout = 1;
        }
        for(int i = 0; i < timeout; i++)
        {
            sleep(1000);
            try
            {
                Object got = getElement(obj);
                Status st = getAttribute("status", got);
                LOGGER.log(Level.FINE, "Status {0} for object {1}", new Object[]{st.getState(), obj.getClass().getName()});
                if(st.getState().equals(status))
                {
                    return (T) got;
                }
            }catch(OVirtEngineException ex)
            {
                LOGGER.log(Level.SEVERE, "unexcpected exception", ex);
            }
        }
        throw new OVirtEngineTimeout(timeout);
    }
    
    public List<String> getVmIPs(String name) throws OVirtEngineRequestFailed
    {
        GuestInfo info = getVm(name).getGuestInfo();
        LinkedList<String> ips = new LinkedList<String>();
        if(info != null)
        {
            IPs infoIPs = info.getIps();
            if (infoIPs != null)
            {
                for (IP ip: infoIPs.getIPs())
                {
                    String address = ip.getAddress();
                    if(address != null && !address.isEmpty())
                    {
                        ips.add(ip.getAddress());
                    }
                }
            }
        }
        return ips;
    }
    
    private long sleep(long timeToSleep)
    {
        long start = System.currentTimeMillis();
        try
        {
            Thread.sleep(timeToSleep);
        }catch(InterruptedException ex)
        {
            LOGGER.fine("sleep was interuted"); //FIXME: this shhoul be handled in better manner
            Thread.currentThread().interrupt();
        }
        return System.currentTimeMillis() - start;
    }
    
    private static <T>T getAttribute(String name, Object obj) throws OVirtEngineAttributeResolutionError
    {
        Exception caughtex;
        try
        {
            String funcName = "get" + WordUtils.capitalize(name);
            Method func = obj.getClass().getMethod(funcName);
            return (T) func.invoke(obj);
        }
        catch(NoSuchMethodException ex)
        {
            caughtex = ex;
        }
        catch(IllegalAccessException ex)
        {
            caughtex = ex;
        }
        catch(InvocationTargetException ex)
        {
            caughtex = ex;
        }
        
        LOGGER.log(Level.SEVERE, "can not compose path to api for object: " + obj.getClass().getName(), caughtex);
        throw new OVirtEngineAttributeResolutionError(caughtex.toString());
    }

    public static String composePath(Object obj) throws OVirtEngineReflectionError
    {
        return composePath(OBJECT_LOCATORS.get(obj.getClass()), obj);   
    }
    
    public static String composePath(String pathTemplate, Object obj)throws OVirtEngineAttributeResolutionError
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
    
    public static class OVirtEngineException extends Exception 
    {

        public OVirtEngineException(String message) {
            super(message);
        }
    }
    public static class OVirtEngineReflectionError extends OVirtEngineException
    {

        public OVirtEngineReflectionError(String message) {
            super(message);
        }

    }
    public static class OVirtEngineAttributeResolutionError extends OVirtEngineReflectionError
    {

        public OVirtEngineAttributeResolutionError(String message) {
            super(message);
        }
    }
    public static class OVirtEngineRequestResolutionError extends OVirtEngineReflectionError
    {

        public OVirtEngineRequestResolutionError(String message) {
            super(message);
        }

    }
    public static class OVirtEngineRequestFailed extends OVirtEngineException
    {

        public OVirtEngineRequestFailed(String message) {
            super(message);
        }
    }
    public static class OVirtEngineTimeout extends OVirtEngineException
    {

        public OVirtEngineTimeout(int time) {
            super("Timeout expired: "+String.valueOf(time));
        }

    }

    public static class OVirtEngineEntityNotFound extends OVirtEngineRequestFailed
    {

        public OVirtEngineEntityNotFound(String message) {
            super(message);
        }

    }
}