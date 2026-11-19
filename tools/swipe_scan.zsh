#!/bin/zsh
# Swipe through episodes and report probe summaries after each swipe.
# usage: [ANDROID_SERIAL=<serial>] swipe_scan.zsh [count] [waitSec]
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LOG="$ROOT/probe-logs/live.log"

ADB=(adb)
if [ -n "$ANDROID_SERIAL" ]; then
  ADB+=(-s "$ANDROID_SERIAL")
fi

N=${1:-10}; W=${2:-3}
for i in $(seq 1 "$N"); do
  "${ADB[@]}" shell input swipe 540 1650 540 600 250
  sleep "$W"
  last=$(grep -n "====" "$LOG" | tail -1 | cut -d: -f1)
  [ -z "$last" ] && continue
  hdr=$(sed -n "${last}p" "$LOG" | sed 's/.*==== //')
  texts=$(tail -n +"$last" "$LOG" | grep ' V ' | grep -o 't="[^"]*"\|d="[^"]*"' | tr '\n' ' ' | cut -c1-400)
  echo "[$i] $hdr"
  echo "    $texts"
  if tail -n +"$last" "$LOG" | grep ' V ' | grep -q -E '广告|下载|详情|跳过|秒|奖励|解锁|了解更多'; then echo "    >>> AD-ish markers found, stopping"; break; fi
done
