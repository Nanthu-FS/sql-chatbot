#!/bin/sh
# Plays one turn of Dither Farm headlessly (see app/src/test/kotlin/com/ditherfarm/play/PlayTest.kt).
# Usage: Tools/jvm-check/play.sh <dir>   (write <dir>/actions.txt first; prints <dir>/state.txt)
set -e
dir=$(cd "$1" && pwd)
cd "$(dirname "$0")"
${GRADLE:-/opt/gradle/bin/gradle} test -q -Pplay="$dir" --tests '*PlayTest*' >"$dir/gradle.log" 2>&1 || { tail -30 "$dir/gradle.log"; exit 1; }
cat "$dir/state.txt"
