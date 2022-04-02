/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package biz.szydlowski.zabbixjbossagent;


import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.jboss_domain_controllers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author dkbu
 */
public class ZabbixClientMaintenanceThread implements Runnable
{
    static final Logger log =  LogManager.getLogger(ZabbixClientMaintenanceThread.class);

    Socket socket;
    private long startTime;
    String [] validIpAddrList;
    BufferedReader in = null;
    boolean validAddr;

   // Utility items
    StringWriter writer = null;
    short[] tempShort = null;
    Properties props = null;
   
   ZabbixClientMaintenanceThread(Socket socket, Properties props)
    {
        this.socket= socket;
        tempShort = new short[4];
        this.props = props;
        validIpAddrList = props.getProperty("authorized_zabbix_server_name", "localhost").split(",");
        validAddr=false;
    }

    @Override
    public void run()  {
       // final Thread currentThread = Thread.currentThread();
        //currentThread.setName("Processing-" + System.currentTimeMillis());
        
        startTime= System.currentTimeMillis();
     
        String client = socket.getInetAddress().getHostAddress();
        if(log.isDebugEnabled()) {
               log.info("Accepted Connection From: " + client);
        }
       
        
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
                log.warn("Execution done with timeout (ERR)....");
            }
           
            return;
        }
        
        try  {
          
           socket.setTcpNoDelay(true); 
          
           int filterOut = 0;
           int dataLength=20000;
           String key="";
          
           in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    
            int indx=-8;
            
            
            while (true) {
              
                if (indx==dataLength) break; 
                indx++;
                
                byte b = (byte) in.read();
                              
                if (filterOut > 0)
                {
                    /* if (filterOut==8){
                    len=b+8;
                    }*/
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
                res = getResponseForKey(key);
            }          
        

            // ////////////////////////////////////////////
            // Send data back to Zabbix
            // ////////////////////////////////////////////

            log.debug("response: " + res);
            
            if (res.length()==0){
                res = "ZBX_NOTSUPPORTED\0res.length()==0";
            } else if (res.equals("undefined")) {
                //res = "";
                res = "ZBX_NOTSUPPORTED\0undefined";
            }
            
           writeMessage(socket.getOutputStream(), res.getBytes(Charset.forName("ISO-8859-1")));
           
   
            
        }   catch(SocketTimeoutException ste) {
            log.error(client + ": Timeout Detected.");
        }
        catch (Exception e) {
            log.error(e);
        }
        finally  {
            if(log.isDebugEnabled()) {
               log.debug(client + ": Disconnected.");
            }

            try { if(in != null) { in.close(); } } catch(Exception e) { }
            try { socket.close(); } catch(Exception e) { 
                log.error(e);
            }
      
            if (System.currentTimeMillis() - startTime > 10000 ){
                log.warn("Execution done with timeout....");
            }
            
            
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
   
     private String getResponseForKey(String key){
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
                
                if ("maintenance.zabbix".equals(key_root)) {
                 
                  if (key.split("\\[").length>=2){
                      String[] key_args = key.split("\\[")[1].replace("]", "").split(",");
                      //log.debug("key_root: " + key_root);
                                       
                      switch (key_args[0]) {

                            case "connError":
                                res = "0";
                                if (key_args.length>1){                                     
                                     res= Long.toString(WorkingStats.connError.get(Integer.parseInt(key_args[1])));                                     
                                }
                                break;

                            case "javaUsedMemory":
                                res = Long.toString(WorkingStats.getJavaUsedMemory());
                                break;

                            case "activeThreads":
                                res = Long.toString(WorkingStats.activeThreads);
                                break;

                            case "okCount":
                                if (key_args.length>1){                                     
                                     res= Long.toString(WorkingStats.okCount.get(Integer.parseInt(key_args[1])));                                     
                                }
                                break;

                            case "errorCount":
                                
                                if (key_args.length>1){                                     
                                     res= Long.toString(WorkingStats.errorCount.get(Integer.parseInt(key_args[1])));                                     
                                }
                                break;  
                                

                            case "agentUptime":
                                res= Long.toString(WorkingStats.getUptime());
                                break;     
                            
                                
                            case "clientUptime":
                               
                                if (key_args.length>1){                                     
                                     res= Long.toString(WorkingStats.getZabbixClientUptime(Integer.parseInt(key_args[1])));                                     
                                }
                                break;  
                            
                            case "idleTime":
                               
                                if (key_args.length>1){                                     
                                     res= Long.toString(WorkingStats.getZabbixClientIdleTime(Integer.parseInt(key_args[1])));                                     
                                }
                                break;
                                
                           case "itemsInCache":
                               
                                res= Integer.toString(CacheValue.getCacheValueSize());
                              
                                break; 


                            case "version":
                                res = Version.getAgentVersion();
                                break;

                            default:
                                res = "";
                            } 
                   }
                }  else if ("discovery.maintenance.vports".equals(key_root)) {
                    res = Discovery.discoverVirtualConnnector(props, jboss_domain_controllers);
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

    
    @Override
    public String toString(){
        return ""+this.startTime;
    }
    
}
