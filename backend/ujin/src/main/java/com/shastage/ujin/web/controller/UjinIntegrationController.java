package com.shastage.ujin.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports implemented capabilities, without probing or controlling platform devices. */
@RestController
public class UjinIntegrationController {
    public record Capability(String status, String reason) {}
    public record Status(Capability publish, Capability read, Capability downlink, Capability autoscripts) {}

    @GetMapping("/api/v1/integrations/ujin/status")
    public Status status() {
        return new Status(
                new Capability("BLOCKED", "Публикация устройств на платформу пока не реализована"),
                new Capability("UNKNOWN", "Адаптер чтения подключён; доступ и наличие сигнала проверяются отдельным запросом чтения"),
                new Capability("BLOCKED", "Подтверждённая доставка команд Ujin пока не реализована"),
                new Capability("BLOCKED", "Сценарии на платформе Ujin пока не подключены"));
    }
}
