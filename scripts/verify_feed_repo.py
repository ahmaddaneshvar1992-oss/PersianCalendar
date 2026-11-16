#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
راستی‌آزمایی محلی مخزن خوراک PersianCalendar-feed — از ۱.۱۱.۴۶
(قبل از push و هر بازبینی امنیتی اجرا شود)

چک‌ها:
  ۱) ساختار: هر feed/*.json یک .sig هم‌نام دارد؛ فایل اضافه/گمشده نیست
  ۲) امضا: هر .sig دقیقاً ۶۴ بایت (Ed25519) و با یکی از دو کلید عمومی داخل اپ
     معتبر است (اگر کتابخانهٔ cryptography نصب باشد — وگرنه فقط طول چک می‌شود
     و یادآوری چاپ می‌شود)
  ۳) وورکفلو: PINNED_SHA و دو هشِ PINNED_SHA256_* دارد؛ permissions=contents:write
     دارد؛ کد امضا را با SHA ثابت می‌گیرد (نه از main)؛ «python scripts/» مخزن
     اصلی را checkout نمی‌کند
  ۴) بدون کلید خصوصی: هیچ فایلی از مخزن Secret کلید را حمل نمی‌کند (هیچ بلاک
     base64 ۴۴ کاراکتریِ خارج از .sig‌ها)
  ۵) README راهنمای مدل تهدید دارد

استفاده: python3 scripts/verify_feed_repo.py
"""
import base64
import re
import sys
from pathlib import Path

FEED_REPO = Path("/home/z/my-project/PersianCalendar-feed")
MAIN_REPO = Path("/home/z/my-project/PersianCalendar")

PINS = [
    "lpVtz80eClDZSxdm+GmxmBXuu3VN7ObOvjuTpQiQ6HI=",
    "Z5mIWsYEhgULQ6B3tGimJkkQCUNPN0ipslFBY8uArPw=",
]
PINNED_SHA = "a78ff9b662f85dd01a34e33592ae8356ee548402"
SIGN_HASH = "08f6ebb6131ed009cf20bd72adbd7c10fc77e300d8279a9a284622d328ddd31c"
VALIDATE_HASH = "b016d24e9a44b6b01b5b4db05f9ad5d3abf55ccdc606a31f997dd8abbaefda64"


def main() -> int:
    fails = []

    def chk(cond, msg):
        print(("✓ " if cond else "✗ ") + msg)
        if not cond:
            fails.append(msg)

    print("== ساختار ==")
    feed = FEED_REPO / "feed"
    jsons = sorted(feed.glob("*.json"))
    sigs = sorted(feed.glob("*.json.sig"))
    chk(len(jsons) >= 3, f"فایل‌های خوراک موجودند: {[j.name for j in jsons]}")
    chk(len(jsons) == len(sigs), f"تعداد .sig = تعداد json ({len(sigs)})")
    for j in jsons:
        chk((feed / (j.name + ".sig")).exists(), f"{j.name} → {j.name}.sig موجود")

    print("== امضا ==")
    try:
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
        pubs = [Ed25519PublicKey.from_public_bytes(base64.b64decode(p)) for p in PINS]
        full = True
    except ImportError:
        print("  (cryptography نصب نیست — فقط طول امضاها چک می‌شود؛ pip install cryptography)")
        pubs = None
        full = False
    for sg in sigs:
        raw = base64.b64decode(sg.read_bytes())
        chk(len(raw) == 64, f"{sg.name}: طول امضا ۶۴ بایت")
        if pubs:
            data = (feed / sg.name[:-4]).read_bytes()
            ok = False
            for p in pubs:
                try:
                    p.verify(raw, data)
                    ok = True
                except Exception:
                    pass
            chk(ok, f"{sg.name}: امضا با کلید عمومی اپ معتبر")
    if not full:
        fails.append("امضای کامل آزموده نشد — cryptography را نصب و دوباره اجرا کنید")

    print("== وورکفلو ==")
    wf = FEED_REPO / ".github/workflows/sign-feed.yml"
    chk(wf.exists(), "sign-feed.yml موجود است")
    if wf.exists():
        w = wf.read_text(encoding="utf-8")
        chk(PINNED_SHA in w, "PINNED_SHA درست (کد امضا با SHA ثابت)")
        chk(SIGN_HASH in w, "sha256 «sign_feed.py» پین شده")
        chk(VALIDATE_HASH in w, "sha256 «tools/ads_feed.py» پین شده")
        chk("@main/scripts/sign_feed.py" not in w and "@main/feed" not in w,
            "هیچ fetchی از main مخزن اصلی نیست (فقط SHA ثابت)")
        chk("permissions:" in w and "contents: write" in w, "permissions=contents:write")
        chk("FEED_SIGNING_KEY" in w, "Secret FEED_SIGNING_KEY مصرف می‌شود")
        chk("Verify signatures against app pinned keys" in w, "گام راستی‌آزمایی با کلیدهای اپ هست")
        # هش‌های پین‌شده باید با فایل‌های واقعی مخزن اصلی هم‌خوان باشند
        try:
            import hashlib
            h1 = hashlib.sha256((MAIN_REPO / "scripts/sign_feed.py").read_bytes()).hexdigest()
            h2 = hashlib.sha256((MAIN_REPO / "tools/ads_feed.py").read_bytes()).hexdigest()
            chk(h1 == SIGN_HASH, "هش واقعی sign_feed.py مخزن اصلی = هش پین‌شده")
            chk(h2 == VALIDATE_HASH, "هش واقعی tools/ads_feed.py مخزن اصلی = هش پین‌شده")
        except FileNotFoundError as e:
            fails.append(f"فایل مخزن اصلی پیدا نشد: {e}")

    print("== بدون کلید خصوصی ==")
    leaked = []
    pat = re.compile(r"^[A-Za-z0-9+/]{43}=$", re.M)
    for p in FEED_REPO.rglob("*"):
        if p.is_file() and p.suffix != ".sig" and ".git" not in p.parts:
            try:
                t = p.read_text(encoding="utf-8")
            except Exception:
                continue
            if p.name == "sign-feed.yml":
                # کلیدهای عمومی سنجاق‌شده ۴۴ کاراکتری‌اند — آن‌ها مجازند
                t = t.replace(PINS[0], "").replace(PINS[1], "")
            for m in pat.findall(t):
                leaked.append(p.name)
    chk(not leaked, f"هیچ بلاب ۳۲ بایتی مشکوک به کلید خصوصی در مخزن نیست ({leaked})")

    print("== README ==")
    readme = FEED_REPO / "README.md"
    chk(readme.exists() and "مدل تهدید" in readme.read_text(encoding="utf-8"),
        "README با مدل تهدید موجود است")

    print()
    print("نتیجه:", "همه ✓ — مخزن خوراک آمادهٔ push است" if not fails
          else f"{len(fails)} مورد ناموفق ✗")
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
