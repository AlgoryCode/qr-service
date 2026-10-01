import json
from datetime import datetime, timezone
from urllib.request import Request, urlopen

import psycopg2

BATCH_ID = "batch_6aa25d96e3848190ad2d87f678761c33"
ITEM_ID = "17799efb-9369-4951-92c5-53db768e26b7"
BATCH_ROW_ID = "4cccfed9-bc11-40ab-ab6a-47629e9c1d12"
AI_BASE = "https://ai-service.algorycode.com"
API_KEY = "dev-ai-service-key"


def get(path: str):
    req = Request(AI_BASE + path, headers={"X-API-Key": API_KEY})
    with urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


conn = psycopg2.connect(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=10,
)
cur = conn.cursor()
cur.execute(
    """
    SELECT column_name, is_nullable
    FROM information_schema.columns
    WHERE table_name='tbl_smart_report_results'
    ORDER BY ordinal_position
    """
)
print("columns", cur.fetchall())

status = get(f"/api/v1/batch-reports/{BATCH_ID}/status")
results = get(f"/api/v1/batch-reports/{BATCH_ID}/results")
match = next(i for i in results if str(i.get("customId")) == ITEM_ID)
body = match.get("body") or {}
result_text = body.get("output_text")
if not result_text:
    chunks = []
    for part in body.get("output") or []:
        for content in part.get("content") or []:
            if content.get("text"):
                chunks.append(content["text"])
    result_text = "".join(chunks)

counts = status.get("requestCounts") or {}
now = datetime.now(timezone.utc).replace(tzinfo=None)

cur.execute("SELECT menu_id, branch_id FROM tbl_smart_report_events WHERE process_id=%s::uuid", (ITEM_ID,))
menu_id, branch_id = cur.fetchone()
print("menu_id", menu_id, "branch_id", branch_id)

conn.autocommit = False
cur.execute(
    """
    UPDATE tbl_batch_reports
    SET status=%s, request_total=%s, request_completed=%s, request_failed=%s,
        updated_at=%s, completed_at=%s
    WHERE id=%s::uuid
    """,
    (
        "completed",
        int(counts.get("total") or 1),
        int(counts.get("completed") or 1),
        int(counts.get("failed") or 0),
        now,
        now,
        BATCH_ROW_ID,
    ),
)
cur.execute(
    """
    UPDATE tbl_batch_report_items
    SET status=%s, result_json=%s::jsonb, error_message=NULL, updated_at=%s
    WHERE id=%s::uuid
    """,
    ("completed", json.dumps(body), now, ITEM_ID),
)
cur.execute(
    """
    UPDATE tbl_smart_report_events
    SET status=%s, error_code=NULL, error_message=NULL, updated_at=%s, completed_at=%s
    WHERE process_id=%s::uuid
    """,
    ("completed", now, now, ITEM_ID),
)
cur.execute("SELECT 1 FROM tbl_smart_report_results WHERE process_id=%s::uuid", (ITEM_ID,))
if cur.fetchone():
    cur.execute(
        "UPDATE tbl_smart_report_results SET result_text=%s WHERE process_id=%s::uuid",
        (result_text, ITEM_ID),
    )
else:
    # stage schema still has menu_id NOT NULL; branch reports use 0 sentinel
    cur.execute(
        """
        INSERT INTO tbl_smart_report_results (menu_id, process_id, result_text, created_at)
        VALUES (%s, %s::uuid, %s, %s)
        """,
        (menu_id if menu_id is not None else 0, ITEM_ID, result_text, now),
    )
conn.commit()
print("synced ok", "result_len", len(result_text))
conn.close()
