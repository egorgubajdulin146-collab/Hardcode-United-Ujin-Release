package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** Последнее принятое нормализованное значение входа сценария (baseline). */
@Entity
@Table(name = "scenario_observations")
public class ScenarioObservationEntity {
    @Id
    @Column(name = "scenario_id")
    private UUID scenarioId;

    @Column(nullable = false, length = 120)
    private String owner;

    @Column(name = "input_signal_key", nullable = false, length = 64)
    private String inputSignalKey;

    @Column(name = "baseline_value", nullable = false)
    private double baselineValue;

    @Column(name = "updated_at", nullable = false, length = 40)
    private String updatedAt;

    @Column(name = "source_identity", length = 64)
    private String sourceIdentity;
    @Column(name = "accepted_read_order", nullable = false)
    private long acceptedReadOrder;
    @Column(name = "measured_at", length = 40)
    private String measuredAt;
    public String getSourceIdentity() { return sourceIdentity; }
    public void setSourceIdentity(String value) { sourceIdentity = value; }
    public long getAcceptedReadOrder() { return acceptedReadOrder; }
    public void setAcceptedReadOrder(long value) { acceptedReadOrder = value; }
    public String getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(String value) { measuredAt = value; }

    public ScenarioObservationEntity() {}

    public UUID getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(UUID scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getInputSignalKey() {
        return inputSignalKey;
    }

    public void setInputSignalKey(String inputSignalKey) {
        this.inputSignalKey = inputSignalKey;
    }

    public double getBaselineValue() {
        return baselineValue;
    }

    public void setBaselineValue(double baselineValue) {
        this.baselineValue = baselineValue;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
