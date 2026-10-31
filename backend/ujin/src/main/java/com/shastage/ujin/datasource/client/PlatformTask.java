package com.shastage.ujin.datasource.client;

import java.util.List;

/** Задача платформы для сигнала устройства (bmsdevice/get-device-info, поле task). */
public record PlatformTask(String signalName, String taskValue, String taskDt, String currentValue) {

    public String dedupKey() {
        if (taskDt != null && !taskDt.isBlank()) return taskDt;
        return signalName + "=" + taskValue;
    }
}
