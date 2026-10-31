package com.shastage.ujin.web.dto.request;

import com.shastage.ujin.web.dto.*;
import java.util.*;

public record ScenarioCreateRequest(String title, Boolean enabled, String executionMode, TriggerDto trigger, ActionDto action) {}
