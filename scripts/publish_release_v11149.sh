#!/usr/bin/env bash
# انتشار کامل نسخهٔ ۱.۱۱.۴۹ در گیت‌هاب — یک دستور، پنج گام:
#   ۱) push main + تگ v1.11.49
#   ۲) انتظار برای run موفق «Build Android APK» روی تگ v1.11.49
#   ۳) دانلود جفت APK از آرتیفکت‌های CI
#   ۴) راستی‌آزمایی جفت (verify_apk_pair_v11149.py)
#   ۵) ساخت Release «v1.11.49» با یادداشت WHATS-NEW و پیوست هر دو APK
#
# استفاده:  GH_TOKEN="github_pat_..." bash scripts/publish_release_v11149.sh
# توکن Fine-grained با دسترسی «Contents: Read and write» به مخزن
# ahmaddaneshvar1992-oss/PersianCalendar کافی است.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${GH_TOKEN:?متغیر GH_TOKEN تنظیم نشده است — توکن تازهٔ Fine-grained را در آن بگذارید}"
export GH_TOKEN

echo "— گام ۱: push main + تگ v1.11.49 —"
bash scripts/push_release_v11149.sh

echo "— گام ۲: انتظار برای CI تگ v1.11.49 —"
python3 - <<'PY'
import json, os, sys, time, urllib.request

REPO = "ahmaddaneshvar1992-oss/PersianCalendar"

def api(url):
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "release-helper",
    })
    return urllib.request.urlopen(req, timeout=60)

deadline = time.time() + 40 * 60  # حداکثر ۴۰ دقیقه
while True:
    runs = json.load(api(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=100"))["workflow_runs"]
    tag_runs = [r for r in runs if r["head_branch"] == "v1.11.49"]
    if tag_runs:
        r = tag_runs[0]
        print(f"  وضعیت: {r['status']} / {r['conclusion']}")
        if r["status"] == "completed":
            if r["conclusion"] == "success":
                print("  CI موفق بود ✓")
                sys.exit(0)
            sys.exit("CI ناموفق بود ✗ — لاگ را در گیت‌هاب ببینید")
    else:
        print("  run هنوز شروع نشده…")
    if time.time() > deadline:
        sys.exit("انتظار از ۴۰ دقیقه گذشت ✗")
    time.sleep(60)
PY

echo "— گام ۳: دانلود جفت APK —"
python3 scripts/download_artifacts_v11149.py

echo "— گام ۴: راستی‌آزمایی —"
python3 scripts/verify_apk_pair_v11149.py

echo "— گام ۵: ساخت Release با هر دو APK —"
python3 - <<'PY'
import json, os, urllib.request
from pathlib import Path

REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
TAG = "v1.11.49"
NOTES = Path("docs/WHATS-NEW-v1.11.49-FA.md").read_text(encoding="utf-8")
ASSETS = [
    Path("/home/z/my-project/download/PersianCalendar-v1.11.49-install.apk"),
    Path("/home/z/my-project/download/PersianCalendar-v1.11.49-admin-install.apk"),
]

def api(url, data=None, headers=None, raw=None):
    h = {
        "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "release-helper",
    }
    if headers:
        h.update(headers)
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    req = urllib.request.Request(url, data=body, headers=h, method="POST" if (data or raw) else "GET")
    return urllib.request.urlopen(req, timeout=120)

existing = None
try:
    with api(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}") as r:
        existing = json.load(r)
except Exception:
    pass

if existing:
    rel_id = existing["id"]
    print("Release موجود است — فقط assets پیوست می‌شوند")
else:
    payload = {
        "tag_name": TAG,
        "name": "نسخهٔ ۱.۱۱.۴۹ — نوار ناوبری پایین صفحه و ذکر امروز پایین تقویم",
        "body": NOTES,
        "draft": False,
        "prerelease": False,
    }
    with api(f"https://api.github.com/repos/{REPO}/releases", payload) as r:
        rel = json.load(r)
    rel_id = rel["id"]
    print("Release ساخته شد:", rel["html_url"])

for a in ASSETS:
    url = f"https://uploads.github.com/repos/{REPO}/releases/{rel_id}/assets?name={a.name}"
    with api(url, raw=a.read_bytes(), headers={"Content-Type": "application/vnd.android.package-archive"}) as r:
        j = json.load(r)
    print("پیوست شد:", a.name, f"({j['size']/1e6:.1f} MB)")
print("تمام شد — صفحهٔ انتشار:", f"https://github.com/{REPO}/releases/tag/{TAG}")
PY
