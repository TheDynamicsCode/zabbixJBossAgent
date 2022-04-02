package biz.szydlowski.zabbixjbossagent;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import static biz.szydlowski.zabbixjbossagent.WorkingObjects.*;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Discovery
{
     static final Logger log =  LogManager.getLogger(Discovery.class);
        
    public static boolean isSkipping(HashMap<String, String> mapSkip, HashMap<String, String> mapAdd, String key, int index, String check){
       boolean ret=false;
       if (!mapSkip.getOrDefault("controler."+index+"."+key+".skip", "###default").equals("###default")){
            String Skip[] = mapSkip.getOrDefault("controler."+index+"."+key+".skip", "###default").split(";");
            String Add[] = mapAdd.getOrDefault("controler."+index+"."+key+".add", "###default").split(";");
            
            if (Skip!=null && Add!=null){
              
                for (String stmp:Skip){ 
                   
                    if ( check.matches(stmp)){
                       ret = true;
                       log.debug("Discovery Skipping " + check + " for: " + "controler."+index+"."+key+".skip");
                       break;
                    }

                } 
                                
                for (String stmp:Add){
                    if ( check.matches(stmp)){
                       if (ret){
                          log.debug("Discovery was Skipping but is in white list, Add " + check + " for: " + "controler."+index+"."+key+".skip");  
                       } else {
                          log.debug("Discovery Add " + check + " for: " + "controler."+index+"."+key+".skip");
                       }
                       ret = false;                       
                       break;
                    }

                }
            } else {
                log.error("Skip!=null && Add!=null");
            }
       }
       
       return ret;
    }   
    
    public static String discoverVirtualConnnector(Properties p, int controllers) throws CommandFormatException, IOException
    {
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");

        for (int i=0; i<controllers; i++)
        {
               res.append(" { \"{#CONTROLERID}\":\"").append(i).append("\", \"{#VPORT}\":\"").append(p.getProperty("local_port_for_controler."+i)).append("\"},");
             
        }

        res.deleteCharAt(res.length()-1);
        res.append("]}");

        return res.toString();
    }
    
    public static String discoverHosts(JBossApi api, int index) throws CommandFormatException, IOException
    {
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");

        for (String s : nodes)
        {
             if (!s.contains("/")){
                  if (!isSkipping(HostSkip, HostAdd, "host", index, s)) {
                        res.append(" { \"{#ASHOST}\":\"").append(s).append("\"},");
                    } 
             }
        }

        res.deleteCharAt(res.length()-1);
        res.append("]}");
        //res = res.substring(0, res.length() - 1) + "]}";

        return res.toString();
    }
    
    public static String discoverAs(JBossApi api, int index) throws CommandFormatException, IOException
    {
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");

        for (String s : nodes)
        {
            if (!s.contains("/")){
                List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server-config)");
                for (String a : ass)
                {
                      if (!isSkipping(ApplicationServerSkip, ApplicationServerAdd, "applicationserver", index, a)) {
                           res.append(" { \"{#ASHOST}\":\"").append(s).append("\", \"{#ASNAME}\":\"").append(a).append( "\"},");
                      }
                }
            }
        }
        res.deleteCharAt(res.length()-1);
        res.append("]}");
        return  res.toString();
    }

    public static String discoverDatasources(JBossApi api, int index) throws CommandFormatException, IOException
    {
        
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=profile)");

        for (String s : nodes)
        {
            if (!s.contains("/")){
                List<String> ass = api.runListQuery("/profile=" + s + "/subsystem=datasources/:read-children-names(child-type=data-source)");
                 
                for (String a : ass)
                {
                    if (!isSkipping(DatasourcesSkip,  DatasourcesAdd, "datasources", index, a)) {
                        res.append("{ \"{#DSPROFILE}\":\"").append(s).append("\", \"{#DSNAME}\":\"").append(a).append("\"},");
                    }
                }
            }
        }

        res.deleteCharAt(res.length()-1);
        res.append("]}");
        return  res.toString();
    }
    
    public static String discoverDatasourcesOnly(JBossApi api, int index) throws CommandFormatException, IOException
    {
        
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=profile)");
        Set<String> treedps = new TreeSet<>();
        
        for (String s : nodes)
        {
            if (!s.contains("/")){
                List<String> ass = api.runListQuery("/profile=" + s + "/subsystem=datasources/:read-children-names(child-type=data-source)");
                 
                for (String a : ass)
                {
                    if (!isSkipping(DatasourcesSkip,  DatasourcesAdd, "datasources", index, a)) {
                        treedps.add(a);
                    }
                }
            }
        }
        
        for (String tdp : treedps) {
              res.append("{ \"{#DSNAME}\":\"").append(tdp).append("\"},");
        }

        if (treedps.size()>0) res.deleteCharAt(res.length()-1);
        res.append("]}");
        return  res.toString();
    }

    public static String discoverDeployments(JBossApi api) throws CommandFormatException, IOException
    {
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=server-group)");

        for (String s : nodes)
        {
            if (!s.contains("/")){
                List<String> ass = api.runListQuery("/server-group=" + s + "/:read-children-names(child-type=deployment)");
             
                for (String a : ass)
                {
                    res.append(" { \"{#SRVGROUP}\":\"").append(s).append("\", \"{#DPNAME}\":\"").append(a).append("\"},");
                }            
            }
        }

        res.deleteCharAt(res.length()-1);
        res.append("]}");
        return  res.toString();
    }
    
     public static String discoverDeploymentsOnAs(JBossApi api, int index) throws CommandFormatException, IOException
    {
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");
      
        for (String s : nodes)
        {
            
            if (!s.contains("/")){
                if (!isSkipping(HostSkip,  HostAdd, "host", index, s)) { 
                    List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server)");
                    for (String a : ass)
                    {
                           // if (!a.contains("/")){
                            List<String> dps = api.runListQuery("/host=" + s + "/server=" + a + "/:read-children-names(child-type=deployment)");

                            for (String d : dps)
                            {
                                res.append(" { \"{#DPHOST}\":\"").append(s).append("\", \"{#DPAS}\":\"").append(a).append("\", \"{#DPNAME}\":\"").append(d).append("\"},");
                            }
                          //  }

                    }
                }
            }
        }

        res.deleteCharAt(res.length()-1);
        res.append("]}");
        return  res.toString();
    }
    //++++ nowe 12092021
    public static String discoverDeploymentsOnlyOnAs(JBossApi api, int index) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");
        Set<String> treedps = new TreeSet<>();
       
        
        for (String s : nodes)
        {
            
            if (!s.contains("/")){
                if (!isSkipping(HostSkip,  HostAdd, "host", index, s)) { 
                    List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server)");
                    for (String a : ass)
                    {
                           // if (!a.contains("/")){
                            List<String> dps = api.runListQuery("/host=" + s + "/server=" + a + "/:read-children-names(child-type=deployment)");

                            for (String d : dps)
                            {
                                treedps.add(d);
                            }
                          //  }

                    }
                }
            }
        }
        
        for (String tdp : treedps) {
             res += " { \"{#DPNAME}\":\"" + tdp + "\"},";
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;
    }
 public static String discoverDatasourcesOnAs(JBossApi api, int index) throws CommandFormatException, IOException
    {
        StringBuilder res = new StringBuilder();
        res.append("{ \"data\": [");
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");
       
   
        for (String s : nodes)
        {
            if (!s.contains("/")){            
                if (!isSkipping(HostSkip,  HostAdd, "host", index, s)) {           
                    List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server)");

                    for (String a : ass)
                    {
                         if (!isSkipping(ServerSkip,  ServerAdd, "server", index, a)) {
                            List<String> dps = api.runListQuery("/host=" + s + "/server=" + a + "/subsystem=datasources/:read-children-names(child-type=data-source)");
                            for (String d : dps)
                            {
                                  if (!isSkipping(DatasourcesSkip,  DatasourcesAdd, "datasources", index, d)) {
                                       res.append(" { \"{#DSHOST}\":\"").append(s).append("\", \"{#DSAS}\":\"").append(a).append("\", \"{#DSNAME}\":\"").append(d).append("\"},");
                                 }
                            }
                        }

                    }
                }
            }
        }

        res.deleteCharAt(res.length()-1);
        res.append("]}");
        return  res.toString();
    }
 
   
    
    
    public static String discoverServerGroupsJVM(JBossApi api, int index) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=server-group)");
          
        for (String s : nodes)
        {
            if (!s.contains("/")){  
                
                    List<String> ass = api.runListQuery("/server-group=" + s + "/:read-children-names(child-type=jvm)");

                    for (String a : ass)
                    {
                          res += " { \"{#DGROUP}\":\"" + s + "\", \"{#JVM}\":\"" + a + "\"},";
                  
                    }
              
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;
    }
    
     public static String discoverMessagingOnAs(JBossApi api, int index) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");
           
        
        for (String s : nodes)
        {
            if (!s.contains("/")){
                if (!isSkipping(HostSkip, HostAdd, "host", index, s)) {  
                    List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server)");

                    for (String a : ass) {
                        if (!isSkipping(ServerSkip, ServerAdd, "server", index, a)) {

                            List<String> dps = api.runListQuery( "/host=" + s + "/server=" + a + "/subsystem=messaging/hornetq-server=default:read-children-names(child-type=jms-queue)");
                            for (String d : dps)
                            {
                                if (!isSkipping(MessagingSkip, MessagingAdd,"messaging", index, d)) {
                                      res += " { \"{#HQHOST}\":\"" + s + "\", \"{#HQAS}\":\"" + a + "\", \"{#HQNAME}\":\"" + d + "\"},";
                                }
                            }


                       }
                    }
                }
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;
    }
    
}
    

