package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "platform_task_acks")
public class TaskAckEntity {
    @EmbeddedId private TaskAckId id;

    @Column(name = "task_key", nullable = false, length = 255)
    private String taskKey;

    @Column(name = "applied_at", nullable = false, length = 40)
    private String appliedAt;

    public TaskAckEntity() {}

    public TaskAckEntity(UUID deviceId, String signalKey) {
        this.id = new TaskAckId(deviceId, signalKey);
    }

    public TaskAckId getId() {
        return id;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(String appliedAt) {
        this.appliedAt = appliedAt;
    }

    @Embeddable
    public static class TaskAckId implements Serializable {
        @Column(name = "device_id", nullable = false)
        private java.util.UUID deviceId;

        @Column(name = "signal_key", nullable = false, length = 64)
        private String signalKey;

        public TaskAckId() {}

        public TaskAckId(java.util.UUID deviceId, String signalKey) {
            this.deviceId = deviceId;
            this.signalKey = signalKey;
        }

        public java.util.UUID getDeviceId() {
            return deviceId;
        }

        public String getSignalKey() {
            return signalKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TaskAckId that)) return false;
            return Objects.equals(deviceId, that.deviceId)
                    && Objects.equals(signalKey, that.signalKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, signalKey);
        }
    }
}
