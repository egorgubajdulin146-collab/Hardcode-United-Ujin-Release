package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "local_signal_state", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "signal_key"}))
public class LocalSignalStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "signal_key", nullable = false, length = 64)
    private String signalKey;

    @Column(name = "signal_value", nullable = false)
    private double value;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    @Column(name = "owner", length = 120)
    private String owner;

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getSignalKey() { return signalKey; }
    public void setSignalKey(String signalKey) { this.signalKey = signalKey; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getUpdatedAt() { return updatedAt; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
