package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publications")
public class PublicationEntity {
    @Id public UUID id;
    @Version public Long version;
    @Column(nullable = false, length = 120) public String owner;
    @Column(name = "device_id", nullable = false) public UUID deviceId;
    @Column(name = "signal_key", nullable = false, length = 64) public String signalKey;
    @Column(name = "local_value", nullable = false) public double value;
    @Column(nullable = false, columnDefinition = "text") public String plan;
    @Column(nullable = false, length = 16) public String status;
    @Column(nullable = false, length = 64) public String reason;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "observed_at") public Instant observedAt;
    public PublicationEntity() {}
}
