#!/usr/bin/env python3
"""Smoke merchant-scoped API paths (stage/prod/local). Usage:
  python scripts/_merchant_id_smoke.py --base https://stage.qrapi.algorycode.com
  python scripts/_merchant_id_smoke.py --base http://localhost:8080 --token "$ACCESS_TOKEN"
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request


def request(method: str, url: str, token: str | None) -> tuple[int, str]:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, body[:500]
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, body[:500]
    except urllib.error.URLError as exc:
        return 0, str(exc.reason)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, help="API base URL without trailing slash")
    parser.add_argument("--token", default="", help="Optional Bearer access token")
    parser.add_argument("--merchant-id", type=int, default=0, help="Merchant id for path tests")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    token = args.token.strip() or None
    mid = args.merchant_id

    checks: list[tuple[str, str, int | None]] = [
        ("GET", f"{base}/access/context", None if not token else 200),
        ("GET", f"{base}/users/{mid or 1}/package", None if not token else 200),
        ("GET", f"{base}/qr/user/{mid or 1}?page=0&size=1", None if not token else 200),
        ("GET", f"{base}/branches", None if not token else 200),
        ("GET", f"{base}/account/myprofile", None if not token else 200),
    ]

    failed = 0
    for method, url, expect_ok in checks:
        status, snippet = request(method, url, token)
        if status == 0:
            ok = False
            label = "connection failed"
        elif expect_ok is None:
            ok = status in (401, 403)
            label = "auth required"
        else:
            ok = status == expect_ok
            label = f"expect {expect_ok}"
        mark = "OK" if ok else "FAIL"
        print(f"[{mark}] {method} {url} -> {status} ({label})")
        if not ok:
            failed += 1
            print(f"       body: {snippet}")

    if token and mid:
        status, body = request("GET", f"{base}/users/{mid}/package", token)
        if status == 200:
            try:
                payload = json.loads(body)
                user = payload.get("user") or {}
                has_id = "merchantId" in user and "userId" not in user
                print(f"[{'OK' if has_id else 'FAIL'}] session user id fields present: {list(user.keys())[:8]}")
                if not has_id:
                    failed += 1
            except json.JSONDecodeError:
                print("[FAIL] session package response is not JSON")
                failed += 1

    print(f"\nResult: {'PASS' if failed == 0 else 'FAIL'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
