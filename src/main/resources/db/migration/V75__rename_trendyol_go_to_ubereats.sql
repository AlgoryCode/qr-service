ALTER TABLE IF EXISTS ubereats_connections RENAME TO ubereats_menu_connections;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'uk_ubereats_connections_menu') THEN
    ALTER INDEX uk_ubereats_connections_menu RENAME TO uk_ubereats_menu_connections_menu;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_ubereats_connections_user') THEN
    ALTER INDEX idx_ubereats_connections_user RENAME TO idx_ubereats_menu_connections_user;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'tbl_trendyol_go_connection'
  ) THEN
    ALTER TABLE tbl_trendyol_go_connection RENAME TO ubereats_connections;
  ELSIF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'ubereats_connections'
  ) THEN
    CREATE TABLE ubereats_connections (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL,
      branch_id BIGINT NOT NULL,
      seller_id VARCHAR(64) NOT NULL,
      api_key_encrypted TEXT NOT NULL,
      api_secret_encrypted TEXT NOT NULL,
      restaurant_id VARCHAR(64),
      restaurant_name VARCHAR(255),
      status VARCHAR(32) NOT NULL,
      last_error TEXT,
      last_synced_at TIMESTAMP,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE UNIQUE INDEX uk_ubereats_connections_user_branch
      ON ubereats_connections (user_id, branch_id);
    CREATE INDEX idx_ubereats_connections_restaurant
      ON ubereats_connections (restaurant_id);
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'tbl_trendyol_go_order'
  ) THEN
    ALTER TABLE tbl_trendyol_go_order RENAME TO ubereats_orders;
  ELSIF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'ubereats_orders'
  ) THEN
    CREATE TABLE ubereats_orders (
      id BIGSERIAL PRIMARY KEY,
      connection_id BIGINT NOT NULL,
      external_order_id VARCHAR(128) NOT NULL,
      package_status VARCHAR(64),
      total_amount NUMERIC(12, 2),
      currency VARCHAR(8),
      customer_name VARCHAR(255),
      customer_phone VARCHAR(64),
      delivery_address TEXT,
      note TEXT,
      items_json JSONB,
      raw_payload JSONB,
      package_created_at TIMESTAMP,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE UNIQUE INDEX uk_ubereats_orders_connection_external
      ON ubereats_orders (connection_id, external_order_id);
    CREATE INDEX idx_ubereats_orders_connection_status
      ON ubereats_orders (connection_id, package_status);
    CREATE INDEX idx_ubereats_orders_created
      ON ubereats_orders (package_created_at);
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_tgo_connection_user_branch') THEN
    ALTER INDEX idx_tgo_connection_user_branch RENAME TO uk_ubereats_connections_user_branch;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_tgo_connection_restaurant') THEN
    ALTER INDEX idx_tgo_connection_restaurant RENAME TO idx_ubereats_connections_restaurant;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_tgo_order_connection_external') THEN
    ALTER INDEX idx_tgo_order_connection_external RENAME TO uk_ubereats_orders_connection_external;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_tgo_order_connection_status') THEN
    ALTER INDEX idx_tgo_order_connection_status RENAME TO idx_ubereats_orders_connection_status;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_tgo_order_created') THEN
    ALTER INDEX idx_tgo_order_created RENAME TO idx_ubereats_orders_created;
  END IF;
END $$;
