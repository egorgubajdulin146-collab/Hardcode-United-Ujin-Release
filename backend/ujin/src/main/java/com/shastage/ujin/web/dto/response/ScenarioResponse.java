package com.shastage.ujin.web.dto.response;

import com.shastage.ujin.web.dto.*;
import java.util.*;

public record ScenarioResponse(UUID id, long version, String title, boolean enabled, String executionMode, TriggerDto trigger, ActionDto action) {}
