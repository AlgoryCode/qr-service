DO $$
DECLARE
  survivor_id BIGINT;
  loser RECORD;
BEGIN
  FOR survivor_id IN
    SELECT DISTINCT ON (user_id) id
    FROM ubereats_connections
    ORDER BY user_id,
      CASE WHEN status = 'CONNECTED' THEN 0
           WHEN status = 'PENDING_RESTAURANT' THEN 1
           WHEN status = 'ERROR' THEN 2
           ELSE 3 END,
      updated_at DESC NULLS LAST,
      id DESC
  LOOP
    FOR loser IN
      SELECT c.id
      FROM ubereats_connections c
      WHERE c.user_id = (SELECT user_id FROM ubereats_connections WHERE id = survivor_id)
        AND c.id <> survivor_id
    LOOP
      UPDATE ubereats_orders SET connection_id = survivor_id WHERE connection_id = loser.id;
      DELETE FROM ubereats_connections WHERE id = loser.id;
    END LOOP;
  END LOOP;
END $$;

DROP INDEX IF EXISTS uk_ubereats_connections_user_branch;
DROP INDEX IF EXISTS idx_tgo_connection_user_branch;

ALTER TABLE ubereats_connections DROP COLUMN IF EXISTS branch_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ubereats_connections_user
  ON ubereats_connections (user_id);
