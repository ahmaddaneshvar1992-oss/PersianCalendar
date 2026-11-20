#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
دانلود جفت APK نسخهٔ ۱.۱۱.۵۲ از آرتیفکت‌های CI — پس از push موفق
استفاده:  GH_TOKEN="github_pat_..." python3 scripts/download_artifacts_v11152.py
خروجی: download/PersianCalendar-v1.11.52-install.apk و -admin-install.apk
سپس راستی‌آزمایی: python3 scripts/verify_apk_pair_v11152.py
"""
import io
import json
import os
import sys
import zipfile
import urllib.request
from pathlib import Path

REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
NAMES = {
    "PersianCalendar-public-apk": "PersianCalendar-v1.11.52-install.apk",
    "PersianCalendar-admin-apk": "PersianCalendar-v1.11.52-admin-install.apk",
}


def api(url: str):
    tok = os.environ.get("GH_TOKEN", "")
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {tok}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "release-helper",
    })
    return urllib.request.urlopen(req, timeout=60)


def main() -> int:
    # ۱) run موفق «Build Android APK» برای تگ v1.11.52 (نه آخرین run کلی)
    runs = json.load(api(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=100"))["workflow_runs"]
    build = [r for r in runs if r["name"] == "Build Android APK"
             and r["head_branch"] == "v1.11.52" and r["conclusion"] == "success"]
    if not build:
        pending = [r for r in runs if r["head_branch"] == "v1.11.52"]
        if pending:
            print("run تگ v1.11.52 هست ولی هنوز موفق نشده:",
                  [(r["conclusion"] or r["status"]) for r in pending])
        else:
            print("run تگ v1.11.52 پیدا نشد — CI ممکن است هنوز شروع نشده باشد")
        return 1
    run = build[0]
    run_id = run["id"]
    print("run موفق:", run_id, run["html_url"])
    # ۲) آرتیفکت‌ها
    arts = json.load(api(f"https://api.github.com/repos/{REPO}/actions/runs/{run_id}/artifacts"))["artifacts"]
    found = {}
    for a in arts:
        if a["name"] in NAMES and not a["expired"]:
            found[a["name"]] = a["id"]
    if set(found) != set(NAMES):
        print("آرتیفکت‌های مورد انتظار کامل نیست:", list(found), "از", list(NAMES))
        return 1
    # ۳) دانلود و بازکردن
    for name, aid in found.items():
        print("دانلود:", name, "(id", aid, ")")
        blob = api(f"https://api.github.com/repos/{REPO}/actions/artifacts/{aid}/zip").read()
        z = zipfile.ZipFile(io.BytesIO(blob))
        infos = z.infolist()
        print("  داخل:", [i.filename for i in infos])
        apk = [i for i in infos if i.filename.endswith(".apk")][0]
        dest = Path("/home/z/my-project/download") / NAMES[name]
        dest.write_bytes(z.read(apk.filename))
        print("  →", dest, f"({dest.stat().st_size/1e6:.1f} MB)")
    print("جفت APK آماده است — اکنون: python3 scripts/verify_apk_pair_v11152.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
