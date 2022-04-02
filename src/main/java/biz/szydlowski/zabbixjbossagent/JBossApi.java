package biz.szydlowski.zabbixjbossagent;


import static biz.szydlowski.zabbixjbossagent.JBossZabbixDaemon.absolutePath;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.dmr.ModelNode;
import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class handling all interactions with the JBoss CLI
 */
public class JBossApi implements Closeable
{
    static final Logger log =  LogManager.getLogger(JBossApi.class);

    DomainClient c;
    CommandContext ctx;
    final Properties cnxProps;


    public static JBossApi create(final Properties p, int i)
    {
      
        // Connect to the JBoss domain controller
        JBossApi api = new JBossApi(p, i);
        return api;
    }

    private JBossApi(final Properties p, int i)
    {
        this.cnxProps = p;
        try {
            reconnect(i);
        } catch (Exception e){
            log.error("E002 "  + e);
        }
    }

    private void reconnect(int i)
    {
        CallbackHandler m = new CallbackHandler()
        {
            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
            {
                for (Callback current : callbacks)
                {
                    log.debug("Authentication callback was called with callback of type " + current.getClass());
                    if (current instanceof NameCallback)
                    {
                        NameCallback ncb = (NameCallback) current;
                        ncb.setName(cnxProps.getProperty("jboss_admin_user."+i));
                    }
                    else if (current instanceof PasswordCallback)
                    {
                        PasswordCallback pcb = (PasswordCallback) current;
                        pcb.setPassword(cnxProps.getProperty("jboss_admin_password."+i).toCharArray());
                    }
                    else if (current instanceof RealmCallback)
                    {
                        RealmCallback rcb = (RealmCallback) current;
                        rcb.setText(rcb.getDefaultText());
                    }
                    else
                    {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            }
        };

        try
        {
            c = DomainClient.Factory.create(InetAddress.getByName(cnxProps.getProperty("jboss_server_name."+i)),
                    Integer.parseInt(cnxProps.getProperty("jboss_server_port."+i)), m);
            
       
            ctx = CommandContextFactory.getInstance().newCommandContext();
            ctx.setResolveParameterValues(false);
            ctx.setSilent(true);
            
            ctx.setCurrentDir(new File(absolutePath));
                       
        }
        catch (Exception e)
        {
            log.error(e);
        }
    }

    @Override
    public synchronized void close() throws IOException
    {
        c.close();
    }

    public synchronized String runSingleQuery(String query, String attr) throws CommandFormatException, IOException
    {
        log.trace("running query " + query);
        ModelNode n = ctx.buildRequest(query);
        ModelNode rq = c.execute(n);

        if (!rq.get("outcome").asString().toUpperCase().equals("SUCCESS"))
        {
            throw new RuntimeException(rq.get("failure-description").asString());
        }

        if (attr != null)
        {
            if (rq.get("result").get(attr).asString().length()==0){
                log.error("length()==0, running query " + query);
                return "0";
            } else  return rq.get("result").get(attr).asString();
        }
        else
        {
           if (rq.get("result").asString().length()==0){
               log.error("length()==0, running query " + query);
               return "0";
           } else
           return rq.get("result").asString();
        }
    }

    public synchronized List<String> runListQuery(String query) throws CommandFormatException, IOException
    {
        log.trace("running query " + query);
        
        ModelNode n = ctx.buildRequest(query);
        ModelNode rq = c.execute(n);
        List<String> res = new ArrayList<>();
        
       // log.debug("running query reponse " + rq.toString());
           
        if (!rq.get("outcome").asString().toUpperCase().equals("SUCCESS"))
        {
             log.error("running query (with errors) " + query);
             log.error(rq.get("failure-description"));
    
            //throw new RuntimeException(rq.get("failure-description").asString());
        } else {


            for (ModelNode r : rq.get("result").asList())
            {
                if (r.asString().length()==0) {
                    log.error("length()==0, running query " + query);
                    res.add("0");
                }
                else res.add(r.asString());
            }
        }

        return res;
    }
}
