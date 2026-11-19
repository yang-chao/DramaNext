# DramaNext（短剧自动下一集）

一个轻量级、纯本地运行的 Android 无障碍辅助工具：当短剧 App 播放剧间插播广告时，自动监测广告是否播放结束，并在结束后模拟一次垂直上划进入下一集，省去手动划屏和避免误触广告下载区域。

## 核心特性

- **不跳过、不屏蔽、不点击广告**：仅在确认广告播放完成后，通过 `AccessibilityService` 模拟一次垂直上划手势（`dispatchGesture`），并在重试时兜底调用垂直滚动动作（`accessibilityActionScrollDown`），绝不点击广告落地页或下载按钮。
- **零网络权限 · 完全本地运行**：`AndroidManifest.xml` 未申请 `INTERNET` 权限，仅在目标短剧应用前台运行时读取当前界面节点，不收集或上传任何内容。
- **规则评分引擎（`AdDetector`）**：综合广告标签、主流广告 SDK 视图 ID（穿山甲 / Pangle、优量汇 / GDT、快手等）、CTA 按钮文案、倒计时和结束提示词判断状态，并排除「开通会员免广告」等正常页面干扰。
- **状态机防循环（`AutoNextEngine`）**：包含 800ms 结束防抖、倒计时消失（1.5s）/ 倒计时超时（2.5s）兜底、单条广告最多触发 2 次（间隔 2.5s），防止在广告页或正常剧集里反复误划。
- **可视化控制台（Jetpack Compose）**：
  - 实时显示无障碍连接状态、当前检测阶段（正常剧集 / 广告倒计时 / 广告已结束）与累计自动下一集次数。
  - 支持开启「仅检测（Dry-run）」模式，只观察识别结果而不执行滑动。
  - 内置无障碍节点探针日志（`DNProbe` + 应用私有目录 `probe.log`），便于排查或扩展适配新的短剧 App。

## 当前支持的应用

配置于 [`AppRules`](app/src/main/java/com/dramanext/app/AdDetector.kt) 与 [`autonext_service.xml`](app/src/main/res/xml/autonext_service.xml)：

| 应用 | 包名 |
| --- | --- |
| 红果短剧 | `com.phoenix.read` |
| 红果短剧（Google Play 国际版） | `com.phoenix.read.oversea.gp` |

## 使用说明

1. 安装应用（Android 8.0 / API 26 及以上）。
2. 打开 **DramaNext（短剧自动下一集）**，点击开启无障碍服务。
   - **Android 13+ 侧载安装提示**：如果系统弹出「出于安全考虑，此设置目前已受限」，请进入系统「设置 → 应用 → 短剧自动下一集 → 应用信息」，点击右上角菜单选择 **允许受限设置**，再返回开启无障碍开关。
3. 保持「自动执行上划」开关处于开启状态（如需先测试识别准确率，可暂时关闭该开关进入「仅检测」模式）。
4. 建议在系统应用设置中将电池策略设为 **不受限制**，并锁定后台，避免长时看剧过程中服务被系统回收。

## 项目结构

```text
app/src/main/
├── AndroidManifest.xml                                 # 声明无障碍服务与目标包名查询（无网络权限）
├── res/xml/autonext_service.xml                        # 无障碍服务监听范围配置
└── java/com/dramanext/app/
    ├── AdDetector.kt                                   # 节点模型与广告状态规则评分引擎
    ├── AutoNextEngine.kt                               # 广告结束判定与防循环状态机（纯逻辑可单测）
    ├── AutoNextService.kt                              # AccessibilityService 监听、手势滑动与 DNProbe 日志
    ├── MainActivity.kt                                 # Jetpack Compose 主界面、状态展示与开关控制
    └── Prefs.kt                                        # SharedPreferences 本地配置与统计
app/src/test/java/com/dramanext/app/
└── AdDetectorTest.kt                                   # 规则识别与状态机单元测试
tools/
├── swipe_scan.zsh                                      # ADB 辅助翻页并抓取节点探针摘要
└── watch_ads.zsh                                       # ADB 监听实时日志并在疑似广告页自动截图
```

## 构建与测试

项目基于 Gradle Kotlin DSL 构建（`compileSdk = 37`、`minSdk = 26`、Java 17）。

1. 配置 Android SDK 路径（设置环境变量 `ANDROID_HOME`，或在未提交的 `local.properties` 中写入 `sdk.dir=/path/to/Android/sdk`）。
2. 运行单元测试与 Debug 构建：

```sh
./gradlew testDebugUnitTest assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 扩展适配更多短剧 App

当前规则以信息流插播广告（「广告播完后需要上划继续看下一集」的交互形态）为主，适配新 App 的步骤：

1. 在 `app/src/main/java/com/dramanext/app/AdDetector.kt` 的 `AppRules` 中新增或扩展 `AppRule`，并加入 `AppRules.ALL`。
2. 同步更新 `app/src/main/res/xml/autonext_service.xml` 中的 `android:packageNames` 与 `app/src/main/AndroidManifest.xml` 中的 `<queries>`。
3. 打开 App 内的「节点探针日志」，通过 `adb logcat -s DNProbe` 或 `tools/watch_ads.zsh` 抓取广告页节点特征以调优正则。

> **注意**：对于海外常见「激励视频播完后必须点击右上角 `X` 关闭」的弹窗广告形态，仅靠垂直上划无法退出，当前版本暂未实现自动点击关闭按钮。

## 开源协议与声明

- 本项目基于 [MIT License](LICENSE) 开源。
- 本项目仅作为 Android `AccessibilityService` 无障碍辅助翻页的技术实现与学习参考，不提供广告屏蔽、去广告或模拟点击广告功能。请遵守相关平台的使用规范。
