import json
import os
from datetime import datetime
from pathlib import Path

import psycopg2
from openai import OpenAI

BATCH_ID = "batch_6aa25d96e3848190ad2d87f678761c33"
ITEM_ID = "17799efb-9369-4951-92c5-53db768e26b7"
BATCH_ROW_ID = "4cccfed9-bc11-40ab-ab6a-47629e9c1d12"

env_path = Path(r"c:\Users\Tarik\Desktop\Services\ai-service\.env")
for line in env_path.read_text(encoding="utf-8").splitlines():
    if line.startswith("OPENAI_API_KEY="):
        os.environ["OPENAI_API_KEY"] = line.split("=", 1)[1].strip()
        break

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"], timeout=180.0)
batch = client.batches.retrieve(BATCH_ID)
counts = getattr(batch, "request_counts", None)
print("openai_status", batch.status)
print(
    "counts",
    getattr(counts, "total", None),
    getattr(counts, "completed", None),
    getattr(counts, "failed", None),
)
print("output_file_id", getattr(batch, "output_file_id", None))
print("error_file_id", getattr(batch, "error_file_id", None))

if batch.status != "completed":
    raise SystemExit("batch not completed yet")

output_file_id = batch.output_file_id
content = client.files.content(output_file_id)
raw = content.read() if hasattr(content, "read") else content
text = raw.decode("utf-8") if isinstance(raw, bytes) else str(raw)

result_text = None
result_body = None
for line in text.splitlines():
    if not line.strip():
        continue
    payload = json.loads(line)
    print("line_custom_id", payload.get("custom_id"), "error", bool(payload.get("error")))
    if str(payload.get("custom_id")) != ITEM_ID:
        continue
    if payload.get("error"):
        raise SystemExit(f"item error: {payload.get('error')}")
    response = payload.get("response") or {}
    body = response.get("body") if isinstance(response, dict) else None
    if not isinstance(body, dict):
        body = response if isinstance(response, dict) else {"raw": response}
    result_body = body
    result_text = body.get("output_text")
    if not result_text:
        chunks = []
        for item in body.get("output") or []:
            for part in item.get("content") or []:
                if part.get("text"):
                    chunks.append(part["text"])
        result_text = "".join(chunks)
    break

if not result_text:
    raise SystemExit("no result_text extracted")

print("result_text_len", len(result_text))
print("result_preview", result_text[:120].replace("\n", " "))

STAGE = dict(
    host="185.184.210.52",
    port=5433,
    dbname="algoryqrdb-stage",
    user="postgres",
    password="postgres_stage",
    sslmode="disable",
    connect_timeout=10,
)
conn = psycopg2.connect(**STAGE)
conn.autocommit = False
cur = conn.cursor()
now = datetime.utcnow()

cur.execute(
    """
    UPDATE tbl_batch_reports
    SET status=%s, request_total=%s, request_completed=%s, request_failed=%s,
        updated_at=%s, completed_at=%s
    WHERE id=%s::uuid
    """,
    (
        "completed",
        int(getattr(counts, "total", 1) or 1),
        int(getattr(counts, "completed", 1) or 1),
        int(getattr(counts, "failed", 0) or 0),
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
    ("completed", json.dumps(result_body), now, ITEM_ID),
)
cur.execute(
    """
    UPDATE tbl_smart_report_events
    SET status=%s, error_code=NULL, error_message=NULL, updated_at=%s, completed_at=%s
    WHERE process_id=%s::uuid
    """,
    ("completed", now, now, ITEM_ID),
)
cur.execute(
    "SELECT 1 FROM tbl_smart_report_results WHERE process_id=%s::uuid",
    (ITEM_ID,),
)
if cur.fetchone():
    cur.execute(
        "UPDATE tbl_smart_report_results SET result_text=%s WHERE process_id=%s::uuid",
        (result_text, ITEM_ID),
    )
else:
    cur.execute(
        """
        INSERT INTO tbl_smart_report_results (menu_id, process_id, result_text, created_at)
        SELECT menu_id, process_id, %s, %s
        FROM tbl_smart_report_events
        WHERE process_id=%s::uuid
        """,
        (result_text, now, ITEM_ID),
    )

conn.commit()
print("DB synced for", ITEM_ID)
conn.close()
