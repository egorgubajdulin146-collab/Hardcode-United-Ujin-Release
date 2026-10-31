package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "platform_bindings")
public class PlatformBindingEntity {
    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, length = 120)
    private String owner;

    @Column(name = "serial_number", nullable = false, length = 128)
    private String serialNumber;

    @ElementCollection
    @CollectionTable(
            name = "platform_binding_signals",
            joinColumns = @JoinColumn(name = "device_id"))
    private Set<BindingSignal> signals = new LinkedHashSet<>();

    public PlatformBindingEntity() {}

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Set<BindingSignal> getSignals() {
        return signals;
    }

    public void setSignals(Set<BindingSignal> signals) {
        this.signals = signals;
    }
}
