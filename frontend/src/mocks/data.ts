import type {
  Device,
  DevicePage,
  DeviceState,
  Run,
  RunPage,
  Scenario,
  ScenarioPage,
  SystemStatus,
  UjinStatus,
} from "../api/types";

// Мок-данные соответствуют docs/contracts/openapi.json 0.1.2-draft
// и docs/contracts/examples.json

export const mockSystemStatus: SystemStatus = {
  contractVersion: "0.1.2-draft",
  supportedModes: ["LOCAL"],
};

export const mockDevices: Device[] = [
  {
    id: "10000000-0000-4000-8000-000000000001",
    version: 1,
    title: "Датчик протечки",
    family: "leak",
    signals: [
      {
        key: "leak",
        title: "Протечка",
        group: "property",
        management: "r",
        valueType: "NUMBER",
        control: "list",
        enumValues: [0, 1],
      },
      {
        key: "battery",
        title: "Батарея",
        group: "property",
        management: "r",
        valueType: "NUMBER",
        control: "counter",
        unit: "%",
        min: 0,
        max: 100,
      },
    ],
    platformLink: "NOT_CONFIGURED",
    events: [
      {
        key: "leak_changed",
        title: "Протечка изменилась",
        signalKey: "leak",
        emission: "CHANGE",
      },
    ],
  },
  {
    id: "10000000-0000-4000-8000-000000000002",
    version: 1,
    title: "Светильник",
    family: "light",
    signals: [
      {
        key: "lamp",
        title: "Свет",
        group: "capability",
        management: "rw",
        valueType: "NUMBER",
        control: "switch",
        enumValues: [0, 1],
      },
      {
        key: "brightness",
        title: "Яркость",
        group: "capability",
        management: "rw",
        valueType: "NUMBER",
        control: "slider",
        min: 0,
        max: 100,
      },
      {
        key: "offline_timeout",
        title: "Таймаут офлайн",
        group: "setting",
        management: "rw",
        valueType: "NUMBER",
        control: "counter",
        unit: "с",
        min: 0,
        max: 3600,
      },
    ],
    platformLink: "NOT_CONFIGURED",
    events: [],
  },
];

export const mockDevicePage: DevicePage = {
  items: mockDevices,
  page: 0,
  size: 20,
  total: 2,
};

export const mockScenarios: Scenario[] = [
  {
    id: "20000000-0000-4000-8000-000000000001",
    version: 1,
    title: "Протечка — включить свет",
    enabled: true,
    executionMode: "LOCAL",
    trigger: {
      deviceId: "10000000-0000-4000-8000-000000000001",
      signalKey: "leak",
      operator: "EQ",
      value: 1,
      eventKey: "leak_changed",
    },
    action: {
      deviceId: "10000000-0000-4000-8000-000000000002",
      signalKey: "lamp",
      value: 1,
    },
  },
];

export const mockScenarioPage: ScenarioPage = {
  items: mockScenarios,
  page: 0,
  size: 20,
  total: 1,
};

export const mockDeviceStates: Record<string, DeviceState> = {
  "10000000-0000-4000-8000-000000000001": {
    deviceId: "10000000-0000-4000-8000-000000000001",
    executionMode: "LOCAL",
    values: [
      { signalKey: "leak", value: 0, updatedAt: "2026-10-31T10:00:00+05:00" },
      { signalKey: "battery", value: 95, updatedAt: "2026-10-31T10:00:00+05:00" },
    ],
    availability: "ONLINE",
  },
  "10000000-0000-4000-8000-000000000002": {
    deviceId: "10000000-0000-4000-8000-000000000002",
    executionMode: "LOCAL",
    values: [
      { signalKey: "lamp", value: 0, updatedAt: "2026-10-31T10:00:00+05:00" },
      { signalKey: "brightness", value: 100, updatedAt: "2026-10-31T10:00:00+05:00" },
      { signalKey: "offline_timeout", value: 0, updatedAt: "2026-10-31T10:00:00+05:00" },
    ],
    availability: "ONLINE",
  },
};

export const mockRuns: Run[] = [
  {
    id: "40000000-0000-4000-8000-000000000001",
    eventId: "30000000-0000-4000-8000-000000000001",
    scenarioId: "20000000-0000-4000-8000-000000000001",
    scenarioVersion: 1,
    executionMode: "LOCAL",
    status: "SUCCEEDED",
    createdAt: "2026-10-31T10:05:00+05:00",
    entries: [
      { at: "2026-10-31T10:05:00+05:00", stage: "EVENT", message: "Событие leak_changed" },
      { at: "2026-10-31T10:05:00+05:00", stage: "CONDITION", message: "Условие leak EQ 1 — истинно" },
      { at: "2026-10-31T10:05:00+05:00", stage: "ACTION", message: "Команда lamp=1" },
      { at: "2026-10-31T10:05:01+05:00", stage: "RESULT", message: "Состояние lamp=1 подтверждено" },
    ],
    input: { signalKey: "leak", value: 1, updatedAt: "2026-10-31T10:05:00+05:00" },
    action: { deviceId: "10000000-0000-4000-8000-000000000002", signalKey: "lamp", value: 1 },
  },
  {
    id: "40000000-0000-4000-8000-000000000002",
    eventId: "30000000-0000-4000-8000-000000000002",
    scenarioId: "20000000-0000-4000-8000-000000000001",
    scenarioVersion: 1,
    executionMode: "LOCAL",
    status: "SKIPPED",
    createdAt: "2026-10-31T10:06:00+05:00",
    entries: [
      { at: "2026-10-31T10:06:00+05:00", stage: "EVENT", message: "Событие leak_changed" },
      { at: "2026-10-31T10:06:00+05:00", stage: "CONDITION", message: "Условие leak EQ 0 — ложно" },
    ],
    input: { signalKey: "leak", value: 0, updatedAt: "2026-10-31T10:06:00+05:00" },
    reason: "CONDITION_FALSE",
  },
  {
    id: "40000000-0000-4000-8000-000000000003",
    eventId: "30000000-0000-4000-8000-000000000003",
    scenarioId: "20000000-0000-4000-8000-000000000001",
    scenarioVersion: 1,
    executionMode: "LOCAL",
    status: "BLOCKED",
    createdAt: "2026-10-31T10:07:00+05:00",
    entries: [
      { at: "2026-10-31T10:07:00+05:00", stage: "EVENT", message: "Событие leak_changed" },
      { at: "2026-10-31T10:07:00+05:00", stage: "CONDITION", message: "Условие leak EQ 1 — истинно" },
      { at: "2026-10-31T10:07:00+05:00", stage: "ACTION", message: "Устройство недоступно" },
    ],
    input: { signalKey: "leak", value: 1, updatedAt: "2026-10-31T10:07:00+05:00" },
    reason: "DEVICE_UNAVAILABLE",
  },
];

export const mockRunPage: RunPage = {
  items: mockRuns,
  page: 0,
  size: 20,
  total: 3,
};

export const mockUjinStatus: UjinStatus = {
  publish: { status: "UNKNOWN", reason: "Ещё не проверено" },
  read: { status: "UNKNOWN", reason: "Ещё не проверено" },
  downlink: { status: "UNKNOWN", reason: "Ещё не проверено" },
  autoscripts: { status: "UNKNOWN", reason: "Ещё не проверено" },
};
