package biz.szydlowski.zabbixjbossagent;

import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.cnxProps;
import biz.szydlowski.utils.MonitorThread;
import biz.szydlowski.utils.RejectedExecutionHandlerImpl;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import javax.net.ServerSocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class represents a TCP server that implements the Zabbix Client protocol. It therefore can be queries directly by a Zabbix server.
 * Parameters are found inside the conf.properties file.
 */
public class ZabbixClient  extends Thread {


     static final Logger log =  LogManager.getLogger(ZabbixClient.class);

    private boolean running = false;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor executorPool=null;
    private Socket clientSocket;
    JBossApi api = null;
    Properties p = null;
    int index = 0;
    MonitorThread monitor;
    Thread monitorThread ;
    protected Thread runningThread = null;
     
    ZabbixClient(int i)  {
        // Connect to the domain controller
        this.p= cnxProps;
        
        api = JBossApi.create(p, i);
        this.index = i;

        // Utility items
       // StringWriter writer = null;
        clientSocket = null;
       // InputStream iss = null;

        // Create Zabbix listener
        
        RejectedExecutionHandlerImpl rejectionHandler = new RejectedExecutionHandlerImpl();
        
        //public ThreadPoolExecutor(int corePoolSize,
        //          int maximumPoolSize,
        //          long keepAliveTime,
        //          TimeUnit unit,
        //          BlockingQueue<Runnable> workQueue)
        
         //Get the ThreadFactory implementation to use
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        //creating the ThreadPoolExecutor
        executorPool = new ThreadPoolExecutor(Integer.parseInt(p.getProperty("thread_pool_min", "2")), 
                Integer.parseInt(p.getProperty("thread_pool_max", "4")), Integer.parseInt(p.getProperty("thread_keep_alive_time", "10")), 
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(Integer.parseInt(p.getProperty("blocking_queuee_size", "10"))), threadFactory, rejectionHandler);
        executorPool.allowCoreThreadTimeOut(true);
      
         //start the monitoring thread
        monitor = new MonitorThread(executorPool, Integer.parseInt(p.getProperty("monitor_thread_interval", "60")) );
        monitorThread = new Thread(monitor);
        monitorThread.setName("monitorThread - " + index);
     
       
    }
    
    @Override
    public void run(){     
        
        synchronized(this){
            this.runningThread = Thread.currentThread();   
            this.runningThread.setName("ZabbixClient-"+index);
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
           
           // InputStream is =  clientSocket.getInputStream();
       
            executorPool.execute(new ZabbixClientThread(clientSocket, api, p, index));
           
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
            serverSocket = ServerSocketFactory.getDefault().createServerSocket(Integer.parseInt(p.getProperty("local_port_for_controler."+index, "9752")),50);
        } catch (IOException ex) {
            log.error(ex);
        } 
        
        log.info("Create serverSocket " +  serverSocket.getLocalPort());

    }
}
