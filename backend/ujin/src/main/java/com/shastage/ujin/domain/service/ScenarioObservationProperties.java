package com.shastage.ujin.domain.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Наблюдение показаний Ujin для сценариев. Выключено по умолчанию: сохранение
 * черновика и запуск наблюдения разделены, наблюдение запускается только при
 * явно включённом серверном флаге.
 */
@Component
@ConfigurationProperties(prefix = "ujin.scenarios")
public class ScenarioObservationProperties {
    private boolean observationEnabled;

    public boolean isObservationEnabled() {
        return observationEnabled;
    }

    public void setObservationEnabled(boolean observationEnabled) {
        this.observationEnabled = observationEnabled;
    }
}
