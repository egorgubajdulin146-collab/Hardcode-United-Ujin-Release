CREATE TABLE platform_bindings (
    device_id UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    owner VARCHAR(120) NOT NULL,
    serial_number VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL
);
CREATE TABLE platform_binding_signals (
    device_id UUID NOT NULL REFERENCES platform_bindings(device_id) ON DELETE CASCADE,
    signal_key VARCHAR(64) NOT NULL,
    platform_name VARCHAR(255) NOT NULL,
    PRIMARY KEY(device_id, signal_key)
);
