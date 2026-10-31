import type {
  Device,
  DeviceState,
  Run,
  Scenario,
  SystemStatus,
  UjinStatus,
} from "../api/types";

// Мок-данные соответствуют docs/contracts/examples.json
// Режим MOCK — источник синтетических данных интерфейса, не доказательство интеграции

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
    platformLink: "NOT_CONFIGURED",
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
  },
  {
    id: "10000000-0000-4000-8000-000000000002",
    version: 1,
    title: "Светильник",
    family: "light",
    platformLink: "NOT_CONFIGURED",
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
  },
];

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
    input: {
      signalKey: "leak",
      value: 1,
      updatedAt: "2026-10-31T10:05:00+05:00",
    },
    action: {
      deviceId: "10000000-0000-4000-8000-000000000002",
      signalKey: "lamp",
      value: 1,
      result: "SUCCEEDED",
    },
  },
];

export const mockUjinStatus: UjinStatus = {
  publish: { status: "UNKNOWN", reason: "Ещё не проверено" },
  read: { status: "UNKNOWN", reason: "Ещё не проверено" },
  downlink: { status: "UNKNOWN", reason: "Ещё не проверено" },
  autoscripts: { status: "UNKNOWN", reason: "Ещё не проверено" },
};
