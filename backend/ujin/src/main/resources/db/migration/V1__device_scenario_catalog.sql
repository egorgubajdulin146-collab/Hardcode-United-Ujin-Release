CREATE TABLE catalog_lock (id INTEGER PRIMARY KEY);
INSERT INTO catalog_lock (id) VALUES (1);
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    family VARCHAR(64) NOT NULL,
    platform_link VARCHAR(24) NOT NULL
);
CREATE TABLE device_signals (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id),
    signal_order INTEGER,
    signal_key VARCHAR(64) NOT NULL,
    title VARCHAR(120) NOT NULL,
    signal_group VARCHAR(24) NOT NULL,
    management VARCHAR(4) NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    control VARCHAR(40) NOT NULL,
    unit VARCHAR(24),
    min_value DOUBLE PRECISION,
    max_value DOUBLE PRECISION
);
CREATE INDEX device_signals_device_idx ON device_signals(device_id);
CREATE TABLE signal_enum_values (
    signal_id UUID NOT NULL REFERENCES device_signals(id),
    value_order INTEGER NOT NULL,
    enum_value DOUBLE PRECISION NOT NULL,
    PRIMARY KEY(signal_id,value_order)
);
CREATE TABLE device_events (
    device_id UUID NOT NULL REFERENCES devices(id),
    event_order INTEGER NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    title VARCHAR(120) NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    emission VARCHAR(16) NOT NULL,
    PRIMARY KEY(device_id,event_order)
);
CREATE TABLE scenarios (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    execution_mode VARCHAR(16) NOT NULL,
    trigger_device_id UUID NOT NULL REFERENCES devices(id),
    trigger_signal VARCHAR(64) NOT NULL,
    trigger_event VARCHAR(64) NOT NULL,
    trigger_operator VARCHAR(8) NOT NULL,
    trigger_value DOUBLE PRECISION NOT NULL,
    action_device_id UUID NOT NULL REFERENCES devices(id),
    action_signal VARCHAR(64) NOT NULL,
    action_value DOUBLE PRECISION NOT NULL
);
CREATE INDEX scenarios_trigger_device_idx ON scenarios(trigger_device_id);
CREATE INDEX scenarios_action_device_idx ON scenarios(action_device_id);
