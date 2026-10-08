#!/bin/bash
# 에뮬레이터 안에서: Palma 2 화면으로 맞추고, 설치, 계측 테스트 실행, 스크린샷 수거
set -x
adb shell wm size 824x1648
adb shell wm density 320
adb shell settings put system screen_off_timeout 1800000
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.dain.focusink.test/androidx.test.runner.AndroidJUnitRunner > instrument.txt 2>&1
cat instrument.txt | tail -40
mkdir -p screens
adb pull /sdcard/Android/data/com.dain.focusink/files/screens/. screens/ || true
ls -la screens
adb logcat -d -s AndroidRuntime:E | tail -40 >> instrument.txt
grep -q "^OK (" instrument.txt
