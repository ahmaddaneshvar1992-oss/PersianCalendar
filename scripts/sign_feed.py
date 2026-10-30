#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
امضای خوراک‌های بادخزان با Ed25519 — نسخهٔ ۱.۱۱.۳۰

هر feed/*.json باید فایل امضای هم‌نامِ .sig داشته باشد وگرنه اپ‌های ۱.۱۱.۳۰ به بعد
آن منبع را «ناسالم» می‌دانند. این اسکریپت همهٔ فایل‌های feed/*.json را با کلید
خصوصی امضا می‌کند (فایل امضای قبلی بازنویسی می‌شود).

استفاده:
  ۱) از رایانه (کلید خصوصی نزد شماست):
       FEED_SIGNING_KEY="$(cat feed-ed25519-private.b64)" python3 scripts/sign_feed.py
  ۲) روی گیت‌هاب: وورکفلوی «Sign feed files» خودش با Secret مخزن همین اسکریپت
     را اجرا می‌کند — نیازی به کار دستی نیست.

فرمت کلید: Base64 از ۳۲ بایت seed کلید خصوصی Ed25519.
امضا: Base64 از ۶۴ بایت امضای Ed25519 روی بایت‌های دقیق فایل جیسون.
"""
import base64
import os
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
except ImportError:
    print("خطا: کتابخانهٔ cryptography نصب نیست — اجرا کنید: pip install cryptography")
    sys.exit(1)

FEED_DIR = Path(__file__).resolve().parent.parent / "feed"


def main() -> int:
    key_b64 = os.environ.get("FEED_SIGNING_KEY", "").strip()
    if not key_b64:
        print("خطا: متغیر محیطی FEED_SIGNING_KEY تنظیم نشده است.")
        print("کلید خصوصی (Base64 سی‌ودو بایتی) را در این متغیر بگذارید — هرگز در مخزن نباشد.")
        return 1
    try:
        seed = base64.b64decode(key_b64)
        if len(seed) != 32:
            raise ValueError(f"طول کلید {len(seed)} بایت است — باید ۳۲ بایت باشد")
        sk = Ed25519PrivateKey.from_private_bytes(seed)
    except Exception as e:
        print(f"خطا: کلید خصوصی نامعتبر است: {e}")
        return 1

    files = sorted(FEED_DIR.glob("*.json"))
    if not files:
        print(f"خطا: هیچ فایلی در {FEED_DIR} پیدا نشد")
        return 1

    failed = False
    for f in files:
        data = f.read_bytes()
        sig = sk.sign(data)
        (f.with_suffix(f.suffix + ".sig")).write_bytes(base64.b64encode(sig) + b"\n")
        print(f"امضا شد: {f.name} → {f.name}.sig ({len(data)} بایت محتوا)")
        # تأیید خودکار همان امضا
        try:
            sk.public_key().verify(sig, data)
        except Exception:
            print(f"  ✗ تأیید داخلی ناموفق برای {f.name}")
            failed = True
    if failed:
        return 1
    print("همهٔ امضاها ساخته و تأیید شدند ✓")
    return 0


if __name__ == "__main__":
    sys.exit(main())
