package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "devices")
public class DeviceEntity {
    @Id private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 64)
    private String family;

    @Column(name = "platform_link", nullable = false, length = 24)
    private String platformLink;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "device_id", nullable = false)
    @OrderColumn(name = "signal_order")
    private List<SignalEntity> signals;

    @ElementCollection
    @CollectionTable(name = "device_events", joinColumns = @JoinColumn(name = "device_id"))
    @OrderColumn(name = "event_order")
    private List<EventDefinition> events;

    public DeviceEntity() {}

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

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getPlatformLink() {
        return platformLink;
    }

    public void setPlatformLink(String platformLink) {
        this.platformLink = platformLink;
    }

    public List<SignalEntity> getSignals() {
        return signals;
    }

    public void setSignals(List<SignalEntity> signals) {
        this.signals = signals;
    }

    public List<EventDefinition> getEvents() {
        return events;
    }

    public void setEvents(List<EventDefinition> events) {
        this.events = events;
    }
}
