/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package biz.szydlowski.zabbixjbossagent;

import java.util.HashMap;

/**
 *
 * @author Dominik
 */
public class WorkingObjects {
    
    public static HashMap<String, String> HostSkip = new HashMap<>(); 
    public static HashMap<String, String> ServerSkip = new HashMap<>(); 
    public static HashMap<String, String> ProfileSkip = new HashMap<>(); 
    public static HashMap<String, String> DatasourcesSkip = new HashMap<>(); 
    public static HashMap<String, String> MessagingSkip = new HashMap<>(); 
    public static HashMap<String, String> ApplicationServerSkip = new HashMap<>(); 
    
    public static HashMap<String, String> HostAdd = new HashMap<>(); 
    public static HashMap<String, String> ServerAdd = new HashMap<>(); 
    public static HashMap<String, String> ProfileAdd = new HashMap<>(); 
    public static HashMap<String, String> DatasourcesAdd = new HashMap<>(); 
    public static HashMap<String, String> MessagingAdd = new HashMap<>();
    public static HashMap<String, String> ApplicationServerAdd = new HashMap<>();
    
}
