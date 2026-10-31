package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "runtime_runs")
public class RunEntity {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "scenario_version", nullable = false)
    private long scenarioVersion;

    @Column(name = "execution_mode", nullable = false, length = 8)
    private String executionMode;

    @Column(nullable = false, length = 16)
    private String status; // PENDING, SUCCEEDED, SKIPPED, FAILED, BLOCKED, UNKNOWN

    @Column(length = 120)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    // Входное событие
    @Column(name = "input_signal_key", length = 64)
    private String inputSignalKey;

    @Column(name = "input_value")
    private Double inputValue;

    @Column(name = "input_updated_at")
    private String inputUpdatedAt;

    // Действие
    @Column(name = "action_device_id")
    private UUID actionDeviceId;

    @Column(name = "action_signal_key", length = 64)
    private String actionSignalKey;

    @Column(name = "action_value")
    private Double actionValue;

    // Журнал записей
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("at ASC")
    private List<RunEntryEntity> entries = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getScenarioId() { return scenarioId; }
    public void setScenarioId(UUID scenarioId) { this.scenarioId = scenarioId; }
    public long getScenarioVersion() { return scenarioVersion; }
    public void setScenarioVersion(long scenarioVersion) { this.scenarioVersion = scenarioVersion; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getInputSignalKey() { return inputSignalKey; }
    public void setInputSignalKey(String inputSignalKey) { this.inputSignalKey = inputSignalKey; }
    public Double getInputValue() { return inputValue; }
    public void setInputValue(Double inputValue) { this.inputValue = inputValue; }
    public String getInputUpdatedAt() { return inputUpdatedAt; }
    public void setInputUpdatedAt(String inputUpdatedAt) { this.inputUpdatedAt = inputUpdatedAt; }
    public UUID getActionDeviceId() { return actionDeviceId; }
    public void setActionDeviceId(UUID actionDeviceId) { this.actionDeviceId = actionDeviceId; }
    public String getActionSignalKey() { return actionSignalKey; }
    public void setActionSignalKey(String actionSignalKey) { this.actionSignalKey = actionSignalKey; }
    public Double getActionValue() { return actionValue; }
    public void setActionValue(Double actionValue) { this.actionValue = actionValue; }
    public List<RunEntryEntity> getEntries() { return entries; }
    public void setEntries(List<RunEntryEntity> entries) { this.entries = entries; }

    public void addEntry(String stage, String message, String at) {
        RunEntryEntity entry = new RunEntryEntity();
        entry.setRun(this);
        entry.setStage(stage);
        entry.setMessage(message);
        entry.setAt(at);
        entries.add(entry);
    }
}
