package com.shastage.ujin.datasource.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class BindingSignal {
    @Column(name = "signal_key", nullable = false, length = 64)
    private String signalKey;

    @Column(name = "platform_name", nullable = false, length = 255)
    private String platformName;

    public BindingSignal() {}

    public BindingSignal(String signalKey, String platformName) {
        this.signalKey = signalKey;
        this.platformName = platformName;
    }

    public String getSignalKey() {
        return signalKey;
    }

    public void setSignalKey(String signalKey) {
        this.signalKey = signalKey;
    }

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BindingSignal that)) return false;
        return Objects.equals(signalKey, that.signalKey)
                && Objects.equals(platformName, that.platformName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalKey, platformName);
    }
}
