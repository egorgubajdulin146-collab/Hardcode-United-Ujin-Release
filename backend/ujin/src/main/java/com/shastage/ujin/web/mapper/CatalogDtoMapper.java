package com.shastage.ujin.web.mapper;

import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.web.dto.*;
import com.shastage.ujin.web.dto.request.*;
import com.shastage.ujin.web.dto.response.*;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

@Component
public class CatalogDtoMapper {
    public DeviceWrite toDomain(DeviceCreateRequest data) {
        return new DeviceWrite(
                data.title(), data.family(), signals(data.signals()), events(data.events()), null);
    }

    public DeviceWrite toDomain(DeviceUpdateRequest data) {
        return new DeviceWrite(
                data.title(),
                data.family(),
                signals(data.signals()),
                events(data.events()),
                data.expectedVersion());
    }

    private List<Signal> signals(List<SignalDefinitionDto> values) {
        return values == null
                ? null
                : values.stream()
                        .map(s ->
                                s == null
                                        ? null
                                        : new Signal(
                                                s.key(),
                                                s.title(),
                                                s.group(),
                                                s.management(),
                                                s.valueType(),
                                                s.control(),
                                                s.unit(),
                                                s.min(),
                                                s.max(),
                                                s.enumValues()))
                        .toList();
    }

    private List<Event> events(List<EventDefinitionDto> values) {
        return values == null
                ? null
                : values.stream()
                        .map(e ->
                                e == null
                                        ? null
                                        : new Event(
                                                e.key(), e.title(), e.signalKey(), e.emission()))
                        .toList();
    }

    public ScenarioWrite toDomain(ScenarioCreateRequest data) {
        return new ScenarioWrite(
                data.title(),
                data.enabled(),
                data.executionMode(),
                toDomain(data.trigger()),
                toDomain(data.action()),
                null);
    }

    public ScenarioWrite toDomain(ScenarioUpdateRequest data) {
        return new ScenarioWrite(
                data.title(),
                data.enabled(),
                data.executionMode(),
                toDomain(data.trigger()),
                toDomain(data.action()),
                data.expectedVersion());
    }

    private Trigger toDomain(TriggerDto value) {
        return value == null
                ? null
                : new Trigger(
                        value.deviceId(),
                        value.signalKey(),
                        value.operator(),
                        value.value(),
                        value.eventKey());
    }

    private Action toDomain(ActionDto value) {
        return value == null
                ? null
                : new Action(value.deviceId(), value.signalKey(), value.value());
    }

    public DeviceResponse toResponse(Device value) {
        return new DeviceResponse(
                value.id(),
                value.version(),
                value.title(),
                value.family(),
                value.signals().stream()
                        .map(s ->
                                new SignalDefinitionDto(
                                        s.key(),
                                        s.title(),
                                        s.group(),
                                        s.management(),
                                        s.valueType(),
                                        s.control(),
                                        s.unit(),
                                        s.min(),
                                        s.max(),
                                        s.enumValues()))
                        .toList(),
                value.events().stream()
                        .map(e ->
                                new EventDefinitionDto(
                                        e.key(), e.title(), e.signalKey(), e.emission()))
                        .toList(),
                value.platformLink());
    }

    public ScenarioResponse toResponse(Scenario value) {
        var t = value.trigger();
        var a = value.action();
        return new ScenarioResponse(
                value.id(),
                value.version(),
                value.title(),
                value.enabled(),
                value.executionMode(),
                new TriggerDto(t.deviceId(), t.signalKey(), t.operator(), t.value(), t.eventKey()),
                new ActionDto(a.deviceId(), a.signalKey(), a.value()));
    }

    public DeviceDependenciesResponse toResponse(Dependencies value) {
        return new DeviceDependenciesResponse(
                value.deviceId(), value.scenarioIds(), value.pendingOperationIds());
    }

    public <T, R> PageResponse<R> toResponse(Page<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
                page.items().stream().map(mapper).toList(), page.page(), page.size(), page.total());
    }
}
