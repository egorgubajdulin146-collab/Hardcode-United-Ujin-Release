CREATE TABLE local_signal_state (
    id UUID NOT NULL PRIMARY KEY,
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);

CREATE TABLE runtime_events (
    event_id UUID NOT NULL PRIMARY KEY,
    device_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    processed_at VARCHAR(40)
);

CREATE TABLE runtime_commands (
    command_id UUID NOT NULL PRIMARY KEY,
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    execution_mode VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    confirmed_at VARCHAR(40)
);

CREATE TABLE runtime_runs (
    id UUID NOT NULL PRIMARY KEY,
    event_id UUID NOT NULL,
    scenario_id UUID NOT NULL,
    scenario_version BIGINT NOT NULL,
    execution_mode VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(120),
    created_at VARCHAR(40) NOT NULL,
    input_signal_key VARCHAR(64),
    input_value DOUBLE PRECISION,
    input_updated_at VARCHAR(40),
    action_device_id UUID,
    action_signal_key VARCHAR(64),
    action_value DOUBLE PRECISION
);

CREATE TABLE runtime_run_entries (
    id UUID NOT NULL PRIMARY KEY,
    run_id UUID NOT NULL,
    at VARCHAR(40) NOT NULL,
    stage VARCHAR(16) NOT NULL,
    message VARCHAR(1000) NOT NULL
);

CREATE UNIQUE INDEX uk_local_signal_state ON local_signal_state(device_id, signal_key);
CREATE INDEX idx_local_signal_state_device ON local_signal_state(device_id);
CREATE INDEX idx_runtime_events_device ON runtime_events(device_id);
CREATE INDEX idx_runtime_runs_event ON runtime_runs(event_id);
CREATE INDEX idx_runtime_runs_scenario ON runtime_runs(scenario_id);
CREATE INDEX idx_runtime_run_entries_run ON runtime_run_entries(run_id);
