#!/usr/bin/env bash
# 真机视觉回归：按当前主题截取首页、设置、对话三页，存到 $1（默认 ./shots/<时间>）。
# 前提：adb 已连接；App 已配置好主机并连上。坐标按 1080x2400 屏幕。
set -euo pipefail
PKG=${PKG:-dev.herdr.signal.debug}
OUT=${1:-shots/$(date +%Y%m%d-%H%M%S)}
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "$OUT/$1.png"; }
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/dev.herdr.signal.MainActivity" >/dev/null
sleep 6; shot home
adb shell input tap 810 549; sleep 1; shot home-idle
adb shell input tap 1003 158; sleep 2; shot settings
adb shell input keyevent 4; sleep 1
adb shell input tap 330 714; sleep 4; shot conversation
adb shell input keyevent 4
