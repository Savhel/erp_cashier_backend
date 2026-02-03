CREATE TABLE IF NOT EXISTS rt_sync_state (
    organization_id TEXT PRIMARY KEY,
    last_synced_at TIMESTAMP
);
