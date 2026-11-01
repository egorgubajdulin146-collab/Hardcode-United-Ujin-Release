CREATE TABLE publications (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    owner VARCHAR(120) NOT NULL,
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    local_value DOUBLE PRECISION NOT NULL,
    plan TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX publications_device_owner ON publications(device_id, owner, created_at);
