package com.shastage.ujin.web.dto.response;

import com.shastage.ujin.web.dto.*;

import java.util.*;

public record DeviceDependenciesResponse(
        UUID deviceId, List<UUID> scenarioIds, List<UUID> pendingOperationIds) {}
