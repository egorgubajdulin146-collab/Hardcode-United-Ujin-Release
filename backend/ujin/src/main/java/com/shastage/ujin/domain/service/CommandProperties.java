package com.shastage.ujin.domain.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operator switch for the Ujin command path. Disabled by default: with the flag
 * off no command is dispatched and the task source is never called.
 */
@Component
@ConfigurationProperties(prefix = "ujin.commands")
public class CommandProperties {
    /** Master switch for apartment/send-signal dispatch. */
    private boolean enabled;
    /**
     * Opt-in to the documented task source (bmsdevice/get-device-info). The
     * resident role has historically been denied this method; it stays off until
     * organizers confirm the permitted role or endpoint.
     */
    private boolean taskFetchEnabled;
    /** Confirmed full-name prefix of platform signals (short name = full minus prefix). */
    private String taskSignalPrefix = "";
    /** Our observation window for a command result, in seconds (not a platform SLA). */
    private long observationWindowSeconds = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTaskFetchEnabled() {
        return taskFetchEnabled;
    }

    public void setTaskFetchEnabled(boolean taskFetchEnabled) {
        this.taskFetchEnabled = taskFetchEnabled;
    }

    public String getTaskSignalPrefix() {
        return taskSignalPrefix;
    }

    public void setTaskSignalPrefix(String taskSignalPrefix) {
        this.taskSignalPrefix = taskSignalPrefix == null ? "" : taskSignalPrefix;
    }

    public long getObservationWindowSeconds() {
        return observationWindowSeconds;
    }

    public void setObservationWindowSeconds(long observationWindowSeconds) {
        this.observationWindowSeconds = observationWindowSeconds;
    }
}
