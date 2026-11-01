package com.shastage.ujin.domain.service;
import com.shastage.ujin.domain.exception.CatalogException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.util.Map;

/** Private fixed operator destinations; no credentials and no URLs from requests. */
@Component
public class PythonEmulatorGateway {
    private final JsonMapper json=JsonMapper.builder().build();
    public double read(URI base,String family,String key){return value(exchange(base,"/state",null),family,key);}
    public void command(URI base,double value){exchange(base,"/command",Map.of("signalKey","lamp","value",value));}
    private JsonNode exchange(URI base,String path,Map<String,Object> body){
        if(base==null || !"http".equals(base.getScheme()) || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
            || base.getHost()==null || !java.util.Set.of("127.0.0.1","localhost","leak","light").contains(base.getHost())
            || !(base.getPath().isEmpty() || base.getPath().equals("/")))
            throw new CatalogException(503,"PYTHON_NOT_CONFIGURED","Недопустимый адрес локального эмулятора на сервере");
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)URI.create(base.toString().replaceAll("/+$","")+path).toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(2000);c.setReadTimeout(3000);c.setRequestProperty("Accept","application/json");
            if(body!=null){c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);
                byte[] bytes=json.writeValueAsBytes(body);c.setFixedLengthStreamingMode(bytes.length);try(var out=c.getOutputStream()){out.write(bytes);}}
            int status=c.getResponseCode();
            if(status!=200)throw new CatalogException(status>=400&&status<500?422:503,status>=400&&status<500?"PYTHON_REJECTED":"PYTHON_UNAVAILABLE","Локальный Python-эмулятор отклонил запрос или недоступен");
            try(var in=c.getInputStream()){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw invalid();return json.readTree(bytes);}
        }catch(IOException ex){throw new CatalogException(503,"PYTHON_UNAVAILABLE","Нет ответа локального Python-эмулятора");}
        catch(RuntimeException ex){if(ex instanceof CatalogException ce)throw ce;throw invalid();}
        finally{if(c!=null)c.disconnect();}
    }
    private double value(JsonNode root,String family,String key){
        if(root==null || !family.equals(root.path("family").asString("")) || !"LOCAL".equals(root.path("executionMode").asString("")))throw invalid();
        var value=root.path("values").path(key);if(!value.isNumber())throw invalid();double n=value.asDouble();if(n!=0 && n!=1)throw invalid();return n;
    }
    private CatalogException invalid(){return new CatalogException(502,"PYTHON_BAD_RESPONSE","Некорректное состояние локального Python-эмулятора");}
}
