#!/usr/bin/env bash
# Runs inside the emulator step of .github/workflows/screenshots.yml (or by hand, with one
# device attached and the debug APK built). Screenshots and the raw video go to
# docs/screenshots/v3.0/; nothing here talks to a real server, the app runs in demo mode.
set -u
out=docs/screenshots/v3.0
mkdir -p "$out" shots
adb wait-for-device
adb install -r app/build/outputs/apk/phone/debug/app-phone-debug.apk

# A tidy status bar: 12:00, full battery, no notification icons.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

maestro test .maestro/screenshots/v3_showcase.yaml || echo "showcase flow ended early, keeping what it took"
cp shots/*.png "$out/" 2>/dev/null || true

# The video: screenrecord stops by itself at the limit; the flow takes ~50 s.
adb shell rm -f /sdcard/demo.mp4
adb shell screenrecord --bit-rate 8000000 --time-limit 120 /sdcard/demo.mp4 &
rec=$!
sleep 2
maestro test .maestro/screenshots/v3_video.yaml || echo "video flow ended early"
sleep 1
adb shell pkill -INT screenrecord || true
wait $rec || true
sleep 2
adb pull /sdcard/demo.mp4 "$out/demo-raw.mp4" || true
ls -la "$out"
