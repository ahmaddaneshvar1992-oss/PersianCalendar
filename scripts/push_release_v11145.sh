#!/usr/bin/env bash
# انتشار نسخهٔ ۱.۱۱.۴۵ آمادهٔ محلی — push main + تگ با توکن تازهٔ Fine-grained
# (Contents: Read and write)
#
# استفاده:  GH_TOKEN="github_pat_..." bash scripts/push_release_v11145.sh
# پس از push: CI «Build Android APK» + «Sign feed files» اجرا می‌شوند؛ سپس:
#   GH_TOKEN=... python3 scripts/download_artifacts_v11145.py
#   python3 scripts/verify_apk_pair_v11145.py
set -euo pipefail
cd "$(dirname "$0")/.."
: "${GH_TOKEN:?متغیر GH_TOKEN تنظیم نشده است — توکن Fine-grained تازه را در آن بگذارید}"
REMOTE="https://x-access-token:${GH_TOKEN}@github.com/ahmaddaneshvar1992-oss/PersianCalendar.git"
git push "$REMOTE" main
git push "$REMOTE" v1.11.45
echo "push انجام شد — CI در حال اجراست"
