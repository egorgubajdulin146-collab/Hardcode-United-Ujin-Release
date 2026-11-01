package com.shastage.ujin.web.dto.response;

import com.shastage.ujin.web.dto.*;

import java.util.*;

public record DeviceResponse(
        UUID id,
        long version,
        String title,
        String family,
        List<SignalDefinitionDto> signals,
        List<EventDefinitionDto> events,
        String platformLink) {}
