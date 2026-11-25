#!/usr/bin/env bash
# انتشار کامل نسخهٔ ۱.۱۱.۵۳ — push main + تگ → انتظار CI → دانلود جفت APK
# → راستی‌آزمایی → کامل‌کردن Release (وورکفلو release را با APK عمومی می‌سازد؛
# اینجا نام/متن از WHATS-NEW + APK مدیریت + make_latest اضافه می‌شود)
#
# استفاده:  GH_TOKEN="github_pat_..." bash scripts/publish_release_v11153.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${GH_TOKEN:?متغیر GH_TOKEN تنظیم نشده است}"
export GH_TOKEN

echo "— گام ۱: push main + تگ v1.11.53 —"
bash scripts/push_release_v11153.sh

echo "— گام ۲: انتظار برای CI تگ v1.11.53 —"
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

deadline = time.time() + 40 * 60
while True:
    runs = json.load(api(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=100"))["workflow_runs"]
    tag_runs = [r for r in runs if r["head_branch"] == "v1.11.53"]
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
python3 scripts/download_artifacts_v11153.py

echo "— گام ۴: راستی‌آزمایی —"
python3 scripts/verify_apk_pair_v11153.py

echo "— گام ۵: کامل‌کردن Release v1.11.53 (نام + متن + APK مدیریت + latest) —"
python3 - <<'PY'
import json, os, urllib.request, urllib.error
from pathlib import Path

REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
TAG = "v1.11.53"
NOTES = Path("docs/WHATS-NEW-v1.11.53-FA.md").read_text(encoding="utf-8")
NAME = "نسخهٔ ۱.۱۱.۵۳ — پنج رفع باگ (اندروید ۷/۸، مجوز اعلان، نیمه‌شب اذان، ناوبری اعلان، + تکراری و تصویر بنر)"
ADMIN_APK = Path("/home/z/my-project/download/PersianCalendar-v1.11.53-admin-install.apk")

H = {"Authorization": f"Bearer {os.environ['GH_TOKEN']}",
     "Accept": "application/vnd.github+json", "User-Agent": "release-helper"}

def api(url, data=None, method=None, headers=None):
    h = dict(H)
    if headers: h.update(headers)
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    return urllib.request.urlopen(req, timeout=120)

rels = json.load(api(f"https://api.github.com/repos/{REPO}/releases?per_page=100"))
rel = next((r for r in rels if r["tag_name"] == TAG), None)
if rel is None:
    body = json.dumps({"tag_name": TAG, "name": NAME, "body": NOTES,
                       "draft": False, "prerelease": False}).encode()
    rel = json.load(api(f"https://api.github.com/repos/{REPO}/releases",
                        data=body, method="POST", headers={"Content-Type": "application/json"}))
    print("Release ساخته شد")
else:
    print("Release از وورکفلو موجود است")

body = json.dumps({"name": NAME, "body": NOTES, "draft": False,
                   "prerelease": False, "make_latest": "true"}).encode()
api(f"https://api.github.com/repos/{REPO}/releases/{rel['id']}",
    data=body, method="PATCH", headers={"Content-Type": "application/json"})
print("نام و یادداشت کامل شد ✓")

assets = {a["name"] for a in json.load(api(f"https://api.github.com/repos/{REPO}/releases/{rel['id']}/assets"))}
if ADMIN_APK.name not in assets:
    up = f"https://uploads.github.com/repos/{REPO}/releases/{rel['id']}/assets?name={ADMIN_APK.name}"
    a = json.load(api(up, data=ADMIN_APK.read_bytes(), method="POST",
                      headers={"Content-Type": "application/vnd.android.package-archive"}))
    print("پیوست ✓", a["name"], f"{a['size']/1e6:.1f} MB")
else:
    print("APK مدیریت از قبل پیوست است ✓")

final = json.load(api(f"https://api.github.com/repos/{REPO}/releases/latest"))
print("نهایی:", final["tag_name"], "| assets:", len(final["assets"]), "| url:", final["html_url"])
PY

echo "— انتشار ۱.۱۱.۵۳ کامل ✓ —"
