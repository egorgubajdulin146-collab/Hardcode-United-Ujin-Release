package com.shastage.ujin.web.controller;

import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.service.ScenarioObservationService;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Ручная проверка показания Ujin по сценарию; наблюдение — не скрытое расписание. */
@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioPollController {
    private final ScenarioObservationService service;

    public ScenarioPollController(ScenarioObservationService service) {
        this.service = service;
    }

    private String currentOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    private static String ujinToken(HttpSession session) {
        Object token = session.getAttribute("UJIN_TOKEN");
        return token instanceof String value ? value : null;
    }

    @PostMapping("/{scenarioId}/poll")
    public Map<String, Object> poll(
            @PathVariable UUID scenarioId,
            @RequestBody PollRequest request,
            HttpSession session) {
        if (request == null || request.pollId() == null) {
            throw new CatalogException(400, "INVALID_REQUEST", "Request body with pollId is required");
        }
        return service.poll(scenarioId, request.pollId(), currentOwner(), ujinToken(session));
    }

    public record PollRequest(UUID pollId) {}
}
