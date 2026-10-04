#!/usr/bin/env bash
# Installs the app on the running emulator, walks every screen via the instrumented
# smoke test, then hammers it with monkey. Collects screenshots and crash logs.
set -x
OUT=device-out
mkdir -p "$OUT"
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

adb shell am instrument -w -r com.smartnotes.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrument.txt" 2>&1
adb exec-out run-as com.smartnotes tar c files/screens > "$OUT/screens.tar" 2>/dev/null
(cd "$OUT" && tar xf screens.tar && rm screens.tar) || true

# The shipped APK is the optimised release build: stress-test that one.
adb install -r -g app/build/outputs/apk/release/app-release.apk
adb shell dumpsys gfxinfo com.smartnotes reset > /dev/null

# Cold launch, then random input.
adb shell am force-stop com.smartnotes
adb shell am start -W -n com.smartnotes/.ui.MainActivity > "$OUT/launch.txt" 2>&1
sleep 5
adb exec-out screencap -p > "$OUT/cold_launch.png"
adb shell monkey -p com.smartnotes -s 7 --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 -v 2500 > "$OUT/monkey.txt" 2>&1

adb shell dumpsys gfxinfo com.smartnotes > "$OUT/gfxinfo.txt"
adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d '*:E' > "$OUT/errors.txt"
set +x
echo "===== INSTRUMENTATION RESULTS ====="
grep -E "INSTRUMENTATION_STATUS: (class|test)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_RESULT|INSTRUMENTATION_CODE|Tests run|^OK|FAILURES|Process crashed" "$OUT/instrument.txt"
echo "===== INSTRUMENTATION STACKS ====="
grep -A 25 "INSTRUMENTATION_STATUS: stack=" "$OUT/instrument.txt" | head -150
echo "===== FRAME STATS ====="
grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Slow bitmap|Number Frame deadline missed" "$OUT/gfxinfo.txt" | head -12
echo "===== CRASH BUFFER ====="
head -120 "$OUT/crash.txt"
echo "===== MONKEY CRASH ====="
grep -B 2 -A 40 "CRASH" "$OUT/monkey.txt" | head -80
exit 0
