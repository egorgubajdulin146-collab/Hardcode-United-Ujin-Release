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
            throw new com.shastage.ujin.domain.exception.CatalogException(
                    switch (error.kind()) {
                        case UNAUTHORIZED -> 401;
                        case FORBIDDEN, ACCESS_DENIED -> 403;
                        case UNAVAILABLE -> 503;
                        case SIGNAL_NOT_FOUND -> 404;
                        case BAD_RESPONSE, PLATFORM_ERROR -> 502;
                    },
                    "UJIN_" + error.kind().name(),
                    switch (error.kind()) {
                        case UNAUTHORIZED -> "Ujin authentication is required";
                        case FORBIDDEN, ACCESS_DENIED -> "No access to device data";
                        case UNAVAILABLE -> "Ujin platform is unavailable";
                        case SIGNAL_NOT_FOUND -> "Requested device or signal was not found";
                        case BAD_RESPONSE -> "Ujin returned an invalid response";
                        case PLATFORM_ERROR -> "Ujin returned a platform error";
                    });
        }
    }

    public record SignalValue(String name, String value) {}
}
