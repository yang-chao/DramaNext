#!/bin/zsh
# Watch probe log while the drama plays; screenshot when ad-ish markers or detector events appear.
# usage: watch_ads.zsh <minutes>
S=R5CY708XC3W
DIR=/Volumes/MacExt/develop/github/DramaNext/probe-logs
LOG=$DIR/live.log
END=$(( $(date +%s) + ${1:-10} * 60 ))
seen=$(wc -l < $LOG)
shots=0; nextAt=0
while [ $(date +%s) -lt $END ]; do
  sleep 2
  total=$(wc -l < $LOG)
  [ $total -le $seen ] && continue
  new=$(sed -n "$((seen+1)),${total}p" $LOG); seen=$total
  echo "$new" | grep "I/DramaNext" | sed 's/^/  EVT /'
  echo "$new" | grep -o 'nodes=[0-9]* state=.*' | sort -u | grep -v NotAd | sed 's/^/  STATE /'
  ep=$(echo "$new" | grep -o ' V TextView [a-z_]* t="第[0-9]*集"' | tail -1)
  [ -n "$ep" ] && echo "$(date +%T) $ep"
  if echo "$new" | grep ' V ' | grep -q -E '广告|下载|详情|跳过|[0-9] ?[sS秒]|奖励|解锁|了解更多|重播' || echo "$new" | grep -q -E 'state=(Playing|Finished)|NEXT via'; then
    if [ $shots -lt 25 ] && [ $(date +%s) -ge $nextAt ]; then
      f=$DIR/ad-$(date +%H%M%S).png
      adb -s $S exec-out screencap -p > $f; shots=$((shots+1)); nextAt=$(( $(date +%s) + 4 ))
      echo "$(date +%T) AD-ish -> $f"
      echo "$new" | grep ' V ' | grep -o 'V [A-Za-z]* [a-z_0-9]* \(t\|d\)="[^"]*"' | head -30 | sed 's/^/    /'
    fi
  fi
done
echo done
