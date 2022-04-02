/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package biz.szydlowski.zabbixjbossagent;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Dominik
 */
public class CacheValue {
     
     private static  ConcurrentHashMap<String, String> cacheMap = new ConcurrentHashMap<>();
     private static  ConcurrentHashMap<String, Long> cacheLastTime = new ConcurrentHashMap<>();
     private static  ConcurrentHashMap<String, Integer> cacheHits = new ConcurrentHashMap<>();
     private static long max_time = 600000; //5minut
     private static int max_hits=3;
     
     public static int getCacheValueSize(){
         return cacheMap.size();
     }
     
     public static void setCacheValue(String key, String value){
         cacheMap.put(key, value);
         cacheLastTime.put(key, System.currentTimeMillis());
         cacheHits.put(key, 0);
     }
     
     public static String getCacheValue(String key){
         int t = cacheHits.getOrDefault(key, 0);
         t++;
         cacheHits.put(key, t);
         return cacheMap.get(key);
     }
     
     public static boolean isValueInCacheValid(String key){
         long t = cacheLastTime.getOrDefault(key, 0L);
         return System.currentTimeMillis()-t < max_time;
     }
     
      public static boolean isValueInCacheHitsValid(String key){
         int t = cacheHits.getOrDefault(key, 0);
         return t < max_hits;
     }
     
     
    
}
