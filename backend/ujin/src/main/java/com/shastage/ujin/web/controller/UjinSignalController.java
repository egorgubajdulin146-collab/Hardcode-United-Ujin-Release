package com.shastage.ujin.web.controller;

import com.shastage.ujin.datasource.client.UjinApiClient;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ujin/devices")
public class UjinSignalController {
    private final UjinApiClient client;

    public UjinSignalController(@Value("${ujin.api.base-url}") URI baseUrl) {
        this.client = new UjinApiClient(baseUrl);
    }

    @GetMapping("/{serialnumber}/signals")
    public SignalValue read(@PathVariable String serialnumber, @RequestParam String name, HttpSession session) {
        Object token = session.getAttribute("UJIN_TOKEN");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return new SignalValue(name, client.readSignal(value, serialnumber, name));
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException error) {
            if ("Ujin rejected the request".equals(error.getMessage())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if ("Requested signal was not found".equals(error.getMessage())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            if ("Unable to read Ujin API; check connection and access".equals(error.getMessage())) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
    }

    public record SignalValue(String name, String value) {}
}
