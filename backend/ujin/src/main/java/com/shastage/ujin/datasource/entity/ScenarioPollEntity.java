package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** Результат запроса наблюдения; повтор pollId возвращает эту же запись. */
@Entity
@Table(name = "scenario_polls")
public class ScenarioPollEntity {
    @Id
    @Column(name = "poll_id")
    private UUID pollId;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(nullable = false, length = 120)
    private String owner;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 80)
    private String reason;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    public ScenarioPollEntity() {}

    public UUID getPollId() {
        return pollId;
    }

    public void setPollId(UUID pollId) {
        this.pollId = pollId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getObservedValue() {
        return observedValue;
    }

    public void setObservedValue(Double observedValue) {
        this.observedValue = observedValue;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
