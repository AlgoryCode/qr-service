import json
import urllib.error
import urllib.request

PATHS = [
    "/menu/public/16",
    "/menu/public/id/35",
    "/menu/public/legacy-qr/35/public-id",
    "/menu/public/scxtG9KR5FrK0RlLjcYPUw",
    "/actuator/mappings",
    "/v3/api-docs",
]

for host in ["https://prod.qrapi.algorycode.com", "https://stage.qrapi.algorycode.com"]:
    print("\n====", host)
    for path in PATHS:
        url = host + path
        try:
            req = urllib.request.Request(url, headers={"Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=20) as resp:
                body = resp.read().decode("utf-8", "replace")
                print(resp.status, path, body[:180].replace("\n", " "))
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", "replace")
            print(exc.code, path, body[:180].replace("\n", " "))
        except Exception as exc:
            print("ERR", path, exc)
