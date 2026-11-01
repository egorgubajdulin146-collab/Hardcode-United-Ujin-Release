package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "device_signals")
public class SignalEntity {
    @Id private UUID id;

    @Column(name = "signal_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "signal_group", nullable = false, length = 24)
    private String group;

    @Column(nullable = false, length = 4)
    private String management;

    @Column(name = "value_type", nullable = false, length = 16)
    private String valueType;

    @Column(nullable = false, length = 40)
    private String control;

    @Column(length = 24)
    private String unit;

    @Column(name = "min_value")
    private Double min;

    @Column(name = "max_value")
    private Double max;

    @ElementCollection
    @CollectionTable(name = "signal_enum_values", joinColumns = @JoinColumn(name = "signal_id"))
    @Column(name = "enum_value", nullable = false)
    @OrderColumn(name = "value_order")
    private List<Double> enumValues;

    public SignalEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getManagement() {
        return management;
    }

    public void setManagement(String management) {
        this.management = management;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getControl() {
        return control;
    }

    public void setControl(String control) {
        this.control = control;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public List<Double> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<Double> enumValues) {
        this.enumValues = enumValues;
    }
}
