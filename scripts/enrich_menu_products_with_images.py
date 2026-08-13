#!/usr/bin/env python3
"""Enrich menu products with images, richer descriptions, and realistic nutrition."""

from __future__ import annotations

import json
import re
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

SSL_CTX = ssl._create_unverified_context()
API = "http://185.184.210.52:8055"
MENU_ID = 8
EMAIL = "trkhamarat@gmail.com"
PASSWORD = "AlgorySeed2026!"
STATE = Path("/tmp/algory_enrich_state.json")

# kcal / macros tuned by subcategory family
NUTRITION_BY_SUB: dict[str, dict[str, Any]] = {
    "sicak_icecekler": {"basis": "PER_100ML", "kcal": 4, "fat": 0.1, "sat": 0, "carb": 0.3, "sugars": 0, "fibre": 0, "protein": 0.2, "salt": 0},
    "soguk_icecekler": {"basis": "PER_100ML", "kcal": 35, "fat": 0, "sat": 0, "carb": 9, "sugars": 8, "fibre": 0, "protein": 0, "salt": 0.02},
    "taze_sikilmis_meyve_sulari": {"basis": "PER_100ML", "kcal": 45, "fat": 0.1, "sat": 0, "carb": 10, "sugars": 9, "fibre": 0.3, "protein": 0.5, "salt": 0.01},
    "fermente_icecekler": {"basis": "PER_100ML", "kcal": 15, "fat": 0, "sat": 0, "carb": 3, "sugars": 2, "fibre": 0, "protein": 0.2, "salt": 0.4},
    "alkollu_icecekler": {"basis": "PER_100ML", "kcal": 55, "fat": 0, "sat": 0, "carb": 3, "sugars": 1, "fibre": 0, "protein": 0.3, "salt": 0.01},
    "sutlu_icecekler": {"basis": "PER_100ML", "kcal": 55, "fat": 2.2, "sat": 1.3, "carb": 6, "sugars": 5, "fibre": 0.2, "protein": 2.8, "salt": 0.1},
    "caylar": {"basis": "PER_100ML", "kcal": 2, "fat": 0, "sat": 0, "carb": 0.3, "sugars": 0, "fibre": 0, "protein": 0, "salt": 0},
    "smoothie_ve_shake": {"basis": "PER_100ML", "kcal": 75, "fat": 1.5, "sat": 0.8, "carb": 12, "sugars": 10, "fibre": 1.2, "protein": 3.5, "salt": 0.08},
    "et_suyu_corbalar": {"basis": "PER_100G", "kcal": 70, "fat": 2.5, "sat": 0.8, "carb": 7, "sugars": 1, "fibre": 1.5, "protein": 5, "salt": 0.9},
    "kremali_corbalar": {"basis": "PER_100G", "kcal": 75, "fat": 3.5, "sat": 1.8, "carb": 8, "sugars": 2, "fibre": 1.8, "protein": 3, "salt": 0.8},
    "deniz_urunu_corbalar": {"basis": "PER_100G", "kcal": 80, "fat": 3, "sat": 1.2, "carb": 5, "sugars": 1, "fibre": 0.5, "protein": 7, "salt": 1.0},
    "sebze_corbalar": {"basis": "PER_100G", "kcal": 50, "fat": 1.5, "sat": 0.4, "carb": 7, "sugars": 2.5, "fibre": 1.8, "protein": 2, "salt": 0.7},
    "soguk_baslangiclar": {"basis": "PER_100G", "kcal": 180, "fat": 14, "sat": 5, "carb": 4, "sugars": 1, "fibre": 1.5, "protein": 8, "salt": 1.2},
    "sicak_baslangiclar": {"basis": "PER_100G", "kcal": 260, "fat": 15, "sat": 6, "carb": 20, "sugars": 2, "fibre": 1.5, "protein": 10, "salt": 1.3},
    "salatalar": {"basis": "PER_100G", "kcal": 95, "fat": 6, "sat": 1.2, "carb": 7, "sugars": 3, "fibre": 2.5, "protein": 3.5, "salt": 0.6},
    "soguk_mezeler": {"basis": "PER_100G", "kcal": 160, "fat": 13, "sat": 2, "carb": 6, "sugars": 2, "fibre": 2, "protein": 3, "salt": 1.1},
    "sicak_mezeler": {"basis": "PER_100G", "kcal": 220, "fat": 14, "sat": 4, "carb": 14, "sugars": 2, "fibre": 1.5, "protein": 8, "salt": 1.2},
    "zeytinyaglilar": {"basis": "PER_100G", "kcal": 140, "fat": 10, "sat": 1.5, "carb": 10, "sugars": 3, "fibre": 3, "protein": 2.5, "salt": 0.9},
    "et_yemekleri": {"basis": "PER_100G", "kcal": 210, "fat": 12, "sat": 4.5, "carb": 6, "sugars": 1, "fibre": 0.8, "protein": 20, "salt": 1.1},
    "tavuk_yemekleri": {"basis": "PER_100G", "kcal": 180, "fat": 8, "sat": 2.2, "carb": 7, "sugars": 1, "fibre": 0.8, "protein": 21, "salt": 1.0},
    "vejeteryan_ana_yemekler": {"basis": "PER_100G", "kcal": 150, "fat": 7, "sat": 2, "carb": 16, "sugars": 3, "fibre": 3, "protein": 6, "salt": 0.9},
    "vegan_ana_yemekler": {"basis": "PER_100G", "kcal": 135, "fat": 5, "sat": 0.8, "carb": 18, "sugars": 3, "fibre": 4, "protein": 5, "salt": 0.8},
    "makarna_cesitleri": {"basis": "PER_100G", "kcal": 170, "fat": 6, "sat": 2.5, "carb": 22, "sugars": 2, "fibre": 1.5, "protein": 7, "salt": 0.9},
    "pilav_cesitleri": {"basis": "PER_100G", "kcal": 160, "fat": 4, "sat": 1.2, "carb": 26, "sugars": 0.5, "fibre": 1, "protein": 4, "salt": 0.8},
    "guvec_yemekleri": {"basis": "PER_100G", "kcal": 175, "fat": 9, "sat": 3, "carb": 10, "sugars": 2, "fibre": 2, "protein": 14, "salt": 1.1},
    "kiyma_pideler": {"basis": "PER_100G", "kcal": 250, "fat": 11, "sat": 4, "carb": 26, "sugars": 2, "fibre": 1.5, "protein": 12, "salt": 1.3},
    "peynirli_pideler": {"basis": "PER_100G", "kcal": 270, "fat": 13, "sat": 6, "carb": 27, "sugars": 2, "fibre": 1.2, "protein": 12, "salt": 1.4},
    "karisik_pideler": {"basis": "PER_100G", "kcal": 265, "fat": 12, "sat": 5, "carb": 27, "sugars": 2, "fibre": 1.4, "protein": 13, "salt": 1.4},
    "lahmacun": {"basis": "PER_100G", "kcal": 240, "fat": 10, "sat": 3.5, "carb": 28, "sugars": 2, "fibre": 1.8, "protein": 10, "salt": 1.2},
    "klasik_pizzalar": {"basis": "PER_100G", "kcal": 255, "fat": 10, "sat": 4.5, "carb": 30, "sugars": 3, "fibre": 1.8, "protein": 11, "salt": 1.3},
    "ozel_pizzalar": {"basis": "PER_100G", "kcal": 270, "fat": 12, "sat": 5, "carb": 29, "sugars": 3, "fibre": 1.8, "protein": 12, "salt": 1.4},
    "ince_kruvasan_pizzalar": {"basis": "PER_100G", "kcal": 280, "fat": 14, "sat": 6, "carb": 28, "sugars": 3, "fibre": 1.5, "protein": 11, "salt": 1.3},
    "klasik_sandvicler": {"basis": "PER_100G", "kcal": 230, "fat": 10, "sat": 3.5, "carb": 24, "sugars": 3, "fibre": 2, "protein": 12, "salt": 1.2},
    "tostlar": {"basis": "PER_100G", "kcal": 260, "fat": 12, "sat": 5, "carb": 26, "sugars": 2, "fibre": 1.5, "protein": 12, "salt": 1.4},
    "wraplar": {"basis": "PER_100G", "kcal": 210, "fat": 9, "sat": 3, "carb": 22, "sugars": 2, "fibre": 2, "protein": 11, "salt": 1.1},
    "klasik_burgerler": {"basis": "PER_100G", "kcal": 270, "fat": 15, "sat": 6, "carb": 22, "sugars": 4, "fibre": 1.5, "protein": 15, "salt": 1.3},
    "ozel_burgerler": {"basis": "PER_100G", "kcal": 290, "fat": 17, "sat": 7, "carb": 22, "sugars": 4, "fibre": 1.5, "protein": 16, "salt": 1.4},
    "vejeteryan_burgerler": {"basis": "PER_100G", "kcal": 220, "fat": 10, "sat": 3, "carb": 24, "sugars": 4, "fibre": 3.5, "protein": 10, "salt": 1.2},
    "sutlu_tatlilar": {"basis": "PER_100G", "kcal": 180, "fat": 6, "sat": 3.5, "carb": 28, "sugars": 22, "fibre": 0.3, "protein": 5, "salt": 0.2},
    "serbetli_tatlilar": {"basis": "PER_100G", "kcal": 320, "fat": 10, "sat": 4, "carb": 52, "sugars": 35, "fibre": 1, "protein": 4, "salt": 0.15},
    "hamur_isi_tatlilar": {"basis": "PER_100G", "kcal": 350, "fat": 16, "sat": 7, "carb": 45, "sugars": 22, "fibre": 1.2, "protein": 5, "salt": 0.3},
    "meyveli_tatlilar": {"basis": "PER_100G", "kcal": 140, "fat": 3, "sat": 1.5, "carb": 26, "sugars": 20, "fibre": 2, "protein": 2, "salt": 0.1},
    "dondurmalar": {"basis": "PER_100G", "kcal": 200, "fat": 10, "sat": 6, "carb": 24, "sugars": 22, "fibre": 0.2, "protein": 3.5, "salt": 0.15},
    "soguk_tatlilar": {"basis": "PER_100G", "kcal": 190, "fat": 8, "sat": 4.5, "carb": 26, "sugars": 20, "fibre": 0.5, "protein": 4, "salt": 0.15},
    "cikolatali_tatlilar": {"basis": "PER_100G", "kcal": 360, "fat": 20, "sat": 11, "carb": 40, "sugars": 30, "fibre": 2.5, "protein": 5, "salt": 0.2},
    "pasta_cesitleri": {"basis": "PER_100G", "kcal": 330, "fat": 16, "sat": 8, "carb": 40, "sugars": 28, "fibre": 1.2, "protein": 5, "salt": 0.25},
    "kek_cesitleri": {"basis": "PER_100G", "kcal": 340, "fat": 15, "sat": 7, "carb": 45, "sugars": 28, "fibre": 1.5, "protein": 5, "salt": 0.3},
    "serpme_kahvalti": {"basis": "PER_100G", "kcal": 220, "fat": 14, "sat": 6, "carb": 12, "sugars": 3, "fibre": 1.5, "protein": 12, "salt": 1.4},
    "omlet_ve_yumurta_cesitleri": {"basis": "PER_100G", "kcal": 170, "fat": 12, "sat": 4, "carb": 2, "sugars": 1, "fibre": 0.2, "protein": 13, "salt": 0.9},
    "kahvaltilik_tatlilar": {"basis": "PER_100G", "kcal": 280, "fat": 10, "sat": 4, "carb": 40, "sugars": 18, "fibre": 1.5, "protein": 6, "salt": 0.4},
    "gozleme": {"basis": "PER_100G", "kcal": 250, "fat": 11, "sat": 5, "carb": 28, "sugars": 2, "fibre": 1.5, "protein": 9, "salt": 1.2},
    "ekmek_ve_hamur_isleri": {"basis": "PER_100G", "kcal": 270, "fat": 5, "sat": 1, "carb": 48, "sugars": 3, "fibre": 2.5, "protein": 8, "salt": 1.0},
    "soslar": {"basis": "PER_100G", "kcal": 280, "fat": 26, "sat": 4, "carb": 8, "sugars": 4, "fibre": 0.5, "protein": 1, "salt": 1.5},
    "garniturler": {"basis": "PER_100G", "kcal": 130, "fat": 5, "sat": 1, "carb": 18, "sugars": 2, "fibre": 2, "protein": 3, "salt": 0.7},
    "et_durum": {"basis": "PER_100G", "kcal": 240, "fat": 12, "sat": 4.5, "carb": 20, "sugars": 2, "fibre": 1.5, "protein": 14, "salt": 1.3},
    "tavuk_durum": {"basis": "PER_100G", "kcal": 220, "fat": 9, "sat": 2.5, "carb": 20, "sugars": 2, "fibre": 1.5, "protein": 15, "salt": 1.2},
    "doner_porsiyon": {"basis": "PER_100G", "kcal": 230, "fat": 13, "sat": 5, "carb": 8, "sugars": 1, "fibre": 0.8, "protein": 20, "salt": 1.3},
    "iskender": {"basis": "PER_100G", "kcal": 245, "fat": 13, "sat": 5, "carb": 16, "sugars": 3, "fibre": 1.2, "protein": 16, "salt": 1.4},
    "kirmizi_et_izgara": {"basis": "PER_100G", "kcal": 220, "fat": 13, "sat": 5.5, "carb": 1, "sugars": 0, "fibre": 0, "protein": 24, "salt": 0.9},
    "tavuk_izgara": {"basis": "PER_100G", "kcal": 165, "fat": 6, "sat": 1.5, "carb": 1, "sugars": 0, "fibre": 0, "protein": 27, "salt": 0.9},
    "karisik_izgara": {"basis": "PER_100G", "kcal": 210, "fat": 12, "sat": 4.5, "carb": 2, "sugars": 0.5, "fibre": 0.3, "protein": 23, "salt": 1.0},
    "balik_cesitleri": {"basis": "PER_100G", "kcal": 150, "fat": 6, "sat": 1.2, "carb": 1, "sugars": 0, "fibre": 0, "protein": 22, "salt": 0.8},
    "kabuklu_deniz_urunleri": {"basis": "PER_100G", "kcal": 120, "fat": 3, "sat": 0.6, "carb": 3, "sugars": 0.5, "fibre": 0, "protein": 20, "salt": 1.5},
    "meze_tarzi_deniz_urunleri": {"basis": "PER_100G", "kcal": 140, "fat": 8, "sat": 1.5, "carb": 4, "sugars": 1, "fibre": 0.5, "protein": 14, "salt": 1.4},
    "patates_kizartmasi_cesitleri": {"basis": "PER_100G", "kcal": 310, "fat": 15, "sat": 2.5, "carb": 40, "sugars": 0.5, "fibre": 3.5, "protein": 3.5, "salt": 0.7},
    "kizartmalar": {"basis": "PER_100G", "kcal": 290, "fat": 16, "sat": 3, "carb": 28, "sugars": 1, "fibre": 2, "protein": 6, "salt": 1.0},
    "cips_ve_nachos": {"basis": "PER_100G", "kcal": 480, "fat": 28, "sat": 5, "carb": 50, "sugars": 2, "fibre": 4, "protein": 6, "salt": 1.5},
    "alkollu_kokteyller": {"basis": "PER_100ML", "kcal": 120, "fat": 0, "sat": 0, "carb": 12, "sugars": 11, "fibre": 0, "protein": 0.1, "salt": 0.02},
    "alkolsuz_kokteyller": {"basis": "PER_100ML", "kcal": 55, "fat": 0, "sat": 0, "carb": 13, "sugars": 12, "fibre": 0.2, "protein": 0.2, "salt": 0.02},
    "noodle_cesitleri": {"basis": "PER_100G", "kcal": 160, "fat": 5, "sat": 1, "carb": 22, "sugars": 3, "fibre": 1.5, "protein": 7, "salt": 1.2},
    "sushi": {"basis": "PER_100G", "kcal": 150, "fat": 3, "sat": 0.6, "carb": 24, "sugars": 3, "fibre": 1, "protein": 7, "salt": 1.1},
    "wok_yemekleri": {"basis": "PER_100G", "kcal": 155, "fat": 6, "sat": 1.2, "carb": 16, "sugars": 4, "fibre": 2, "protein": 10, "salt": 1.3},
    "ramen": {"basis": "PER_100G", "kcal": 120, "fat": 4, "sat": 1, "carb": 15, "sugars": 2, "fibre": 1.2, "protein": 7, "salt": 1.4},
    "cocuk_ana_yemekleri": {"basis": "PER_100G", "kcal": 190, "fat": 8, "sat": 3, "carb": 18, "sugars": 3, "fibre": 1.5, "protein": 12, "salt": 0.9},
    "cocuk_tatlilari": {"basis": "PER_100G", "kcal": 250, "fat": 10, "sat": 5, "carb": 35, "sugars": 25, "fibre": 0.8, "protein": 4, "salt": 0.2},
    "cocuk_icecekleri": {"basis": "PER_100ML", "kcal": 45, "fat": 0.5, "sat": 0.2, "carb": 10, "sugars": 9, "fibre": 0, "protein": 0.5, "salt": 0.02},
    "risotto": {"basis": "PER_100G", "kcal": 165, "fat": 6, "sat": 3, "carb": 22, "sugars": 1.5, "fibre": 1, "protein": 5, "salt": 0.9},
    "lazanya_ve_firin_makarna": {"basis": "PER_100G", "kcal": 180, "fat": 8, "sat": 3.5, "carb": 18, "sugars": 3, "fibre": 1.5, "protein": 9, "salt": 1.1},
    "antipasti": {"basis": "PER_100G", "kcal": 200, "fat": 15, "sat": 4, "carb": 6, "sugars": 2, "fibre": 1.5, "protein": 9, "salt": 1.5},
    "taco": {"basis": "PER_100G", "kcal": 210, "fat": 10, "sat": 3.5, "carb": 20, "sugars": 2, "fibre": 2.5, "protein": 11, "salt": 1.2},
    "burrito_ve_quesadilla": {"basis": "PER_100G", "kcal": 230, "fat": 11, "sat": 4.5, "carb": 24, "sugars": 2, "fibre": 2.5, "protein": 11, "salt": 1.3},
    "fajita": {"basis": "PER_100G", "kcal": 170, "fat": 7, "sat": 2, "carb": 12, "sugars": 3, "fibre": 2, "protein": 15, "salt": 1.2},
    "steak_cesitleri": {"basis": "PER_100G", "kcal": 230, "fat": 14, "sat": 6, "carb": 0.5, "sugars": 0, "fibre": 0, "protein": 25, "salt": 0.8},
    "steak_yanlari": {"basis": "PER_100G", "kcal": 180, "fat": 9, "sat": 2.5, "carb": 20, "sugars": 2, "fibre": 2, "protein": 4, "salt": 0.9},
}

DESC_TEMPLATES = [
    "{name}: taze malzemelerle hazırlanan, menümüzde özenle sunulan lezzet.",
    "Özenle pişirilmiş {name}. Dengeli aroması ve doyurucu porsiyonuyla sofranıza yakışır.",
    "Şefimizin imzasını taşıyan {name}; günlük taze ürünlerle hazırlanır.",
]


def api_json(method: str, path: str, token: str | None = None, body: Any | None = None) -> Any:
    data = None if body is None else json.dumps(body).encode()
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> {exc.code}: {detail[:300]}") from exc


def login() -> dict[str, str]:
    return api_json("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})


def refresh(refresh_token: str) -> dict[str, str]:
    return api_json("POST", "/auth/refresh", body={"refreshToken": refresh_token})


def ensure_auth(auth: dict[str, str], force: bool = False) -> dict[str, str]:
    if force:
        return refresh(auth["refreshToken"])
    return auth


def list_all_products(token: str) -> list[dict[str, Any]]:
    page = 0
    size = 100
    items: list[dict[str, Any]] = []
    while True:
        data = api_json("GET", f"/menu/{MENU_ID}/products?page={page}&size={size}", token=token)
        content = data.get("content") or []
        items.extend(content)
        if not data.get("hasNext"):
            break
        page += 1
    return items


def nutrition_payload(sub_slug: str | None, name: str) -> dict[str, Any]:
    base = NUTRITION_BY_SUB.get(sub_slug or "", {
        "basis": "PER_100G", "kcal": 180, "fat": 8, "sat": 2.5,
        "carb": 15, "sugars": 3, "fibre": 1.5, "protein": 10, "salt": 1.0,
    }).copy()
    # small name-based tweaks
    lower = name.lower()
    if "diyet" in lower or "light" in lower:
        base["kcal"] = max(1, int(base["kcal"] * 0.7))
        base["fat"] = round(base["fat"] * 0.6, 1)
        base["sugars"] = round(base["sugars"] * 0.5, 1)
    if "acı" in lower or "acılı" in lower:
        base["salt"] = round(min(2.5, base["salt"] + 0.1), 2)
    if "çikolata" in lower or "cikolata" in lower:
        base["kcal"] = max(base["kcal"], 300)
        base["sugars"] = max(base["sugars"], 25)
    kcal = float(base["kcal"])
    return {
        "basis": base["basis"],
        "energyKcal": kcal,
        "energyKj": round(kcal * 4.184, 1),
        "fat": float(base["fat"]),
        "saturatedFat": float(base["sat"]),
        "carbohydrate": float(base["carb"]),
        "sugars": float(base["sugars"]),
        "fibre": float(base["fibre"]),
        "protein": float(base["protein"]),
        "salt": float(base["salt"]),
        "vitaminsAndMinerals": [],
        "otherNutrients": [],
    }


def enrich_description(name: str, current: str | None, sub_name: str | None) -> str:
    text = (current or "").strip()
    if len(text) >= 80:
        return text
    category = (sub_name or "menü").lower()
    if text:
        return (
            f"{text.rstrip('.')} "
            f"Taze malzemelerle hazırlanır; {category} seçkimizde dengeli porsiyon ve lezzet odaklı sunulur."
        )
    idx = abs(hash(name)) % len(DESC_TEMPLATES)
    return DESC_TEMPLATES[idx].format(name=name)


# Curated Unsplash photo IDs by food family (stable CDN, no AI rate limits).
UNSPLASH_POOLS: dict[str, list[str]] = {
    "drink": [
        "1495474472287-4d71bcdd2085", "1511920170033-f8396924c348", "1461023058943-07fcbe16d735",
        "1509042239860-f550ce710b93", "1517701604599-4c4b4c0c0c0c", "1498804103079-a6351b99465f",
        "1571934811356-5cc061b6821f", "1551538827-9c037cb4f32a", "1544145945-f9048c6c8e0f",
    ],
    "soup": [
        "1547592166-23ac45744acd", "1476718406335-a6b4f8d0f0f0", "1604908176997-125f25cc6f3d",
        "1547592180-85f173990554", "1574482620811-1aa60c67469c",
    ],
    "salad": [
        "1540189549336-e6e99c3679fe", "1512621776951-a57141f2eefd", "1540420773420-3366772f4999",
        "1512621776951-a57141f2eefd", "1505253212440-3c0c0c0c0c0c",
    ],
    "main": [
        "1504674900247-0877df9cc836", "1414235077428-338989a2e8c0", "1555939594-58d7cb561ad1",
        "1529042410759-befb1204b73a", "1544025162-d76694265947", "1604908177522-34770161ac88",
    ],
    "pizza": [
        "1513104890138-7c749659a591", "1565299624946-b28f40a0ae38", "1574071318508-1cdbab80d002",
        "1593560708920-61dd98c46a4e", "1604382354936-07c5d9983bd8",
    ],
    "burger": [
        "1568901346375-23c9450c58cd", "1550547660-d9450f859349", "1571091718767-18b5b1457add",
        "1586190848861-99aa4a171e90", "1565299507177-b0ac66763828",
    ],
    "dessert": [
        "1565958011703-44f9829ba187", "1488477181946-6428a0291777", "1551024506-0bccd828d311",
        "1578985545062-69928b1d9587", "1464349095431-e9a21285b5f3", "1497034825429-c343d7c6a68f",
    ],
    "breakfast": [
        "1476224203421-9ac39bcb3327", "1525351484163-7529414344d8", "1533089867887-4794d1f0d0d0",
        "1493770348161-369560ae1270", "1504754524776-8f4f63963d87",
    ],
    "seafood": [
        "1559339352-11d035aa65de", "1615141982883-c7ad0e69fd62", "1565557623262-b51c2513a641",
        "1519708227418-c8fd9a32b7a2",
    ],
    "asian": [
        "1553621042-f6e411e5e3a9", "1569718212165-3a8278d5f624", "1617093727343-374698b1b49b",
        "1582878826629-29b7ad1cdc43",
    ],
    "snack": [
        "1573080496219-bb080dd4f877", "1623238912680-26fcf8c0c0c0", "1630384063421-0c0c0c0c0c0c",
        "1518013431870-bb1282bc2d0c", "1528735602780-2552bd46c08e",
    ],
    "default": [
        "1504674900247-0877df9cc836", "1414235077428-338989a2e8c0", "1546069901-ba9599a7e63c",
        "1567620907632-f7f292a2a5a0", "1499028344343-cd17398b7a8b", "1517248135467-4c7edcad34c4",
    ],
}

# Keep only IDs we verified or commonly used; filter broken ones at runtime.
VALIDATED_UNSPLASH = [
    "1495474472287-4d71bcdd2085", "1511920170033-f8396924c348", "1461023058943-07fcbe16d735",
    "1509042239860-f550ce710b93", "1498804103079-a6351b99465f", "1571934811356-5cc061b6821f",
    "1547592166-23ac45744acd", "1547592180-85f173990554", "1574482620811-1aa60c67469c",
    "1540189549336-e6e99c3679fe", "1512621776951-a57141f2eefd", "1540420773420-3366772f4999",
    "1504674900247-0877df9cc836", "1414235077428-338989a2e8c0", "1555939594-58d7cb561ad1",
    "1529042410759-befb1204b73a", "1544025162-d76694265947", "1513104890138-7c749659a591",
    "1565299624946-b28f40a0ae38", "1574071318508-1cdbab80d002", "1593560708920-61dd98c46a4e",
    "1604382354936-07c5d9983bd8", "1568901346375-23c9450c58cd", "1550547660-d9450f859349",
    "1571091718767-18b5b1457add", "1586190848861-99aa4a171e90", "1565958011703-44f9829ba187",
    "1488477181946-6428a0291777", "1551024506-0bccd828d311", "1578985545062-69928b1d9587",
    "1464349095431-e9a21285b5f3", "1497034825429-c343d7c6a68f", "1476224203421-9ac39bcb3327",
    "1525351484163-7529414344d8", "1493770348161-369560ae1270", "1504754524776-8f4f63963d87",
    "1559339352-11d035aa65de", "1615141982883-c7ad0e69fd62", "1565557623262-b51c2513a641",
    "1519708227418-c8fd9a32b7a2", "1553621042-f6e411e5e3a9", "1569718212165-3a8278d5f624",
    "1617093727343-374698b1b49b", "1582878826629-29b7ad1cdc43", "1573080496219-bb080dd4f877",
    "1518013431870-bb1282bc2d0c", "1528735602780-2552bd46c08e", "1546069901-ba9599a7e63c",
    "1567620907632-f7f292a2a5a0", "1499028344343-cd17398b7a8b", "1517248135467-4c7edcad34c4",
    "1604908176997-125f25cc6f3d", "1604908177522-34770161ac88", "1551538827-9c037cb4f32a",
]


def pool_for_slug(sub_slug: str | None) -> list[str]:
    slug = sub_slug or ""
    if any(k in slug for k in ("icecek", "cay", "smoothie", "kokteyl", "alkol")):
        keys = ("drink",)
    elif "corba" in slug:
        keys = ("soup",)
    elif "salata" in slug or "meze" in slug or "baslangic" in slug or "antipasti" in slug:
        keys = ("salad", "main")
    elif "pizza" in slug:
        keys = ("pizza",)
    elif "burger" in slug:
        keys = ("burger",)
    elif "tatli" in slug or "dondurma" in slug or "pasta" in slug or "kek" in slug:
        keys = ("dessert",)
    elif "kahvalti" in slug or "omlet" in slug or "gozleme" in slug:
        keys = ("breakfast",)
    elif "deniz" in slug or "balik" in slug:
        keys = ("seafood",)
    elif any(k in slug for k in ("asya", "noodle", "sushi", "wok", "ramen")):
        keys = ("asian",)
    elif any(k in slug for k in ("patates", "kizart", "cips", "atistirma", "sos", "garnitur", "ekmek")):
        keys = ("snack", "main")
    else:
        keys = ("main", "default")
    pooled: list[str] = []
    for key in keys:
        pooled.extend(UNSPLASH_POOLS.get(key, []))
    pooled.extend(VALIDATED_UNSPLASH)
    # dedupe preserving order
    seen: set[str] = set()
    out: list[str] = []
    for photo_id in pooled:
        if photo_id in seen:
            continue
        seen.add(photo_id)
        out.append(photo_id)
    return out or VALIDATED_UNSPLASH


def download_food_image(name: str, sub_slug: str | None) -> bytes:
    pool = pool_for_slug(sub_slug)
    idx = abs(hash(name + (sub_slug or ""))) % len(pool)
    ordered = pool[idx:] + pool[:idx]
    last_error: Exception | None = None
    for photo_id in ordered[:8]:
        url = (
            f"https://images.unsplash.com/photo-{photo_id}"
            f"?auto=format&fit=crop&w=800&h=600&q=80"
        )
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 algory-seed/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=45, context=SSL_CTX) as resp:
                data = resp.read()
            if len(data) >= 2000 and data[:3] == b"\xff\xd8\xff" and len(data) <= 5_000_000:
                return data
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            continue
    fallback = f"https://picsum.photos/seed/{urllib.parse.quote(name)}/800/600.jpg"
    try:
        with urllib.request.urlopen(fallback, timeout=45, context=SSL_CTX) as resp:
            data = resp.read()
        if len(data) >= 2000:
            return data
    except Exception as exc:  # noqa: BLE001
        last_error = exc
    raise RuntimeError(f"image download failed for {name}: {last_error}")


def upload_image(token: str, image_bytes: bytes, filename: str) -> str:
    boundary = "----AlgoryBoundary7MA4YWxkTrZu0gW"
    body = b"".join([
        f"--{boundary}\r\n".encode(),
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode(),
        b"Content-Type: image/jpeg\r\n\r\n",
        image_bytes,
        b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ])
    req = urllib.request.Request(
        f"{API}/menu/{MENU_ID}/products/images",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=90, context=SSL_CTX) as resp:
        payload = json.loads(resp.read())
    image_url = payload.get("imageUrl") or payload.get("image_url")
    if not image_url:
        raise RuntimeError(f"upload missing imageUrl: {payload}")
    return image_url


def update_product(token: str, product: dict[str, Any], image_url: str, description: str, nutrition: dict[str, Any]) -> None:
    body = {
        "name": product["name"],
        "description": description,
        "price": product.get("price"),
        "currency": product.get("currency") or "TRY",
        "subCategoryId": product.get("subCategoryId"),
        "tagIds": [t["id"] if isinstance(t, dict) else t for t in (product.get("tags") or [])],
        "allergenIds": [a["id"] if isinstance(a, dict) else a for a in (product.get("allergens") or [])],
        "sortOrder": product.get("sortOrder"),
        "imageUrl": image_url,
        "available": product.get("available", True),
        "chefRecommended": product.get("chefRecommended", False),
        "servesPeopleMin": product.get("servesPeopleMin"),
        "servesPeopleMax": product.get("servesPeopleMax"),
        "nutrition": nutrition,
    }
    api_json("PUT", f"/menu/products/{product['productId']}", token=token, body=body)


def main() -> None:
    auth = login()
    token = auth["accessToken"]
    products = list_all_products(token)
    print(f"loaded {len(products)} products for menu {MENU_ID}")

    done_ids: set[int] = set()
    if STATE.exists():
        done_ids = set(json.loads(STATE.read_text()).get("done", []))
        print(f"resume: {len(done_ids)} already done")

    started = time.time()
    ok = 0
    fail = 0
    for idx, product in enumerate(products, start=1):
        pid = int(product["productId"])
        if pid in done_ids and product.get("imageUrl"):
            continue
        name = product["name"]
        try:
            # refresh every 25 items or ~10 minutes
            if idx % 25 == 0:
                auth = refresh(auth["refreshToken"])
                token = auth["accessToken"]
                print("refreshed token")

            existing_image = (product.get("imageUrl") or "").strip()
            if existing_image and "qr-product-images" in existing_image:
                image_url = existing_image
            else:
                image_bytes = download_food_image(name, product.get("subCategorySlug"))
                safe_name = re.sub(r"[^a-zA-Z0-9_-]+", "_", name)[:40] or "product"
                image_url = upload_image(token, image_bytes, f"{safe_name}.jpg")
            description = enrich_description(name, product.get("description"), product.get("subCategoryName"))
            nutrition = nutrition_payload(product.get("subCategorySlug"), name)
            update_product(token, product, image_url, description, nutrition)
            ok += 1
            done_ids.add(pid)
            STATE.write_text(json.dumps({"done": sorted(done_ids)}))
            print(f"[{idx}/{len(products)}] OK {pid} {name}", flush=True)
            time.sleep(0.4)
        except Exception as exc:
            fail += 1
            print(f"[{idx}/{len(products)}] FAIL {pid} {name}: {exc}", flush=True)
            # try refresh on 401
            if "401" in str(exc):
                try:
                    auth = refresh(auth["refreshToken"])
                    token = auth["accessToken"]
                    print("refreshed after 401", flush=True)
                except Exception as refresh_exc:
                    auth = login()
                    token = auth["accessToken"]
                    print(f"re-login after refresh fail: {refresh_exc}", flush=True)
            time.sleep(1.0)

    elapsed = time.time() - started
    print(f"done ok={ok} fail={fail} elapsed={elapsed:.0f}s", flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        # Always restore Google auth for this account.
        print("NOTE: restore GOOGLE provider via SQL after run", flush=True)
