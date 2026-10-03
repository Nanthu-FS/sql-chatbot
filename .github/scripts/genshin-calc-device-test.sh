#!/usr/bin/env bash
# Installs the app on the running emulator, runs the instrumented smoke test (which walks every
# screen and saves screenshots), then hammers the app with monkey. Fails if a test fails or the
# app crashes; logs and screenshots are kept in device-out/ either way.
set -x
OUT=device-out
PKG=com.genshincalc.app
mkdir -p "$OUT"
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrument.txt" 2>&1
adb exec-out run-as $PKG tar c files/screens > "$OUT/screens.tar" 2>/dev/null
(cd "$OUT" && tar xf screens.tar && rm screens.tar) || true

# Cold launch, then random input.
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity > "$OUT/launch.txt" 2>&1
sleep 6
adb exec-out screencap -p > "$OUT/cold_launch.png"
adb shell monkey -p $PKG -s 11 --throttle 120 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 -v 3000 > "$OUT/monkey.txt" 2>&1
adb exec-out screencap -p > "$OUT/after_monkey.png"

adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d '*:E' > "$OUT/errors.txt"
set +x
echo "===== INSTRUMENTATION ====="
grep -E "INSTRUMENTATION_STATUS: (class|test)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_RESULT|INSTRUMENTATION_CODE|Tests run|^OK|FAILURES|Process crashed" "$OUT/instrument.txt"
grep -A 30 "INSTRUMENTATION_STATUS: stack=" "$OUT/instrument.txt" | head -120
echo "===== CRASH BUFFER ====="
head -120 "$OUT/crash.txt"
echo "===== MONKEY ====="
grep -B 2 -A 40 "CRASH" "$OUT/monkey.txt" | head -80
tail -3 "$OUT/monkey.txt"

status=0
grep -q "^OK (" "$OUT/instrument.txt" || { echo "Instrumented tests failed"; status=1; }
if grep -q "FATAL EXCEPTION" "$OUT/crash.txt"; then echo "App crashed"; status=1; fi
if grep -q "CRASH" "$OUT/monkey.txt"; then echo "Monkey found a crash"; status=1; fi
exit $status
