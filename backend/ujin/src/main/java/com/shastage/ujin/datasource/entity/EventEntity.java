package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "runtime_events")
public class EventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "signal_value", nullable = false)
    private double value;

    @Column(nullable = false, length = 16)
    private String status; // ACCEPTED, PROCESSED, FAILED

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "processed_at")
    private String processedAt;

    @Column(name = "owner", length = 120)
    private String owner;

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getProcessedAt() { return processedAt; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }
}
