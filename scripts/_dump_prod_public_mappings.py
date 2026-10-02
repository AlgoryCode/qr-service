import json
import urllib.request

url = "https://prod.qrapi.algorycode.com/actuator/mappings"
with urllib.request.urlopen(url, timeout=30) as resp:
    data = json.load(resp)

mappings = data["contexts"]["algoryqr-service"]["mappings"]["dispatcherServlets"]["dispatcherServlet"]
hits = []
for m in mappings:
    details = m.get("details") or {}
    handler = str((details.get("handlerMethod") or details.get("handler") or ""))
    pred = details.get("requestMappingConditions") or {}
    patterns = pred.get("patterns") or m.get("predicate") or ""
    text = f"{patterns} :: {handler}"
    if "menu" in text.lower() and "public" in text.lower():
        hits.append(text)

for h in sorted(set(hits)):
    print(h)
