// Типы соответствуют контракту 0.1.2-draft (docs/contracts/openapi.json)

export type ExecutionMode = "LOCAL" | "UJIN";

export type SignalGroup = "property" | "capability" | "setting";
export type Management = "r" | "rw" | "w";
export type ValueType = "NUMBER";
export type Control = "list" | "switch" | "slider" | "counter" | "input";

export type PlatformLink =
  | "NOT_CONFIGURED"
  | "CONFIGURING"
  | "LINKED"
  | "LINK_FAILED";

export type Availability = "ONLINE" | "OFFLINE" | "UNKNOWN";

export type RunStatus =
  | "PENDING"
  | "SUCCEEDED"
  | "FAILED"
  | "BLOCKED"
  | "SKIPPED"
  | "UNKNOWN";

export type EventStatus = "ACCEPTED" | "PROCESSED" | "FAILED";

export type CommandStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "UNKNOWN";

export type UjinComponentStatus = "UNKNOWN" | "OK" | "FAILED" | "BLOCKED";

export interface Signal {
  key: string;
  title: string;
  group: SignalGroup;
  management: Management;
  valueType: ValueType;
  control: Control;
  enumValues?: number[];
  unit?: string;
  min?: number;
  max?: number;
}

export interface Device {
  id: string;
  version: number;
  title: string;
  family: string;
  platformLink: PlatformLink;
  signals: Signal[];
}

export interface DeviceWrite {
  title: string;
  family: string;
  signals: Signal[];
}

export interface SignalValue {
  signalKey: string;
  value: number;
  updatedAt: string;
}

export interface DeviceState {
  deviceId: string;
  executionMode: ExecutionMode;
  values: SignalValue[];
  availability: Availability;
}

export interface Trigger {
  deviceId: string;
  signalKey: string;
  operator: "EQ" | "NE" | "GT" | "LT" | "GE" | "LE";
  value: number;
  eventKey: string;
}

export interface Action {
  deviceId: string;
  signalKey: string;
  value: number;
}

export interface Scenario {
  id: string;
  version: number;
  title: string;
  enabled: boolean;
  executionMode: ExecutionMode;
  trigger: Trigger;
  action: Action;
}

export interface ScenarioWrite {
  title: string;
  enabled: boolean;
  executionMode: ExecutionMode;
  trigger: Trigger;
  action: Action;
}

export interface Stimulus {
  eventId: string;
  kind: "WET" | "DRY";
  value: number;
}

export interface EventReceipt {
  eventId: string;
  status: EventStatus;
  runIds: string[];
}

export interface RunInput {
  signalKey: string;
  value: number;
  updatedAt: string;
}

export interface RunAction {
  deviceId: string;
  signalKey: string;
  value: number;
  result?: "SUCCEEDED" | "FAILED" | "UNKNOWN";
}

export interface Run {
  id: string;
  eventId: string;
  scenarioId: string;
  scenarioVersion: number;
  executionMode: ExecutionMode;
  status: RunStatus;
  createdAt: string;
  input: RunInput;
  action: RunAction;
  skipReason?: string;
  error?: { code: string; detail: string };
}

export interface RunPage {
  items: Run[];
  nextCursor?: string;
}

export interface SystemStatus {
  contractVersion: string;
  supportedModes: ExecutionMode[];
}

export interface UjinComponentState {
  status: UjinComponentStatus;
  reason: string;
}

export interface UjinStatus {
  publish: UjinComponentState;
  read: UjinComponentState;
  downlink: UjinComponentState;
  autoscripts: UjinComponentState;
}

export interface ProblemError {
  field: string;
  message: string;
}

export interface Problem {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  errors?: ProblemError[];
}

export interface DeviceDependencies {
  deviceId: string;
  scenarioIds: string[];
  pendingOperationIds: string[];
}

export interface CommandReceipt {
  commandId: string;
  deviceId: string;
  status: CommandStatus;
  executionMode: ExecutionMode;
  resolution?: {
    kind: string;
    note: string;
    resolvedAt: string;
  };
}
