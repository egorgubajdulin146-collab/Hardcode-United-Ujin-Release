package com.shastage.ujin.datasource.entity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name="local_device_availability")
public class LocalAvailabilityEntity {
    @Id private UUID deviceId;
    @Column(nullable=false, length=16) private String availability;
    public LocalAvailabilityEntity() {}
    public LocalAvailabilityEntity(UUID deviceId, String availability) { this.deviceId=deviceId; this.availability=availability; }
    public String getAvailability() { return availability; }
}
