package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "scenarios")
public class ScenarioEntity {
    @Id private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "execution_mode", nullable = false, length = 16)
    private String executionMode;

    @Column(name = "owner", length = 120)
    private String owner;

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trigger_device_id", nullable = false)
    private DeviceEntity triggerDevice;

    @Column(name = "trigger_signal", nullable = false, length = 64)
    private String triggerSignal;

    @Column(name = "trigger_event", nullable = false, length = 64)
    private String triggerEvent;

    @Column(name = "trigger_operator", nullable = false, length = 8)
    private String operator;

    @Column(name = "trigger_value", nullable = false)
    private Double triggerValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_device_id", nullable = false)
    private DeviceEntity actionDevice;

    @Column(name = "action_signal", nullable = false, length = 64)
    private String actionSignal;

    @Column(name = "action_value", nullable = false)
    private Double actionValue;

    public ScenarioEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public DeviceEntity getTriggerDevice() {
        return triggerDevice;
    }

    public void setTriggerDevice(DeviceEntity triggerDevice) {
        this.triggerDevice = triggerDevice;
    }

    public String getTriggerSignal() {
        return triggerSignal;
    }

    public void setTriggerSignal(String triggerSignal) {
        this.triggerSignal = triggerSignal;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Double getTriggerValue() {
        return triggerValue;
    }

    public void setTriggerValue(Double triggerValue) {
        this.triggerValue = triggerValue;
    }

    public DeviceEntity getActionDevice() {
        return actionDevice;
    }

    public void setActionDevice(DeviceEntity actionDevice) {
        this.actionDevice = actionDevice;
    }

    public String getActionSignal() {
        return actionSignal;
    }

    public void setActionSignal(String actionSignal) {
        this.actionSignal = actionSignal;
    }

    public Double getActionValue() {
        return actionValue;
    }

    public void setActionValue(Double actionValue) {
        this.actionValue = actionValue;
    }
}
