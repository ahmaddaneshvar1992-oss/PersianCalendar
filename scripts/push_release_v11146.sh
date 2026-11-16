#!/usr/bin/env bash
# انتشار نسخه‌های آمادهٔ محلی — push main + تگ‌ها با توکن تازهٔ Fine-grained
# (Contents: Read and write) — از ۱.۱۱.۴۵ و ۱.۱۱.۴۶ هر دو محلی آماده‌اند
#
# استفاده:  GH_TOKEN="github_pat_..." bash scripts/push_release_v11146.sh
# پس از push: CI «Build Android APK» اجرا می‌شود؛ برای خوراک، ابتدا مخزن خوراک
# را بسازید:  GH_TOKEN=... FEED_SIGNING_KEY="$(cat کلید.b64)" python3 scripts/create_feed_repo.py
# سپس:
#   GH_TOKEN=... python3 scripts/download_artifacts_v11145.py
#   GH_TOKEN=... python3 scripts/download_artifacts_v11146.py
#   python3 scripts/verify_apk_pair_v11145.py && python3 scripts/verify_apk_pair_v11146.py
#   python3 scripts/verify_feed_repo.py   (مخزن خوراک — پیش از push آن)
set -euo pipefail
cd "$(dirname "$0")/.."
: "${GH_TOKEN:?متغیر GH_TOKEN تنظیم نشده است — توکن Fine-grained تازه را در آن بگذارید}"
REMOTE="https://x-access-token:${GH_TOKEN}@github.com/ahmaddaneshvar1992-oss/PersianCalendar.git"
git push "$REMOTE" main
git push "$REMOTE" v1.11.45 v1.11.46
echo "push انجام شد — CI در حال اجراست"
echo "یادآوری: مخزن خوراک PersianCalendar-feed را هم push کنید (scripts/create_feed_repo.py خودش push می‌کند)"
