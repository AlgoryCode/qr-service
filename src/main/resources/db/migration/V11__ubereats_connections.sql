CREATE TABLE ubereats_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    store_id VARCHAR(128) NOT NULL,
    client_id_encrypted TEXT NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error TEXT,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ubereats_connections_menu
    ON ubereats_connections (menu_id);

CREATE INDEX idx_ubereats_connections_user
    ON ubereats_connections (user_id, updated_at DESC);
