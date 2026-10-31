package com.shastage.ujin.web.dto.request;

import java.util.UUID;

public record StimulusRequest(UUID eventId, String kind, Integer value) {}
