#!/usr/bin/env python3
"""Assign ULTIMATE_PACKAGE on algoryqrdb (see ensure_merchant_active_package.py)."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ensure_merchant_active_package import ensure

TARGETS = [
    "a11_fen@hotmail.com",
    "tavsancoffe@gmail.com",
    "trkhamarat@gmail.com",
]

if __name__ == "__main__":
    for email in TARGETS:
        print(ensure(email, check_only=False))
