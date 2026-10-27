#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/ci

run_logged() {
  local name="$1"
  shift
  set +e
  "$@" 2>&1 | tee "build/ci/$name.log"
  local status=${PIPESTATUS[0]}
  set -e
  if (( status != 0 )); then
    python3 - "$name" <<'PYTHON'
import os, re, sys
from pathlib import Path
name = sys.argv[1]
lines = Path(f"build/ci/{name}.log").read_text(errors="replace").splitlines()
# Kotlin errors can precede a long Gradle stack trace. Keep them in the summary
# and in an annotation so the cause is visible without downloading private logs.
errors = [line for line in lines if re.search(r"(^e: |error:|FAILURE:|What went wrong|^> )", line)]
diagnostic = "\n".join(dict.fromkeys(errors + lines[-60:]))[-30000:]
with open(os.environ.get("GITHUB_STEP_SUMMARY", "build/ci/summary.md"), "a") as summary:
    summary.write(f"## Failed iOS check: {name}\n\n```text\n{diagnostic}\n```\n")
if os.environ.get("GITHUB_ACTIONS") == "true":
    escaped = diagnostic.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title={name} failed::{escaped}")
PYTHON
  fi
  return "$status"
}

case "${1:-}" in
  build)
    run_logged ios-build xcodebuild \
      -project iosApp/iosApp.xcodeproj \
      -scheme iosApp -configuration Debug \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      -derivedDataPath build/ios -resultBundlePath build/ci/ios-build.xcresult \
      CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build
    ;;
  test)
    : "${IOS_SIMULATOR_ID:?Select and boot the CI simulator first}"
    run_logged ios-tests ./gradlew \
      :core:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test --stacktrace
    python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for module in ("core", "composeApp"):
    reports = list(Path(module, "build/test-results/iosSimulatorArm64Test").glob("TEST-*.xml"))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
    if totals["tests"] - totals["skipped"] <= 0 or totals["failures"] or totals["errors"]:
        raise SystemExit(f"{module}: native tests did not pass with executed tests: {totals}")
    print(f"{module}: {totals}")
PY
    ;;
  *) printf 'Usage: %s build|test\n' "$0" >&2; exit 2 ;;
esac
