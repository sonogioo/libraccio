#!/usr/bin/env bash
# Avvia Libraccio nell'emulatore con la libreria demo e scatta gli screenshot.
set -x
APK=app/build/outputs/apk/release/app-release.apk
OUT=screenshots
mkdir -p $OUT
adb install -r "$APK"
adb shell settings put global window_animation_scale 1
shot() { sleep "$2"; adb exec-out screencap -p > "$OUT/$1.png"; }
act() { adb shell am start -n it.libraccio.app/.MainActivity --es demoAction "$1" >/dev/null; }

adb shell am start -n it.libraccio.app/.MainActivity --ez demo true
shot 00-splash 1
shot 01-room-day 28
act closeup;  shot 02-closeup 6
act home;     shot 03-home 5
act pick;     shot 04-pullout 6
act open;     shot 05-detail 6
act list;     shot 06-list 4
act night;    shot 07-room-night 10
act closeup;  shot 08-night-closeup 6
adb logcat -d | grep -iE "libraccio|AndroidRuntime|FATAL|GLES|shader" | tail -300 > $OUT/logcat.txt
exit 0
