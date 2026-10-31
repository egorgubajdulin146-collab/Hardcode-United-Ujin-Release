package com.shastage.ujin.domain.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Operator-assigned destinations; the browser cannot choose an external target. */
@Component
@ConfigurationProperties(prefix = "ujin.publication")
public class PublicationProperties {
    private boolean enabled;
    private String ust = "";
    private long dictionaryDelayMs = 2000;
    private List<Target> targets = new ArrayList<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUst() { return ust; }
    public void setUst(String ust) { this.ust = ust; }
    public long getDictionaryDelayMs() { return dictionaryDelayMs; }
    public void setDictionaryDelayMs(long value) { dictionaryDelayMs = value; }
    public List<Target> getTargets() { return targets; }
    public void setTargets(List<Target> targets) { this.targets = targets; }

    public static class Target {
        private String serialNumber, extGuid, apartmentId, model, publishClass;
        private List<Signal> signals = new ArrayList<>();
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String v) { serialNumber = v; }
        public String getExtGuid() { return extGuid; }
        public void setExtGuid(String v) { extGuid = v; }
        public String getApartmentId() { return apartmentId; }
        public void setApartmentId(String v) { apartmentId = v; }
        public String getModel() { return model; }
        public void setModel(String v) { model = v; }
        public String getPublishClass() { return publishClass; }
        public void setPublishClass(String v) { publishClass = v; }
        public List<Signal> getSignals() { return signals; }
        public void setSignals(List<Signal> v) { signals = v; }
    }
    public static class Signal {
        private String signalKey, platformName, shortName;
        private Double zeroWireValue;
        public String getSignalKey() { return signalKey; }
        public void setSignalKey(String v) { signalKey = v; }
        public String getPlatformName() { return platformName; }
        public void setPlatformName(String v) { platformName = v; }
        public String getShortName() { return shortName; }
        public void setShortName(String v) { shortName = v; }
        public Double getZeroWireValue() { return zeroWireValue; }
        public void setZeroWireValue(Double v) { zeroWireValue = v; }
    }
}
