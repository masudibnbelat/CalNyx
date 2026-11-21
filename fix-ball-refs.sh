#!/usr/bin/env bash
# Ball file er purono service naam fix (2026-11-21).
# rename-spy-thirdeye.sh spy/ + NavGraph bodlechilo, kintu ball/ er
# 2ta file miss korechilo - tai ktdev run e aschilo:
#   Unresolved reference 'ThirdEyeService' / 'ScreenRecordService'
# Project root (CalNyx/) theke chalabe:
#   chmod +x fix-ball-refs.sh && ./fix-ball-refs.sh
set -e

BALL1="app/src/main/java/com/calnyx/app/ball/BallPanelActivity.kt"
BALL2="app/src/main/java/com/calnyx/app/ball/FloatingBallService.kt"

for f in "$BALL1" "$BALL2"; do
  [ -f "$f" ] || { echo "ERROR: $f pelam na - project root theke chalao."; exit 1; }
done

for f in "$BALL1" "$BALL2"; do
  sed -i \
    -e 's/com\.calnyx\.app\.thirdeye\.ThirdEyeService/com.calnyx.app.spy.SpyService/g' \
    -e 's/\bThirdEyeService\b/SpyService/g' \
    -e 's/com\.calnyx\.app\.thirdeye\.ScreenRecordService/com.calnyx.app.spy.SpyScreenRecordService/g' \
    -e 's/\bScreenRecordService\b/SpyScreenRecordService/g' \
    -e 's/com\.calnyx\.app\.thirdeye\.ScreenRecordToolbarActivity/com.calnyx.app.spy.SpyScreenRecordToolbarActivity/g' \
    -e 's/\bScreenRecordToolbarActivity\b/SpyScreenRecordToolbarActivity/g' \
    "$f"
  echo "fixed: $f"
done

echo "--- check: ei 2ta file e ar purono naam ache kina (kichu na aslei OK) ---"
grep -n "ThirdEyeService\|ScreenRecordService\|ScreenRecordToolbarActivity" "$BALL1" "$BALL2" || echo "OK: 2ta file ei clean."

echo "--- check: puro project e ar kothao purono service naam ache kina ---"
echo "(spy/ folder er comment er vitore purono naam thakle somossa na - oita sudhu lekha)"
grep -rn "ThirdEyeService\|ScreenRecordService\|ScreenRecordToolbarActivity" app/src/main/java --include=*.kt || echo "OK: project e ar kothao purono service naam nai."
