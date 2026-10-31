package com.shastage.ujin.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports implemented capabilities, without probing or controlling platform devices. */
@RestController
public class UjinIntegrationController {
    private final com.shastage.ujin.domain.service.PublicationProperties publication;
    public UjinIntegrationController(com.shastage.ujin.domain.service.PublicationProperties publication) {
        this.publication = publication;
    }
    public record Capability(String status, String reason) {}
    public record Status(Capability publish, Capability read, Capability downlink, Capability autoscripts) {}

    @GetMapping("/api/v1/integrations/ujin/status")
    public Status status() {
        return new Status(
                new Capability(publication.isEnabled() ? "UNKNOWN" : "BLOCKED",
                        publication.isEnabled() ? "Публикация показаний подключена; результат подтверждается чтением для каждого устройства"
                                : "Публикация показаний выключена; требуется настройка назначенного устройства на сервере"),
                new Capability("UNKNOWN", "Адаптер чтения подключён; доступ и наличие сигнала проверяются отдельным запросом чтения"),
                new Capability("BLOCKED", "Подтверждённая доставка команд Ujin пока не реализована"),
                new Capability("BLOCKED", "Сценарии на платформе Ujin пока не подключены"));
    }
}
