package com.shastage.ujin.web.dto.request;

import com.shastage.ujin.web.dto.*;

import java.util.*;

public record DeviceUpdateRequest(
        String title,
        String family,
        List<SignalDefinitionDto> signals,
        List<EventDefinitionDto> events,
        Long expectedVersion) {}
