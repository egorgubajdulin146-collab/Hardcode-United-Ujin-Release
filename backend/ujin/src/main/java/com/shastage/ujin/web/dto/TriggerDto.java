package com.shastage.ujin.web.dto;

import java.util.*;

public record TriggerDto(
        UUID deviceId, String signalKey, String operator, Double value, String eventKey) {}
