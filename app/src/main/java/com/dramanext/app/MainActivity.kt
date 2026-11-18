package com.dramanext.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val BgWarmPaper = Color(0xFFF6F4EF)
private val CardWhite = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE6E2D8)
private val SurfaceSubtle = Color(0xFFEEEAE0)
private val TextPrimary = Color(0xFF1F1D1A)
private val TextSecondary = Color(0xFF635E55)
private val TextMuted = Color(0xFF8C867B)

private val BrandTerracotta = Color(0xFFC84B31)
private val BrandTerracottaSoft = Color(0xFFFCEAE5)
private val BrandTerracottaBorder = Color(0xFFF3C5B9)

private val ActiveEmerald = Color(0xFF1B6B4D)
private val ActiveEmeraldSoft = Color(0xFFE2F3EC)
private val ActiveEmeraldBorder = Color(0xFFB7E0CE)

private val PausedAmber = Color(0xFFB45309)
private val PausedAmberSoft = Color(0xFFFEF2D8)
private val PausedAmberBorder = Color(0xFFF3D49B)

@Composable
private fun DramaNextTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandTerracotta,
            onPrimary = Color.White,
            primaryContainer = BrandTerracottaSoft,
            onPrimaryContainer = Color(0xFF5F1B0C),
            secondary = ActiveEmerald,
            onSecondary = Color.White,
            secondaryContainer = ActiveEmeraldSoft,
            onSecondaryContainer = Color(0xFF0D3D2A),
            background = BgWarmPaper,
            surface = CardWhite,
            surfaceVariant = SurfaceSubtle,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary,
            outlineVariant = CardBorder,
        ),
        content = content,
    )
}

data class InstalledTargetApp(
    val rule: AppRule,
    val installedPkg: String?,
    val versionName: String?,
    val launchIntent: Intent?,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        val prefs = Prefs(this)
        setContent {
            DramaNextTheme {
                DramaNextScreen(
                    context = this,
                    prefs = prefs,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }
}

@Composable
private fun DramaNextScreen(
    context: Context,
    prefs: Prefs,
    onOpenAccessibility: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val runtime by AutoNextService.runtime.collectAsStateWithLifecycle()

    var serviceEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var actEnabled by remember { mutableStateOf(prefs.actEnabled) }
    var probeEnabled by remember { mutableStateOf(prefs.probeEnabled) }
    var nextCount by remember { mutableIntStateOf(prefs.nextCount) }
    var lastEvent by remember { mutableStateOf(prefs.lastEvent) }
    var targetApps by remember { mutableStateOf(inspectTargetApps(context)) }

    val refreshState = {
        serviceEnabled = isAccessibilityEnabled(context)
        actEnabled = prefs.actEnabled
        probeEnabled = prefs.probeEnabled
        nextCount = prefs.nextCount
        lastEvent = prefs.lastEvent
        targetApps = inspectTargetApps(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshState()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(runtime) {
        nextCount = prefs.nextCount
        lastEvent = prefs.lastEvent
    }

    val primaryApp = targetApps.firstOrNull()

    Scaffold(containerColor = BgWarmPaper) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderBar(serviceEnabled = serviceEnabled, actEnabled = actEnabled)

            HeroStatusCard(
                serviceEnabled = serviceEnabled,
                actEnabled = actEnabled,
                runtime = runtime,
                primaryApp = primaryApp,
                onOpenAccessibility = onOpenAccessibility,
                onToggleAct = { enabled ->
                    prefs.actEnabled = enabled
                    actEnabled = enabled
                },
                onLaunchApp = { intent -> context.startActivity(intent) },
            )

            MetricsAndLiveRow(
                nextCount = nextCount,
                serviceEnabled = serviceEnabled,
                runtime = runtime,
                lastEvent = lastEvent,
                onResetCount = {
                    prefs.resetStats()
                    nextCount = prefs.nextCount
                    lastEvent = prefs.lastEvent
                },
            )

            SupportedAppsSection(
                apps = targetApps,
                onLaunch = { intent -> context.startActivity(intent) },
            )

            SettingsCard(
                actEnabled = actEnabled,
                probeEnabled = probeEnabled,
                onActChange = {
                    prefs.actEnabled = it
                    actEnabled = it
                },
                onProbeChange = {
                    prefs.probeEnabled = it
                    probeEnabled = it
                },
                onOpenAccessibility = onOpenAccessibility,
            )

            ExpandableGuideCard(
                title = "工作原理与隐私承诺",
                subtitle = "三步自动衔接 · 零网络权限 100% 本地运行",
                defaultExpanded = false,
            ) {
                GuideStepRow("01", "识别剧间广告", "读取短剧窗口中的广告标签、倒计时与穿山甲/优量汇等广告 SDK 节点特征，正常剧集绝不误判。")
                GuideStepRow("02", "确认播放结束", "监测倒计时归零、消失或出现「重播 / 已获得奖励」等结束信号，并等待 0.8 秒稳定期。")
                GuideStepRow("03", "模拟垂直上划", "仅发送一次垂直上划手势进入下一集（最多重试 1 次），绝不点击广告区域或下载按钮。")
            }

            ExpandableGuideCard(
                title = "三星 / 国产系统后台防杀指南",
                subtitle = "避免息屏或长时观看后无障碍服务被系统回收",
                defaultExpanded = false,
            ) {
                GuideStepRow("A", "电池设为「不受限制」", "长按桌面本应用图标 → 应用信息 → 电池 → 选择「不受限制」。")
                GuideStepRow("B", "允许后台自启动", "在系统管家中开启本应用的自启动/后台运行权限，并在最近任务列表中锁定。")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeaderBar(serviceEnabled: Boolean, actEnabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_dramanext),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "短剧自动下一集",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                text = "广告播完自动上划 · 不点广告不误触",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        val (pillBg, pillFg, pillText) = when {
            !serviceEnabled -> Triple(BrandTerracottaSoft, BrandTerracotta, "未开启")
            actEnabled -> Triple(ActiveEmeraldSoft, ActiveEmerald, "守护中")
            else -> Triple(PausedAmberSoft, PausedAmber, "仅检测")
        }
        Surface(
            color = pillBg,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, pillFg.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(pillFg),
                )
                Text(
                    text = pillText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = pillFg,
                )
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    serviceEnabled: Boolean,
    actEnabled: Boolean,
    runtime: RuntimeSnapshot,
    primaryApp: InstalledTargetApp?,
    onOpenAccessibility: () -> Unit,
    onToggleAct: (Boolean) -> Unit,
    onLaunchApp: (Intent) -> Unit,
) {
    val bgColor: Color
    val borderColor: Color
    val accentColor: Color
    val headline: String
    val body: String

    when {
        !serviceEnabled -> {
            bgColor = CardWhite
            borderColor = BrandTerracottaBorder
            accentColor = BrandTerracotta
            headline = "开启无障碍服务以启动自动换集"
            body = "仅在红果短剧前台运行时检测剧间广告是否播完，并在结束后自动向上划入下一集。"
        }
        actEnabled -> {
            bgColor = ActiveEmeraldSoft
            borderColor = ActiveEmeraldBorder
            accentColor = ActiveEmerald
            headline = "自动下一集正在守护"
            body = "当检测到红果短剧插播广告结束时，将延迟 0.8 秒自动上划到下一集，全程不触碰广告。"
        }
        else -> {
            bgColor = PausedAmberSoft
            borderColor = PausedAmberBorder
            accentColor = PausedAmber
            headline = "当前处于「仅检测」调试模式"
            body = "无障碍服务已连接，后台会识别广告并记录日志，但已暂停自动上划动作。"
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.14f),
                ) {
                    Icon(
                        imageVector = when {
                            !serviceEnabled -> Icons.Default.Warning
                            actEnabled -> Icons.Default.CheckCircle
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(8.dp).size(22.dp),
                    )
                }
                Text(
                    text = when {
                        !serviceEnabled -> "第一步 · 授予系统无障碍权限"
                        actEnabled -> "状态正常 · ${runtime.stageLabel}"
                        else -> "已暂停划动 · ${runtime.stageLabel}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp,
            )

            if (!serviceEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgWarmPaper,
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "授权步骤：进入系统「无障碍」→「已安装的应用程序」→ 找到「短剧自动下一集」并开启。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                Button(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTerracotta),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("去开启无障碍服务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!actEnabled) {
                        Button(
                            onClick = { onToggleAct(true) },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PausedAmber),
                        ) {
                            Text("恢复自动下一集", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    } else if (primaryApp?.launchIntent != null) {
                        Button(
                            onClick = { onLaunchApp(primaryApp.launchIntent) },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActiveEmerald),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("打开${primaryApp.rule.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (actEnabled) onToggleAct(false)
                            else if (primaryApp?.launchIntent != null) onLaunchApp(primaryApp.launchIntent)
                            else onOpenAccessibility()
                        },
                        modifier = Modifier.heightIn(min = 50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    ) {
                        Text(
                            text = if (actEnabled) "暂停划动" else if (primaryApp?.launchIntent != null) "打开红果" else "系统设置",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsAndLiveRow(
    nextCount: Int,
    serviceEnabled: Boolean,
    runtime: RuntimeSnapshot,
    lastEvent: String,
    onResetCount: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("累计自动换集", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        if (nextCount > 0) {
                            Text(
                                text = "清零",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandTerracotta,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onResetCount() }.padding(2.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = nextCount.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            text = "次",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        text = if (nextCount == 0) "等待遇到剧间插播广告" else "均通过垂直上划完成",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("识别引擎状态", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(
                        text = if (!serviceEnabled) "服务未开启" else runtime.stageLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (serviceEnabled) ActiveEmerald else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (!serviceEnabled) "需开启系统无障碍" else runtime.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "最近动态",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Text(
                    text = lastEvent.ifEmpty { "暂无触发记录" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SupportedAppsSection(
    apps: List<InstalledTargetApp>,
    onLaunch: (Intent) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已适配短剧应用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = "${apps.count { it.installedPkg != null }} / ${apps.size} 已安装",
                    style = MaterialTheme.typography.labelMedium,
                    color = ActiveEmerald,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            apps.forEach { app ->
                val installed = app.installedPkg != null
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgWarmPaper,
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrandTerracottaSoft,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = app.rule.name.take(1),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandTerracotta,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = app.rule.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                    Surface(
                                        color = if (installed) ActiveEmeraldSoft else SurfaceSubtle,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = if (installed) "已安装" else "未安装",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (installed) ActiveEmerald else TextMuted,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                val sub = buildString {
                                    if (!app.versionName.isNullOrBlank()) append("v${app.versionName} · ")
                                    append(app.installedPkg ?: app.rule.packages.first())
                                }
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (app.launchIntent != null) {
                                FilledTonalButton(
                                    onClick = { onLaunch(app.launchIntent) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = BrandTerracottaSoft,
                                        contentColor = BrandTerracotta,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text("启动", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("穿山甲/优量汇", "倒计时归零", "结束自动上划").forEach { tag ->
                                Surface(
                                    color = CardWhite,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    actEnabled: Boolean,
    probeEnabled: Boolean,
    onActChange: (Boolean) -> Unit,
    onProbeChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = "功能与调试策略",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SettingSwitchRow(
                title = "广告结束后自动下一集",
                subtitle = "关闭后仅在后台识别广告状态，不执行上划手势",
                checked = actEnabled,
                onCheckedChange = onActChange,
            )

            HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))

            SettingSwitchRow(
                title = "记录界面探针日志 (DNProbe)",
                subtitle = "保存无障碍节点特征到本地文件，便于排查新广告样式",
                checked = probeEnabled,
                onCheckedChange = onProbeChange,
            )

            HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAccessibility() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "系统无障碍服务设置",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "查看或重新开关系统无障碍授权",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = "前往设置",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandTerracotta,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ActiveEmerald,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceSubtle,
            ),
        )
    }
}

@Composable
private fun ExpandableGuideCard(
    title: String,
    subtitle: String,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
            if (expanded) {
                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(horizontal = 18.dp))
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun GuideStepRow(badge: String, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BrandTerracottaSoft,
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BrandTerracotta,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val me = ComponentName(context, AutoNextService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

private fun inspectTargetApps(context: Context): List<InstalledTargetApp> {
    val pm = context.packageManager
    return AppRules.ALL.map { rule ->
        var foundPkg: String? = null
        var version: String? = null
        var launch: Intent? = null
        for (pkg in rule.packages) {
            val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() ?: continue
            foundPkg = pkg
            version = info.versionName
            launch = pm.getLaunchIntentForPackage(pkg)
            break
        }
        InstalledTargetApp(
            rule = rule,
            installedPkg = foundPkg,
            versionName = version,
            launchIntent = launch,
        )
    }
}
