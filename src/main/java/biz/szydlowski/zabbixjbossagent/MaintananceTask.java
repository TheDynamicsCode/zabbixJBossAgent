/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package biz.szydlowski.zabbixjbossagent;

import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon._ZabbixClientList;
import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.absolutePath;
import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.executor_timer;
import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.jboss_domain_controllers;
import static biz.szydlowski.zabbixjbossagent.Version.DEV_VERSION_EXPIRE_DATE;
import biz.szydlowski.utils.AutorestartConfig;
import biz.szydlowski.utils.api.AutorestartApi;
import java.util.Date;
import java.util.Map;
import java.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author dkbu
 */
public class MaintananceTask extends TimerTask {
          
            static final Logger log =  LogManager.getLogger(MaintananceTask.class);
             
            int tick=0;
            int mb = 1024 * 1024; 
            Runtime runtime = Runtime.getRuntime();
           
            // get Runtime instance
              
            long maxMemory ;
            long allocatedMemory ;
            long freeMemory ;
            long usedMem ;
            long totalMem;
            
            boolean first=true;
            AutorestartApi autorestartApi = null;
           
                      
            @Override
            public void run(){
                
                   if (first){
                       AutorestartConfig autorestartConfig = new AutorestartConfig(absolutePath +"setting/autorestart.setting");         
                       autorestartApi = autorestartConfig.getAutorestartApi();                       
                       first=false;
                   }
                  
                   WorkingStats.activeThreads = Thread.activeCount();
                   WorkingStats.javaUsedMemory = (int) usedMem / mb;
                   
                   synchronisedTimer();
                  
                              
                   
                    for (int i=0; i<jboss_domain_controllers; i++){  
                      if ( WorkingStats.getZabbixClientIdleTime(i) > 60 && WorkingStats.ZabbixClientTimeoutCount.get(i) > 2){
                           log.error("Detected Idle for connector " + i); 
                           System.out.println("Detected Idle for connector " + i + " " + new Date()); 
                           autorestartApi.restart();    
                             
                      } else  if ( WorkingStats.getZabbixClientIdleTime(i) > 300 ){
                           log.error("Detected Idle (Time) for connector " + i); 
                           System.out.println("Detected Idle (Time) for connector  " + i + " " + new Date()); 
                           autorestartApi.restart();    
                         
                      } else if ( WorkingStats.ZabbixClientTimeoutCount.get(i) > 10){
                           log.error("Detected Idle (Count) for connector " + i); 
                           System.out.println("Detected Idle (Count) for connector " + i + " " + new Date()); 
                           autorestartApi.restart();       
                      } 
                    }
                    
                   if (WorkingStats.getUptime() > 3600*autorestartApi.getMaxUptime()){   
                        log.warn("MaxUptime");  
                        System.out.println("MaxUptime " + new Date()); 
                        autorestartApi.restart();                       
                    }
                   
                               
                   if (tick%50==0){ 
                     
                                           
                      maxMemory = runtime.maxMemory();
                      allocatedMemory = runtime.totalMemory();
                      freeMemory = runtime.freeMemory();
                      usedMem = allocatedMemory - freeMemory;
                      totalMem = runtime.totalMemory();
                        
                      log.info("**************************** Maintenance v20181025 ****************************");
                      log.info("Currently active threads: " + Thread.activeCount());
                      
                      executor_timer.keySet().forEach((key) -> {
                          long diff = System.currentTimeMillis() - executor_timer.get(key);
                          log.info("thread: " + key + ", diff_time " + diff);
                       });
                      
                      
                      if (DEV_VERSION_EXPIRE_DATE.getTime()-System.currentTimeMillis() < 432000000){
                           System.out.println("*********** DEV VERSION DATE EXPIRED IN "+DEV_VERSION_EXPIRE_DATE + " ***************");  
                      }
                       
                      if(DEV_VERSION_EXPIRE_DATE.before(new Date())) {
                          System.out.println("DEV VERSION DATE EXPIRED");
                          System.err.println("DEV VERSION DATE EXPIRED");
                          log.fatal("DEV VERSION DATE EXPIRED");
                          System.exit(2000);
                      }              
                        log.info("***** Heap utilization statistics [MB] *****");
                        // available memory
                        log.info("Total Memory: " + totalMem / mb);
                        // free memory
                        log.info("Free Memory: " + freeMemory / mb);
                        // used memory
                        log.info("Used Memory: " + usedMem / mb);
                        // Maximum available memory
                        log.info("Max Memory: " + maxMemory / mb);
                        
                        tick=0;
                    }
                   
                    tick++;
               
            } //end run
                    
            public synchronized void synchronisedTimer(){
                 
                    for (Map.Entry<String, Long> entry : executor_timer.entrySet()) { 
                         if (System.currentTimeMillis() - entry.getValue() > 120000){
                            log.error("Error thread: " + entry.getKey() + ", time: " + entry.getValue() + " , curr_time: " + System.currentTimeMillis());
                             autorestartApi.restart();
                        }
                    } 
            }
       

      }