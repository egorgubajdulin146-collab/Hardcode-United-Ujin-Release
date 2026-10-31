package com.shastage.ujin.web.controller;

import com.shastage.ujin.datasource.client.UjinApiClient;
import com.shastage.ujin.datasource.client.UjinApiException;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/ujin/devices")
public class UjinSignalController {
    private final UjinApiClient client;

    public UjinSignalController(@Value("${ujin.api.base-url}") URI baseUrl) {
        this.client = new UjinApiClient(baseUrl);
    }

    @GetMapping("/{serialnumber}/signals")
    public SignalValue read(
            @PathVariable String serialnumber, @RequestParam String name, HttpSession session) {
        Object token = session.getAttribute("UJIN_TOKEN");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return new SignalValue(name, client.readSignal(value, serialnumber, name));
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        } catch (UjinApiException error) {
            throw new ResponseStatusException(
                    switch (error.kind()) {
                        case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                        case FORBIDDEN, ACCESS_DENIED -> HttpStatus.FORBIDDEN;
                        case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                        case SIGNAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
                        case BAD_RESPONSE, PLATFORM_ERROR -> HttpStatus.BAD_GATEWAY;
                    },
                    error.detail());
        }
    }

    public record SignalValue(String name, String value) {}
}
