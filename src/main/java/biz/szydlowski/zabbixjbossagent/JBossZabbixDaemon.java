package biz.szydlowski.zabbixjbossagent;

import static biz.szydlowski.zabbixjbossagent.WorkingObjects.*;
import biz.szydlowski.utils.Constans;
import biz.szydlowski.utils.Memory;
import biz.szydlowski.utils.OSValidator;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



//https://dzone.com/articles/executorservice-10-tips-and
//https://www.journaldev.com/1069/threadpoolexecutor-java-thread-pool-example-executorservice

/*// A) Await all runnables to be done (blocking)
for(Future<?> future : futures)
    future.get(); // get will block until the future is done

// B) Check if all runnables are done (non-blocking)
boolean allDone = true;
for(Future<?> future : futures){
    allDone &= future.isDone(); // check if future is done
}*/


/**
 * The entry point of the connector. (main function + shutdown hook)
 */
public class JBossZabbixDaemon  implements Daemon { 
    
     static {
        try {
            System.setProperty("log4j.configurationFile", getJarContainingFolder(JBossZabbixDaemon.class)+"/setting/log4j/log4j2.xml");
        } catch (Exception ex) {
        }
    }
    static final Logger log =  LogManager.getLogger(JBossZabbixDaemon.class);

    private static boolean stop = false;
    
    public static String file ="setting/conf.properties";   
     
    public static String absolutePath="";
    private static Timer MaintenanceTimer = new Timer("MaintenanceTask", true);
    
    static int  jboss_domain_controllers = 1; 
  
    public static List<ZabbixClient> _ZabbixClientList = new ArrayList<>(); 
    public static Map<String, Long> executor_timer =  new ConcurrentHashMap<>();
    private static ZabbixClientMaintenance _ZabbixClientMaintenance;
       
    static Properties cnxProps;
    
    public JBossZabbixDaemon(){
    
    }
    
    public JBossZabbixDaemon(boolean test, boolean win){
          if (test || win){
            if (!win) {
                System.out.println("****** TESTING MODE  ********");
                try {       
                  initialize(); 
                  start();                  
                } catch (Exception ex) {
                    log.error(ex);
                }
            }  else {
                System.out.println("****** WINDOWS MODE  ********");
            } 
           
            
        }
    }
    
    
    
            
    public static void main(String[] args) throws Exception
    {
       
         if (args.length>0){
                 if (args[0].equalsIgnoreCase("testing")){
                     JBossZabbixDaemon jBossZabbixDaemon = new JBossZabbixDaemon(true, false);
                 }

          }     
    }
    
     public boolean initialize() {

              
         if (OSValidator.isUnix()){
             absolutePath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                   absolutePath = absolutePath.substring(0, absolutePath.lastIndexOf("/"))+"/";
                   
             file = absolutePath + "/" + file;
         } else {
               absolutePath="";
         }
                   
              
         System.setProperty("java.net.preferIPv4Stack", "true");
         log.info("Init");
              
         printStarter();  
         
         cnxProps = getProperties();
         setBasicSetting(); 
         
         WorkingStats.start(jboss_domain_controllers);         
         
         
        
        for (int i=0; i<jboss_domain_controllers; i++){  
             log.info("> zabbixJbossConnector-"+i);  
             _ZabbixClientList.add(new ZabbixClient(i));
             _ZabbixClientList.get(i).start();                    
                
        }   
        
         _ZabbixClientMaintenance = new ZabbixClientMaintenance ();
         _ZabbixClientMaintenance.start();
                 
        MaintenanceTimer.schedule(new MaintananceTask(), 10000, 10000);
        return true;
     
     }
     /**
     *
     * @param dc
     * @throws DaemonInitException
     * @throws Exception
     */
    @Override
    public void init(DaemonContext dc) throws DaemonInitException, Exception {
     
        
        //  String[] args = dc.getArguments();                
          
          initialize();       
       
         
         
    }

  
    @Override
    public void start() throws Exception {
          log.info("Starting server");
          Memory.start();
          log.info("Started server");   
          
          while (!stop) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

   
    @Override
    public void stop() throws Exception {
        log.info("Shutting down the connector");
        stop = true;   
        
         for (int i=0; i<jboss_domain_controllers; i++){                   
           if (_ZabbixClientList.get(i)!=null) {
                _ZabbixClientList.get(i).stopSever(); 
           }
        } 
         
         _ZabbixClientMaintenance.stopSever();
        
        log.info("Stopped daemon");
    }
    
       
    //for windows
    public static void start(String[] args) {
        System.out.println("start");
        JBossZabbixDaemon jobber = new JBossZabbixDaemon(false, true);
        
        while (!stop) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }
  
    public static void stop(String[] args) {
        System.out.println("stop");
      
        log.info("Stoppping daemon");
                
        stop = true;
        
        for (int i=0; i<jboss_domain_controllers; i++){                   
           if (_ZabbixClientList.get(i)!=null) {
                _ZabbixClientList.get(i).stopSever(); 
           }
        } 
         
         _ZabbixClientMaintenance.stopSever();
        
        log.info("Stopped daemon");  
        
        System.exit(0);
                
    }
    
    public static Properties getProperties()
    {
        // Load properties
        Properties p = new Properties();
       // String file = System.getProperty("config", "conf.properties");
        FileInputStream input = null;
 
        try
        {
            input = new FileInputStream(file);
            if (input== null)
            {
                throw new RuntimeException("could not find the property file");
            }
            p.load(input);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not load configuration file " + file, e);
        }
        finally
        {
            try
            {
                if (input != null)
                {
                    input.close();
                }
            }
            catch (IOException e)
            {
                // Nothing to do
            }
        }
        return p;
    }

    
    private static void setBasicSetting(){
    
        jboss_domain_controllers = Integer.parseInt(cnxProps.getProperty("jboss_domain_controllers", "1"));
        
        for (int i=0; i<jboss_domain_controllers; i++){
            if (!cnxProps.getProperty("controler."+i+".host.skip", "###default").equals("###default")){
                 if (cnxProps.getProperty("controler."+i+".host.skip").length()>0)   {
                     HostSkip.put("controler."+i+".host.skip", cnxProps.getProperty("controler."+i+".host.skip"));
                 }
            } 
            if (!cnxProps.getProperty("controler."+i+".server.skip", "###default").equals("###default")){
                    ServerSkip.put("controler."+i+".server.skip", cnxProps.getProperty("controler."+i+".server.skip"));
            } 
            if (!cnxProps.getProperty("controler."+i+".profile.skip", "###default").equals("###default")){
                    ProfileSkip.put("controler."+i+".profile.skip", cnxProps.getProperty("controler."+i+".profile.skip"));
            } 
            
            if (!cnxProps.getProperty("controler."+i+".datasources.skip", "###default").equals("###default")){
                    DatasourcesSkip.put("controler."+i+".datasources.skip", cnxProps.getProperty("controler."+i+".datasources.skip"));
            }  
            
            if (!cnxProps.getProperty("controler."+i+".messaging.skip", "###default").equals("###default")){
                    MessagingSkip.put("controler."+i+".messaging.skip", cnxProps.getProperty("controler."+i+".messaging.skip"));
            } 
            
            if (!cnxProps.getProperty("controler."+i+".applicationserver.skip", "###default").equals("###default")){
                    ApplicationServerSkip.put("controler."+i+".applicationserver.skip", cnxProps.getProperty("controler."+i+".applicationserver.skip"));
            }
            
            
            if (!cnxProps.getProperty("controler."+i+".host.add", "###default").equals("###default")){
               if (cnxProps.getProperty("controler."+i+".host.add").length()>0)  HostAdd.put("controler."+i+".host.add", cnxProps.getProperty("controler."+i+".host.add"));
            } 
            if (!cnxProps.getProperty("controler."+i+".server.add", "###default").equals("###default")){
                 if (cnxProps.getProperty("controler."+i+".server.add").length()>0)    ServerAdd.put("controler."+i+".server.add", cnxProps.getProperty("controler."+i+".server.add"));
            } 
            if (!cnxProps.getProperty("controler."+i+".profile.add", "###default").equals("###default")){
                 if (cnxProps.getProperty("controler."+i+".profile.add").length()>0)   ProfileAdd.put("controler."+i+".profile.add", cnxProps.getProperty("controler."+i+".profile.add"));
            } 
            
            if (!cnxProps.getProperty("controler."+i+".datasources.add", "###default").equals("###default")){
                if (cnxProps.getProperty("controler."+i+".datasources.add").length()>0)  DatasourcesAdd.put("controler."+i+".datasources.add", cnxProps.getProperty("controler."+i+".datasources.add"));
            }  
            
            if (!cnxProps.getProperty("controler."+i+".messaging.add", "###default").equals("###default")){
                 if (cnxProps.getProperty("controler."+i+".messaging.add").length()>0) MessagingAdd.put("controler."+i+".messaging.add", cnxProps.getProperty("controler."+i+".messaging.add"));
            }
                         
            if (!cnxProps.getProperty("controler."+i+".applicationserver.add", "###default").equals("###default")){
                    ApplicationServerAdd.put("controler."+i+".applicationserver.add", cnxProps.getProperty("controler."+i+".applicationserver.add"));
            }
            
        }
        
    }
    
         public static String getJarContainingFolder(Class aclass) throws Exception {
          CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();

          File jarFile;

          if (codeSource.getLocation() != null) {
            jarFile = new File(codeSource.getLocation().toURI());
          }
          else {
            String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();
            String jarFilePath = path.substring(path.indexOf(":") + 1, path.indexOf("!"));
            jarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");
            jarFile = new File(jarFilePath);
          }
          return jarFile.getParentFile().getAbsolutePath();
     }
    
    
  
    private static void printStarter(){
        log.info(Constans.STARTER);
        log.info(new Version().getAllInfo());
    }

    @Override
    public void destroy() {
  
    }
}
