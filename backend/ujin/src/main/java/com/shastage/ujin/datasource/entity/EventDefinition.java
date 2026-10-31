package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

@Embeddable
public class EventDefinition {
    @Column(name = "event_key", nullable = false, length = 64)
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Column(name = "signal_key", nullable = false, length = 64)
    private String signalKey;

    public String getSignalKey() {
        return signalKey;
    }

    public void setSignalKey(String signalKey) {
        this.signalKey = signalKey;
    }

    @Column(name = "emission", nullable = false, length = 16)
    private String emission;

    public String getEmission() {
        return emission;
    }

    public void setEmission(String emission) {
        this.emission = emission;
    }
}
