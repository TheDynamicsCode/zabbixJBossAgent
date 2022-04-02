/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package biz.szydlowski.zabbixjbossagent;

import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.cnxProps;
import biz.szydlowski.utils.MonitorThread;
import biz.szydlowski.utils.RejectedExecutionHandlerImpl;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ServerSocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author dkbu
 */
public class ZabbixClientMaintenance extends Thread {

     static final Logger log =  LogManager.getLogger(ZabbixClientMaintenance.class);

    private boolean running = false;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor executorPool=null;
    private Socket clientSocket;
    Properties p = null;
    MonitorThread monitor;
    Thread monitorThread ;
    protected Thread runningThread = null;
     
   ZabbixClientMaintenance ()  {
        // Connect to the domain controller
        this.p= cnxProps;
        
        clientSocket = null;

        // Create Zabbix listener
        
        RejectedExecutionHandlerImpl rejectionHandler = new RejectedExecutionHandlerImpl();

        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        //creating the ThreadPoolExecutor
        executorPool = new ThreadPoolExecutor(Integer.parseInt(p.getProperty("thread_pool_min", "2")), 
                Integer.parseInt(p.getProperty("thread_pool_max", "4")), Integer.parseInt(p.getProperty("thread_keep_alive_time", "10")), 
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(Integer.parseInt(p.getProperty("blocking_queuee_size", "10"))), threadFactory, rejectionHandler);
        executorPool.allowCoreThreadTimeOut(true);
      
         //start the monitoring thread
        monitor = new MonitorThread(executorPool, Integer.parseInt(p.getProperty("monitor_thread_interval", "60")) );
        monitorThread = new Thread(monitor);
        monitorThread.setName("monitorThreadMaintenance");
     
       
    }
    
    @Override
    public void run(){     
        
        synchronized(this){
            this.runningThread = Thread.currentThread();   
            this.runningThread.setName("ZabbixClientMaintenance");
        }
        
         monitorThread.start();
           
        try {
            openServerSocket();
            running  = true;
        } catch (Exception e){
            running  = false;
            log.error(e);
            
        }   

       // Wait for connections
        while (isRunning()) {
            
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException ex) {
                log.error("Error E01 " + ex);
            }
           
       
            executorPool.execute(new ZabbixClientMaintenanceThread(clientSocket, p));
           
        }

        log.info("Zabbix client has shut down");
    
    }
      
    private synchronized boolean isRunning() {
        return this.running ;
    }
   
    public void stopSever() {
        this.running = false;
        try {
            log.info("Currently active threads: " + Thread.activeCount());
            interruptAll();
            this.serverSocket.close();
        } catch (IOException e) {
            log.error("Error closing server", e);
        }
    }

    
   private void interruptAll(){ 
       executorPool.shutdownNow();
       executorPool.purge();
       monitor.shutdown();
   }

    private void openServerSocket() { 
        try {
            // ExecutorService pool = Executors.newFixedThreadPool(Integer.parseInt(p.getProperty("thread_pool", "10")));
            serverSocket = ServerSocketFactory.getDefault().createServerSocket(Integer.parseInt(p.getProperty("local_port_for_maintenance", "9750")),50);
        } catch (IOException ex) {
            log.error(ex);
        } 
        
        log.info("Create serverSocket " +  serverSocket.getLocalPort());

    }
}