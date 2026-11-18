# DramaNext（短剧自动下一集）

Android 无障碍工具：在短剧 App（当前适配红果短剧）播放插播广告时，监测广告是否结束，并在结束后自动上划进入下一集。

- **不跳过、不屏蔽、不点击广告**：只在广告播完后模拟一次垂直上划（`dispatchGesture`），避免误触下载。
- **状态机防循环**：结束防抖 + 倒计时消失/超时兜底，单条广告最多触发 2 次。
- **内置探针日志**：可随时记录无障碍界面节点，便于适配更多短剧 App。

## 结构

- `app/src/main/java/com/dramanext/app/AdDetector.kt` — 节点模型与广告状态规则引擎（`AppRules.HONGGUO`）
- `app/src/main/java/com/dramanext/app/AutoNextEngine.kt` — 广告结束判定与防循环状态机
- `app/src/main/java/com/dramanext/app/AutoNextService.kt` — `AccessibilityService` 监听、滑动执行与 `DNProbe` 日志
- `app/src/main/java/com/dramanext/app/MainActivity.kt` — 状态展示、开关控制与快捷启动
- `tools/` — 真机 ADB 探测与抓包脚本

## 构建与测试

在未提交的 `local.properties` 中设置 `sdk.dir`（或配置 `ANDROID_HOME`）：

```sh
./gradlew testDebugUnitTest assembleDebug
```
