#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
راه‌اندازی یک‌بارهٔ مخزن خوراک «PersianCalendar-feed» — از ۱.۱۱.۴۶ (بازرسی امنیتی)

کارت‌ها:
  ۱) ساخت مخزن عمومی PersianCalendar-feed (اگر API اجازهٔ ساخت ندهید، مراحل وب
     چاپ می‌شود و ادامه از روی مخزن موجود می‌رود)
  ۲) push مخزن محلی (feed/ + وورکفلوی امضا + README)
  ۳) گذاشتن Secret «FEED_SIGNING_KEY» (Base64 از ۳۲ بایت seed کلید خصوصی
     Ed25519) — با API و رمزنگاری sealed-box libsodium (نیاز به PyNaCl)؛ اگر
     PyNaCl نبود، مراحل وب دقیق چاپ می‌شود
  ۴) راه‌اندازی نخستین اجرای وورکفلوی امضا (workflow_dispatch)
  ۵) چاپ چک‌لیست نهایی: Revoke توکن قدیمی چندمخزنی، پاک‌کردن Secret کلید از
     مخزن اصلی، گذاشتن توکن مختص خوراک در پنل مدیر

استفاده:
  GH_TOKEN="github_pat_..." FEED_SIGNING_KEY="$(cat feed-ed25519-private.b64)" \
      python3 scripts/create_feed_repo.py

دسترسی توکن: برای ساخت مخزن و مدیریت Secret به Administration هم نیاز است؛
اگر توکن فقط Contents/Actions دارد، مراحل وب هر گام را دقیق می‌گوید.
"""
import base64
import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

REPO = "ahmaddaneshvar1992-oss/PersianCalendar-feed"
LOCAL = Path("/home/z/my-project/PersianCalendar-feed")
MAIN = "ahmaddaneshvar1992-oss/PersianCalendar"


def api(url, method="GET", payload=None, raw=False):
    tok = os.environ.get("GH_TOKEN", "")
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {tok}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "feed-repo-setup",
        "Content-Type": "application/json",
    })
    try:
        r = urllib.request.urlopen(req, timeout=60)
        body = r.read()
        return r.status, (body if raw else (json.loads(body) if body else {}))
    except urllib.error.HTTPError as e:
        body = e.read()
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body.decode("utf-8", "replace")[:300]}


def step(title):
    print(f"\n== {title} ==")


def main() -> int:
    tok = os.environ.get("GH_TOKEN", "")
    if not tok:
        print("GH_TOKEN تنظیم نشده است.")
        return 1
    key_b64 = os.environ.get("FEED_SIGNING_KEY", "").strip()

    step("۱) بررسی توکن")
    st, me = api("https://api.github.com/user")
    if st != 200:
        print("توکن نامعتبر است:", me)
        return 1
    print("کاربر:", me.get("login"))

    step("۲) ساخت مخزن (اگر از قبل نیست)")
    st, r = api("https://api.github.com/user/repos", "POST", {
        "name": "PersianCalendar-feed",
        "description": "خوراک‌های امضاشدهٔ اپ بادخزان (تقویم فارسی) — فقط feed/ و وورکفلوی امضای Ed25519؛ کد امضا با SHA ثابت از مخزن اصلی گرفته می‌شود",
        "private": False,
        "auto_init": False,
        "has_issues": False,
        "has_wiki": False,
        "has_projects": False,
    })
    if st in (201, 200):
        print("مخزن ساخته شد:", r.get("html_url"))
    elif st == 422:
        print("مخزن از قبل وجود دارد — ادامه می‌دهیم")
    else:
        print(f"ساخت با API ممکن نشد (کد {st}) — دستی بسازید:")
        print("  GitHub ← New repository ← نام: PersianCalendar-feed ← Public ← Create")
        print("سپس این اسکریپت را دوباره اجرا کنید (گام‌های بعدی روی مخزن موجود کار می‌کنند)")
        if st in (403, 404):
            return 1

    step("۳) push مخزن محلی")
    push_url = f"https://x-access-token:{tok}@github.com/{REPO}.git"
    for args in (["git", "push", push_url, "main"],):
        r = subprocess.run(args, cwd=LOCAL, capture_output=True, text=True)
        print(r.stdout.strip() or r.stderr.strip())
        if r.returncode != 0:
            print("push ناموفق — مخزن از قبل هم‌گام است یا دسترسی نیست؛ ادامه می‌دهیم")

    step("۴) Secret «FEED_SIGNING_KEY»")
    if not key_b64:
        print("FEED_SIGNING_KEY در محیط داده نشده — Secret را دستی بگذارید:")
        print("  GitHub ← PersianCalendar-feed ← Settings ← Secrets and variables ← Actions")
        print("  ← New repository secret ← نام: FEED_SIGNING_KEY ← مقدار: Base64 سی‌ودو بایتی seed کلید")
    else:
        st, pk = api(f"https://api.github.com/repos/{REPO}/actions/secrets/public-key")
        if st != 200:
            print(f"گرفتن کلید عمومی Secret ممکن نشد (کد {st}) — دستی بگذارید (مسیر بالا)")
        else:
            try:
                import nacl.public  # type: ignore
                sealed = nacl.public.SealedBox(
                    nacl.public.PublicKey(pk["public_key"].encode())
                ).encrypt(key_b64.encode())
                st2, _ = api(f"https://api.github.com/repos/{REPO}/actions/secrets/FEED_SIGNING_KEY",
                             "PUT", {"encrypted_value": base64.b64encode(sealed).decode(),
                                     "key_id": pk["key_id"]})
                print("Secret:", "گذاشته شد ✓" if st2 in (201, 204) else f"ناموفق (کد {st2}) — دستی بگذارید")
            except ImportError:
                print("PyNaCl نصب نیست (pip install pynacl) — Secret را دستی بگذارید (مسیر بالا)")

    step("۵) نخستین اجرای وورکفلو (dispatch)")
    st, _ = api(f"https://api.github.com/repos/{REPO}/actions/workflows/sign-feed.yml/dispatches",
                "POST", {"ref": "main"})
    print("dispatch:", "راه افتاد ✓ — چند دقیقه بعد در تب Actions سبز بودنش را ببینید" if st == 204
          else f"ناموفق (کد {st}) — از تب Actions دستی Run workflow بزنید")

    step("۶) چک‌لیست امنیتی پایانی (دستی)")
    print("""
  □ توکن قدیمیِ چندمخزنی پنل مدیر را در گیت‌هاب Revoke کنید
  □ Secret «FEED_SIGNING_KEY» را از مخزن اصلی (PersianCalendar) پاک کنید —
    کلید از این به بعد فقط در مخزن خوراک است
  □ وورکفلوی قدیمی sign-feed.yml از مخزن اصلی حذف شده (کامیت ۱.۱۱.۴۶) — نباید برگردد
  □ توکن تازهٔ Fine-grained فقط برای PersianCalendar-feed بسازید
    (Contents: Read and write + Actions: Read and write) و در پنل مدیر بگذارید
  □ یکی از انتشارهای پنل را تست کنید و سبزشدن وورکفلو را ببینید
""")
    return 0


if __name__ == "__main__":
    sys.exit(main())
