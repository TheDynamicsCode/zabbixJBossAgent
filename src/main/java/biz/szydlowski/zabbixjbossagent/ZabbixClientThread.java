package biz.szydlowski.zabbixjbossagent;

import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.absolutePath;
import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.executor_timer;
import biz.szydlowski.utils.OSValidator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.as.cli.CommandFormatException;

public class ZabbixClientThread implements Runnable
{
     static final Logger log =  LogManager.getLogger(ZabbixClientThread.class);

    Socket socket;
    JBossApi api;
    int index=0;
    private long startTime;
    String [] validIpAddrList;
    String [] cachedIpAddrList;
    BufferedReader in = null;
    boolean validAddr;
    boolean cachedAddr;
   // Utility items
    StringWriter writer = null;
    short[] tempShort = null;
    Properties props = null;
   
    ZabbixClientThread(Socket socket, JBossApi api, Properties props,  int index)
    {
        this.socket= socket;
        this.api = api;
        this.index=index;
        tempShort = new short[4];
        this.props = props;
        validIpAddrList = props.getProperty("authorized_zabbix_server_name", "localhost").split(",");
        cachedIpAddrList = props.getProperty("cached_server_name", "localhost123456789").split(",");
        validAddr=false;
    }

    @Override
    public void run()
    {
       // final Thread currentThread = Thread.currentThread();
        //currentThread.setName("Processing-" + System.currentTimeMillis());
        //log.debug("Current thread " + Thread.currentThread());
                
        startTime= System.currentTimeMillis();
        executor_timer.put(Thread.currentThread().toString(), startTime);
     
        String client = socket.getInetAddress().getHostAddress();
        if(log.isDebugEnabled()) {
               log.info("Accepted Connection From: " + client);
        }
       
        WorkingStats.syncZabbixClientIdleTime(index);
        
        for (String h: validIpAddrList){
           if (client.equals(h)){
               validAddr=true;
               break;
           } 
        }
                 
        if (!validAddr){
             try {
                 socket.close();
             } catch(Exception e) { 
                 log.error(e);
            }
           // log.debug(validIpAddr + " rejected, socket.close()" );
            
            if (System.currentTimeMillis() - startTime > 10000 ){
                WorkingStats.syncZabbixClientTimeoutPlus(index);
                log.warn("Execution done with timeout (ERR)....");
            }
            executor_timer.remove(Thread.currentThread().toString());
            return;
        }
        
        for (String h: cachedIpAddrList){
               if (client.equals(h)){
                   cachedAddr=true;
                   log.debug("cachedAddr " + h);
                   break;
               } 
         }

        
        try  {
          
           socket.setTcpNoDelay(true); 
           
           int filterOut = 0;
           int dataLength=20000;
           String key="";
          
           in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    
           int indx=-8;
            
            
            while (true)
            {
              
                if (indx==dataLength) break; 
                indx++;
                
                byte b = (byte) in.read();
                              
                if (filterOut > 0)
                {
                    
                    switch (filterOut) {
                        case 8:
                            tempShort[0] = b;
                            break;
                        case 7:
                            tempShort[1] = b;
                            break;
                        case 6:
                            tempShort[2] = b;
                            break;
                        case 5:
                            tempShort[3] = b;
                            dataLength = convToInt(tempShort);
                            break;
                        default:
                            break;
                    }
                                    
                    filterOut--;
                    continue;
                }
                if (b == -1 || b == 10)
                {
                    // \n or end of stream will end the key.
                    break;
                }
                if (b == 1)
                {
                    // Happens from zabbix sender: the ZBX header is sent by the getter. In that case, ignore length (8 next bytes).
                    writer = new StringWriter();
                    filterOut = 8;
                    indx=-8;
                    continue;
                }
                
                if (writer!=null) writer.write(b); 
             
            }
            
            socket.setSoTimeout(10000);
             
            if (writer!=null) key=writer.toString();
            //log.debug("key.l " + key.length() + " dataLength " + dataLength);
            //log.debug("key " + key);
           
            String res = "";
           
            if (key.length()!=dataLength){
                log.error("key.length()!=dataLength");
            }  else {
               if (cachedAddr){                     
                    String value = CacheValue.getCacheValue(index+"#"+key);
                    if (value==null){
                        log.debug("Value in cache is null");
                        res = getResponseForKey(api, index, key);
                    } else {
                        if (CacheValue.isValueInCacheValid(index+"#"+key)){
                              if (CacheValue.isValueInCacheHitsValid(index+"#"+key)){
                                      res=value;
                                     log.debug("getValue from cache " + value + " for key " + key);
                               } else {
                                     log.debug("Value in cache is out of data (hits), read new value");
                                     res = getResponseForKey(api, index, key);
                               }
                        } else {
                             log.debug("Value in cache is out of age, read new value");
                             res = getResponseForKey(api, index, key);
                        }
                      
                    }
                } else {
                     res = getResponseForKey(api, index, key);
               }
             
            }          
        

            // ////////////////////////////////////////////
            // Send data back to Zabbix
            // ////////////////////////////////////////////

            log.debug("response: " + res);
            
            if (res.length()==0){
                res = "ZBX_NOTSUPPORTED\0res.length()==0";
                WorkingStats.errorCountPlus(index);
            } else if (res.equals("undefined")) {
                //res = "";
                res = "ZBX_NOTSUPPORTED\0undefined";
                WorkingStats.errorCountPlus(index);
            }
            
            if (writeMessage(socket.getOutputStream(), res.getBytes(Charset.forName("ISO-8859-1"))) ){
                 WorkingStats.okCountPlus(index);
            } else {
                 WorkingStats.connErrorPlus(index);
            }
            
   
            
        }   catch(SocketTimeoutException ste) {
            log.error(client + ": Timeout Detected.");
            WorkingStats.connErrorPlus(index);
        }
        catch (IOException e)
        {
            log.error(e);
             WorkingStats.connErrorPlus(index);
        }
        finally
        {
            if(log.isDebugEnabled()) {
               log.debug(client + ": Disconnected.");
            }

            try { if(in != null) { in.close(); } } catch(Exception e) { }
            try { socket.close(); } catch(Exception e) { 
                log.error(e);
            }
            if(log.isDebugEnabled()) {
                   //log.debug(currentThread.getName() + " DONE");
            }
          
            WorkingStats.syncZabbixClientIdleTime(index);
            
            if (System.currentTimeMillis() - startTime > 10000 ){
                WorkingStats.syncZabbixClientTimeoutPlus(index);
                log.warn("Execution done with timeout....");
            }
            
            executor_timer.remove(Thread.currentThread().toString());
            
            
        }
    }
  
  
    // Pass an array of four shorts which convert from LSB first 
     private int convToInt(short[] sb){
       int answer = sb[0]  & 0xFF;
       answer += (sb[1] & 0x00FF) << 8 ;
       answer += (sb[2] & 0x0000FF) << 16  ;
       answer += (sb[3]& 0x000000FF)<< 24  ;
       return answer;        
     }
   
     private String getResponseForKey(JBossApi api, int index, String key){
           // ////////////////////////////////////////////
            // Find data
            // ////////////////////////////////////////////
            startTime=System.currentTimeMillis();

            String res="";

            // Key analysis
            log.debug("Requested key: " + key);
            String key_root = key.split("\\[")[0];
            
            log.trace("key_root: " + key_root);
            
            try {

                // Discovery?
                if ("discovery.hosts".equals(key_root))
                {
                    res = Discovery.discoverHosts(api, index);
                }
                else if ("discovery.as".equals(key_root))
                {
                    res = Discovery.discoverAs(api, index);
                }
                else if ("discovery.datasources".equals(key_root))
                {
                    res = Discovery.discoverDatasources(api, index);
                }
                 else if ("discovery.datasources.only".equals(key_root))
                {
                    res = Discovery.discoverDatasourcesOnly(api, index);
                }
                else if ("discovery.deployments".equals(key_root))
                {
                    res = Discovery.discoverDeployments(api);
                }
                else if ("discovery.asdeployments".equals(key_root))
                {
                    res = Discovery.discoverDeploymentsOnAs(api, index);
                }
                else if ("discovery.asdeployments.only".equals(key_root))
                {
                    res = Discovery.discoverDeploymentsOnlyOnAs(api, index);
                }
                else if ("discovery.asdatasources".equals(key_root))
                {
                    res = Discovery.discoverDatasourcesOnAs(api, index);
                } 
                else if ("discovery.servergroups.jvm".equals(key_root))
                {
                    res = Discovery.discoverServerGroupsJVM(api, index);
                }
                else if ("discovery.asmessaging".equals(key_root))
                {
                    res = Discovery.discoverMessagingOnAs(api, index);
                } else {
                 
                    
                    if (key.split("\\[").length>=2){

                        String[] key_args = key.split("\\[")[1].replace("]", "").split(",");

                        // get the corresponding query from parameter file
                        String query_raw = getQuery(key_root);
                        if (query_raw == null)
                        {
                            // Not supported item key
                            res = "";
                        }
                        else
                        {
                            String attr = null;
                            log.trace("raw query: " + query_raw);
                            if (query_raw.split("!").length > 1)
                            {
                                attr = query_raw.split("!")[1];
                                query_raw = query_raw.split("!")[0];
                            }
                            String query = String.format(query_raw, (Object[]) key_args);

                            if (attr != null && attr.startsWith("sum"))
                            {
                                // Query with loops
                                attr = attr.substring(4, attr.length() - 1);
                                res = sum(query, "", Arrays.asList(attr.split(",")), api).toString();
                                attr = null;
                            }
                            else
                            {
                                // Direct query - no recursion needed
                                try
                                {
                                    res = api.runSingleQuery(query, attr);
                                }
                                catch (Exception e)
                                {
                                    // empty string returned = not supported.
                                    res = "";
                                    log.info("unsupported item requested: " + key);
                                }
                            }
                        }
                   } else {
                       log.info("Pure key " + key_root);
                       
                       String query_raw = getQuery(key_root);
                        if (query_raw == null)
                        {
                            // Not supported item key
                            res = "";
                        }
                        else
                        {
                           String attr = null;
                           log.trace("raw query: " + query_raw);
                                                     
                            // Direct query - no recursion needed
                            try
                            {
                                res = api.runSingleQuery(query_raw, attr);
                            }
                            catch (Exception e)
                            {
                                // empty string returned = not supported.
                                res = "";
                                log.info("unsupported item requested: " + key);
                            }
                            
                        }
                   
                   }
                    
                   CacheValue.setCacheValue(index+"#"+key, res);
                }
        } catch (Exception e){
              log.error("EX121 " + e);      
        }
            
        return res;    
    }
    
   protected boolean writeMessage(OutputStream out, byte[] data) {
		int length = data.length;
                boolean ret =false;
              		
		try   {
                     out.write(new byte[] {
				'Z', 'B', 'X', 'D', 
				'\1',
				(byte)(length & 0xFF), 
				(byte)((length >> 8) & 0x00FF), 
				(byte)((length >> 16) & 0x0000FF), 
				(byte)((length >> 24) & 0x000000FF),
				'\0','\0','\0','\0'});
		
		    out.write(data);
                    out.flush();
                    ret =true;
            } catch (Exception e) {                
                log.error(e);
                 
                 ret =false;
            }
            return ret;
	}

    private volatile static Map<String, String> queries = null;
    private static Date lastLoaded = new Date();
    private static String queryFile = null;
 
    
    private String getQuery(String keyRoot) throws IOException
    {
        if (queries == null || (queryFile != null && lastLoaded.before(new Date((new File(queryFile)).lastModified()))))
        {
            synchronized (ZabbixClientThread.class)
            {
                if (queries == null || (queryFile != null && lastLoaded.before(new Date((new File(queryFile)).lastModified()))))
                {
                    queries = new HashMap<>();
                    if (queryFile == null)
                    {
                        queryFile = "setting/items.txt";
                        if (OSValidator.isUnix()){
                              queryFile = absolutePath + "/" + queryFile ;
                        }
                        
                        if (queryFile == null)
                        {
                            throw new RuntimeException("oups");
                        }
                    }
                    log.info("Reloading query file: " + queryFile);
                    lastLoaded = new Date();
                    BufferedReader br = new BufferedReader(new FileReader(new File(queryFile)));

                    String line = br.readLine();

                    while (line != null)
                    {
                        if (line.isEmpty() || "\n".equals(line) || line.startsWith("#"))
                        {
                            line = br.readLine();
                            continue;
                        }
                        String key = line.split(";")[0];
                        String q = line.split(";")[1];
                        queries.put(key, q);
                        line = br.readLine();
                    }
                    br.close();
                }
            }
        }

        return queries.get(keyRoot);
    }
    
    
    /**
     * 
     * @param query_base
     *            without a first /
     * @param root
     *            should be "" on first call
     * @param types
     * @param api
     */
    public static Long sum(String query_base, String root, List<String> types, JBossApi api) throws CommandFormatException, IOException
    {
        if (types.isEmpty())
        {
            // End of recursion: run the query!
            String query = root + "/" + query_base;
            log.trace(query);            
            String res;
            try
            {
                res = api.runSingleQuery(query, null);
            }
            catch (RuntimeException e)
            {
                res = "undefined";
            }

            if (res.equals("undefined"))
            {
                // The given item may not exist on all loops
                return 0L;
            }
            else
            {
                return Long.parseLong(res);
            }
        }

        Long res = 0L;
        String typed = types.get(0);
        String addRoot = typed.split(":")[0];
        String type = typed.split(":")[1];
        List<String> remaingintypes = types.subList(1, types.size());

        String query = root + addRoot + ":read-children-names(child-type=" + type + ")";
        for (String val : api.runListQuery(query))
        {
           if (!val.contains("/")){
               res += sum(query_base, root + addRoot + type + "=" + val, remaingintypes, api);
           }
        }
        return res;
    }
    
    @Override
    public String toString(){
        return ""+this.startTime;
    }
    
}
