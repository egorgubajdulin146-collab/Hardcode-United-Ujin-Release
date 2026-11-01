package com.shastage.ujin.domain.service;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.UUID;

@Component
@ConfigurationProperties(prefix="ujin.local-python")
public class PythonEmulatorProperties {
    private boolean enabled;
    private String owner="";
    private UUID sourceDeviceId, targetDeviceId;
    private URI leakUrl=URI.create("http://leak:8080"), lightUrl=URI.create("http://light:8080");
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getOwner(){return owner;} public void setOwner(String v){owner=v;}
    public UUID getSourceDeviceId(){return sourceDeviceId;} public void setSourceDeviceId(UUID v){sourceDeviceId=v;}
    public UUID getTargetDeviceId(){return targetDeviceId;} public void setTargetDeviceId(UUID v){targetDeviceId=v;}
    public URI getLeakUrl(){return leakUrl;} public void setLeakUrl(URI v){leakUrl=v;}
    public URI getLightUrl(){return lightUrl;} public void setLightUrl(URI v){lightUrl=v;}
    public boolean source(UUID id,String user){return enabled && id!=null && id.equals(sourceDeviceId) && owner.equals(user);}
    public boolean target(UUID id,String user){return enabled && id!=null && id.equals(targetDeviceId) && owner.equals(user);}
    public boolean selected(UUID id,String user){return source(id,user)||target(id,user);}
}
