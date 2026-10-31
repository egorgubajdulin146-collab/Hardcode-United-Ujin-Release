package com.shastage.ujin.datasource.client;

import java.util.List;

/** Один снимок apartment/get-device-signals: устройства резидента и их сигналы. */
public record PlatformDevice(String id, List<PlatformSignal> signals) {

    public record PlatformSignal(String name, ValueKind kind, Double number, String text, Boolean bool, String dt) {
        public enum ValueKind {
            NUMBER,
            STRING,
            BOOLEAN,
            NULL,
            OTHER
        }

        public static PlatformSignal ofName(String name) {
            return new PlatformSignal(name, ValueKind.NULL, null, null, null, null);
        }
    }
}
