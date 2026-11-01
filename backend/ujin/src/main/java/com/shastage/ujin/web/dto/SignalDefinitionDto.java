package com.shastage.ujin.web.dto;

import java.util.*;

@com.fasterxml.jackson.annotation.JsonInclude(
        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record SignalDefinitionDto(
        String key,
        String title,
        String group,
        String management,
        String valueType,
        String control,
        String unit,
        Double min,
        Double max,
        List<Double> enumValues) {}
