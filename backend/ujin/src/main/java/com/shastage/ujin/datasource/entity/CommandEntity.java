package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "runtime_commands")
public class CommandEntity {
    @Id
    @Column(name = "command_id")
    private UUID commandId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "signal_key", nullable = false, length = 64)
    private String signalKey;

    @Column(name = "signal_value", nullable = false)
    private double value;

    @Column(name = "execution_mode", nullable = false, length = 8)
    private String executionMode;

    @Column(nullable = false, length = 16)
    private String status; // PENDING, CONFIRMED, FAILED, UNKNOWN

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "confirmed_at")
    private String confirmedAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @Column(length = 80)
    private String reason;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private String resolvedAt;

    @Column(name = "owner", length = 120)
    private String owner;

    @Column(name = "task_baseline_key", length = 255)
    private String taskBaselineKey;
    public String getTaskBaselineKey() { return taskBaselineKey; }
    public void setTaskBaselineKey(String key) { this.taskBaselineKey = key; }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getSignalKey() { return signalKey; }
    public void setSignalKey(String signalKey) { this.signalKey = signalKey; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getConfirmedAt() { return confirmedAt; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setConfirmedAt(String confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
}
