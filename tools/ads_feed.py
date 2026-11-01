#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ابزار مدیر فید تبلیغات بادخزان — نسخهٔ ۱.۱۱.۳۵ (کار ۱)

سه کار را با هم انجام می‌دهد:
  ۱) اعتبارسنجی feed/main_ads.json (schema، https بودن همهٔ URLها، تاریخ‌ها،
     کلیدهای لیست‌سفید داخلی، سقف متن‌ها)
  ۲) افزایش یکی sequence (ضد بازپخش — اپ فید با sequence کوچک‌تر/مساوی را رد می‌کند)
  ۳) امضای Ed25519 فایل (فایل main_ads.json.sig کنار آن بازنویسی می‌شود)

استفاده:
  # کلید خصوصی فقط نزد مدیر — هرگز در مخزن نیست!
  export ADS_FEED_KEY="$(cat مسیر/کلید-خصوصی.b64)"     # Base64 از ۳۲ بایت seed

  python3 tools/ads_feed.py            # اعتبارسنجی + sequence+1 + امضا
  python3 tools/ads_feed.py --validate # فقط اعتبارسنجی (بدون تغییر فایل)
  python3 tools/ads_feed.py --no-bump  # اعتبارسنجی + امضا، بدون تغییر sequence

بعد از اجرا، فایل‌ها را commit و push کنید؛ اپ‌ها در همگام‌سازی بعدی می‌گیرند.
(امضای خودکار CI هم هست — ولی این اسکریپت برای انتشار مستقیم از رایانه است.)
"""
import argparse
import base64
import json
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
except ImportError:
    print("خطا: کتابخانهٔ cryptography نصب نیست — اجرا کنید: pip install cryptography")
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parent.parent
FEED_FILE = REPO_ROOT / "feed" / "main_ads.json"

# ---------- سقف‌ها — باید با AdsFeedPolicy.kt یکی باشد ----------
MAX_ITEMS = 12
MAX_ID = 64
MAX_TITLE = 40
MAX_BADGE = 12
MAX_DESC = 80
MAX_URL = 2048
MAX_CACHE_DAYS = 30
INTERNAL_TARGETS = {
    "calendar", "prayer", "qibla", "events", "reminders",
    "announcements", "support", "account",
}
TYPES = {"tile", "banner"}

errors: list = []
warnings: list = []


def err(msg: str):
    errors.append(msg)


def warn(msg: str):
    warnings.append(msg)


def check_https(url: str, field: str, idx: int) -> bool:
    if not url or not isinstance(url, str):
        err(f"آیتم {idx}: «{field}» خالی است (برای tile باید iconUrl و برای banner باید imageUrl باشد)")
        return False
    if len(url) > MAX_URL:
        err(f"آیتم {idx}: «{field}» بلندتر از {MAX_URL} کاراکتر است")
        return False
    low = url.lower()
    if not low.startswith("https://"):
        err(f"آیتم {idx}: «{field}» باید با https:// شروع شود (http/intent/file/javascript/data مجاز نیست)")
        return False
    if any(c.isspace() for c in url):
        err(f"آیتم {idx}: «{field}» فاصله دارد")
        return False
    host = url[8:].split("/")[0].split(":")[0].split("@")[0]
    if not host or ("." not in host and host != "localhost"):
        err(f"آیتم {idx}: «{field}» host نامعتبر دارد")
        return False
    return True


def parse_when(raw, field: str, idx: int) -> int:
    if raw is None:
        return 0
    if isinstance(raw, (int, float)):
        return int(raw)
    if isinstance(raw, str) and raw.strip():
        s = raw.strip()
        if s.isdigit():
            return int(s)
        try:
            d = datetime.fromisoformat(s.replace("Z", "+00:00"))
            if d.tzinfo is None:
                # مثل اپ: تاریخ بدون منطقهٔ زمانی به Asia/Tehran تفسیر می‌شود
                warn(f"آیتم {idx}: «{field}» بدون منطقهٔ زمانی — به Asia/Tehran تفسیر می‌شود")
                d = d.replace(tzinfo=timezone(timedelta(hours=3, minutes=30)))
            return int(d.timestamp() * 1000)
        except ValueError:
            err(f"آیتم {idx}: «{field}» نه عدد epoch است و نه ISO-8601 معتبر («{s}»)")
            return -1
    err(f"آیتم {idx}: «{field}» نامعتبر است")
    return -1


def validate(doc: dict) -> list:
    """اعتبارسنجی کامل — خروجی: فهرست آیتم‌های معتبر (برای bump نشدن sequence خراب)"""
    if not isinstance(doc, dict):
        err("فایل باید یک شیء JSON باشد (نه آرایهٔ قدیمی)")
        return []
    seq = doc.get("sequence")
    if not isinstance(seq, int) or seq < 1:
        err(f"«sequence» باید عدد صحیح ≥ ۱ باشد (فعلی: {seq!r})")
    issued = doc.get("issuedAt")
    if not isinstance(issued, str) or not issued.strip():
        warn("«issuedAt» خالی است — گارد نصب تازهٔ اپ فید را رد می‌کند؛ تاریخ ISO بگذارید")
    else:
        parse_when(issued, "issuedAt", 0)
    if not isinstance(doc.get("adsEnabled", True), bool):
        err("«adsEnabled» باید true/false باشد")
    mcd = doc.get("maxCacheDays", 7)
    if not isinstance(mcd, int) or mcd < 0 or mcd > MAX_CACHE_DAYS:
        err(f"«maxCacheDays» باید عددی بین ۰ و {MAX_CACHE_DAYS} باشد")
    items = doc.get("items")
    if not isinstance(items, list):
        err("«items» باید آرایه باشد")
        return []
    if len(items) > MAX_ITEMS:
        err(f"تعداد آیتم‌ها {len(items)} است — سقف {MAX_ITEMS}")
    seen_ids = set()
    valid = []
    for i, it in enumerate(items):
        idx = i + 1
        if not isinstance(it, dict):
            err(f"آیتم {idx}: باید شیء باشد")
            continue
        iid = str(it.get("id", "")).strip()
        if not iid or len(iid) > MAX_ID:
            err(f"آیتم {idx}: «id» خالی یا بلندتر از {MAX_ID} است")
        elif iid in seen_ids:
            err(f"آیتم {idx}: «id» تکراری است ({iid})")
        else:
            seen_ids.add(iid)
        typ = it.get("type")
        if typ not in TYPES:
            err(f"آیتم {idx} ({iid}): «type» باید tile یا banner باشد ({typ!r})")
            continue
        title = str(it.get("title", "")).strip()
        if not title:
            err(f"آیتم {idx} ({iid}): «title» خالی است")
        elif len(title) > MAX_TITLE:
            err(f"آیتم {idx} ({iid}): «title» بلندتر از {MAX_TITLE} کاراکتر است")
        if len(str(it.get("badge", ""))) > MAX_BADGE:
            err(f"آیتم {idx} ({iid}): «badge» بلندتر از {MAX_BADGE} کاراکتر است")
        if len(str(it.get("description", ""))) > MAX_DESC:
            err(f"آیتم {idx} ({iid}): «description» بلندتر از {MAX_DESC} کاراکتر است")
        if typ == "tile":
            check_https(it.get("iconUrl"), "iconUrl", idx)
        else:
            check_https(it.get("imageUrl"), "imageUrl", idx)
        tt = str(it.get("targetType", "")).strip().lower()
        target = str(it.get("target", "")).strip()
        if tt == "internal":
            if target not in INTERNAL_TARGETS:
                err(f"آیتم {idx} ({iid}): مقصد داخلی «{target}» در لیست‌سفید نیست — مجازها: {sorted(INTERNAL_TARGETS)}")
        else:
            if target:
                check_https(target, "target (external)", idx)
        start = parse_when(it.get("startAt"), "startAt", idx)
        end = parse_when(it.get("endAt"), "endAt", idx)
        if start > 0 and end > 0 and end <= start:
            err(f"آیتم {idx} ({iid}): endAt باید بعد از startAt باشد")
        mvc = it.get("minVersionCode", 0)
        if not isinstance(mvc, int) or mvc < 0:
            err(f"آیتم {idx} ({iid}): «minVersionCode» باید عدد صحیح ≥ ۰ باشد")
        valid.append(it)
    return valid


def bump_sequence(doc: dict) -> dict:
    old = doc.get("sequence", 0)
    if not isinstance(old, int) or old < 1:
        print(f"⚠ sequence نامعتبر ({old!r}) — از ۱ شروع می‌شود")
        old = 0
    doc["sequence"] = old + 1
    doc["issuedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"sequence: {old} → {doc['sequence']}  (issuedAt: {doc['issuedAt']})")
    return doc


def sign_file(path: Path, key_b64: str) -> None:
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
    seed = base64.b64decode(key_b64)
    if len(seed) != 32:
        raise SystemExit(f"خطا: کلید خصوصی باید ۳۲ بایت باشد (فعلی {len(seed)})")
    sk = Ed25519PrivateKey.from_private_bytes(seed)
    data = path.read_bytes()
    sig = sk.sign(data)
    path.with_suffix(path.suffix + ".sig").write_bytes(base64.b64encode(sig) + b"\n")
    # تأیید خودکار همان امضا
    pub = sk.public_key().public_bytes(encoding=Encoding.Raw, format=PublicFormat.Raw)
    Ed25519PublicKey.from_public_bytes(pub).verify(
        base64.b64decode(path.with_suffix(path.suffix + ".sig").read_bytes()), data
    )
    print(f"امضا شد و تأیید شد: {path.name} → {path.name}.sig ({len(data)} بایت)")


def main() -> int:
    ap = argparse.ArgumentParser(description="اعتبارسنجی/امضای فید تبلیغات بادخزان")
    ap.add_argument("--validate", action="store_true", help="فقط اعتبارسنجی")
    ap.add_argument("--no-bump", action="store_true", help="بدون افزایش sequence")
    args = ap.parse_args()

    if not FEED_FILE.exists():
        print(f"خطا: {FEED_FILE} پیدا نشد")
        return 1
    try:
        doc = json.loads(FEED_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"خطا: JSON نامعتبر است: {e}")
        return 1

    validate(doc)
    if warnings:
        print("— هشدارها —")
        for w in warnings:
            print(f"  ⚠ {w}")
    if errors:
        print("— خطاها (فایل منتشر نشود) —")
        for e in errors:
            print(f"  ✗ {e}")
        return 1
    n = len(doc.get("items", []))
    print(f"اعتبارسنجی موفق: sequence={doc.get('sequence')} · {n} آیتم · adsEnabled={doc.get('adsEnabled', True)}")

    if args.validate:
        return 0

    doc = bump_sequence(doc)
    FEED_FILE.write_text(
        json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    key = os.environ.get("ADS_FEED_KEY", "").strip()
    if not key:
        print("خطا: متغیر محیطی ADS_FEED_KEY تنظیم نشده — کلید خصوصی (Base64 ۳۲ بایتی) را در آن بگذارید؛")
        print("کلید خصوصی هرگز در مخزن نیست (نگاه کنید به tools/README-FA.md)")
        return 1
    sign_file(FEED_FILE, key)
    print("تمام شد — حالا commit و push کنید تا به کاربران برسد.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
