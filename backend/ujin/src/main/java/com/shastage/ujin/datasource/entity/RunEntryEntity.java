package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "runtime_run_entries")
public class RunEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private RunEntity run;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false, length = 16)
    private String stage; // EVENT, CONDITION, ACTION, RESULT

    @Column(nullable = false, length = 1000)
    private String message;

    public UUID getId() { return id; }
    public RunEntity getRun() { return run; }
    public void setRun(RunEntity run) { this.run = run; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
