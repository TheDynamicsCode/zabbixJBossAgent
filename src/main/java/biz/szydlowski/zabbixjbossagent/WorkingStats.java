/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package biz.szydlowski.zabbixjbossagent;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Dominik
 */
public class WorkingStats {
    
    static long start=0;
    static List<Integer> errorCount= new ArrayList<>();  
    static List<Integer> okCount= new ArrayList<>();     
    static List<Integer> connError = new ArrayList<>(); 
    static int activeThreads = 0;
    static int javaUsedMemory = 0;
    static List<Integer> ZabbixClientTimeoutCount = new ArrayList<>(); 
    static List<Long> ZabbixClientIdleTime = new ArrayList<>(); 
    static List<Long> ZabbixClientStart= new ArrayList<>(); 
    
    public static void start(int controllers){
        start=System.currentTimeMillis();
        
        for (int index=0; index<controllers; index++){
            ZabbixClientIdleTime.add(start);
            ZabbixClientTimeoutCount.add(0);
            errorCount.add(0);
            okCount.add(0);
            connError.add(0);
            ZabbixClientStart.add(start);
        }
    }   
    
    public static long getUptime(){
        return (System.currentTimeMillis()-start)/1000;
    }   
    
    public static long getZabbixClientIdleTime(int index){
        return (System.currentTimeMillis()- ZabbixClientIdleTime.get(index))/1000;
    } 
    
    public static long getZabbixClientUptime(int index){
        return (System.currentTimeMillis()- ZabbixClientStart.get(index))/1000;
    }   
         
       
    public static int getJavaUsedMemory(){
        return javaUsedMemory;
    }   
      
    public static void errorCountPlus(int index){
       int tmp=errorCount.get(index);
       tmp++;
       errorCount.set(index, tmp); 
    }  
    
    public static void connErrorPlus(int index){
       int tmp=connError.get(index);
       tmp++;
       connError.set(index, tmp);
    }    
    
    public static void okCountPlus(int index){
       int tmp=okCount.get(index);
       tmp++;
       okCount.set(index, tmp); 
    }   
      
   public static synchronized void syncZabbixClientTimeoutPlus(int index) {
       int tmp = ZabbixClientTimeoutCount.get(index);
       tmp++;
       ZabbixClientTimeoutCount.set(index, tmp);       
    }  
    
    public static synchronized void syncZabbixClientTimeoutReset(int index) {
       ZabbixClientTimeoutCount.set(index, 0);
    }
    
    public static synchronized void syncZabbixClientIdleTime(int index) {
       ZabbixClientIdleTime.set(index, System.currentTimeMillis());
    }
   
    public static synchronized void syncZabbixClientStart(int index) {
        ZabbixClientStart.set(index, System.currentTimeMillis());
    }
      
}