package com.shastage.ujin.web.demo;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
@Configuration
public class GuestDemoLifecycle {
    @Bean ServletListenerRegistrationBean<HttpSessionListener> guestDemoListener(GuestDemoService guests) {
        return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
            @Override public void sessionDestroyed(HttpSessionEvent event) {
                var scope = guests.scope(event.getSession());
                if (scope != null) try { guests.close(scope); }
                catch (RuntimeException unavailable) {
                    org.slf4j.LoggerFactory.getLogger(GuestDemoLifecycle.class).warn("Guest sandbox cleanup deferred");
                }
            }
        });
    }
}
