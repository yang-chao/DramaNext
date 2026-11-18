package com.dramanext.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeSnapshot(
    val connected: Boolean = false,
    val activeRuleName: String? = null,
    val stageLabel: String = "空闲待命",
    val detail: String = "等待进入短剧应用",
    val updatedAt: String = "",
)

class AutoNextService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val engine = AutoNextEngine()
    private lateinit var prefs: Prefs
    private lateinit var probe: ProbeLog

    private var processScheduled = false
    private var lastProcessAt = 0L
    private var lastSignature = 0
    private var lastProbeAt = 0L
    private var lastStateKey = ""
    private var lastTargetSeenAt = 0L
    private var nextAttempt = 0

    private val processRunnable = Runnable { processScheduled = false; process("event") }
    private val pollRunnable = object : Runnable {
        override fun run() {
            process("poll")
            // Keep polling while inside a target app; video playback alone may not emit events.
            if (SystemClock.uptimeMillis() - lastTargetSeenAt < POLL_KEEPALIVE_MS) {
                handler.postDelayed(this, if (engine.inAdSession) 700 else 1500)
            } else polling = false
        }
    }
    private var polling = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        probe = ProbeLog(File(filesDir, "probe.log"))
        log("service connected; rules=${AppRules.ALL_PACKAGES}")
        prefs.recordEvent("服务已连接 ${now()}")
        _runtime.value = RuntimeSnapshot(
            connected = true,
            stageLabel = "服务已就绪",
            detail = "等待进入短剧应用",
            updatedAt = now(),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in AppRules.ALL_PACKAGES) return
        lastTargetSeenAt = SystemClock.uptimeMillis()
        if (!polling) { polling = true; handler.postDelayed(pollRunnable, 1000) }
        if (processScheduled) return
        processScheduled = true
        val wait = (MIN_PROCESS_GAP_MS - (SystemClock.uptimeMillis() - lastProcessAt)).coerceIn(150, MIN_PROCESS_GAP_MS)
        handler.postDelayed(processRunnable, wait)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        _runtime.value = RuntimeSnapshot(connected = false, stageLabel = "服务未连接", detail = "请在无障碍设置中开启")
        super.onDestroy()
    }

    private class Snapshot(val pkg: String, val rule: AppRule, val nodes: List<Node>, val infos: List<AccessibilityNodeInfo>)

    private fun process(trigger: String) {
        lastProcessAt = SystemClock.uptimeMillis()
        val snap = snapshot()
        if (snap == null) {
            if (engine.inAdSession) log("target app not in foreground; reset")
            engine.reset(); nextAttempt = 0
            return
        }
        lastTargetSeenAt = SystemClock.uptimeMillis()
        val state = AdDetector.classify(snap.nodes, snap.rule)
        maybeProbe(snap, state, trigger)

        val decision = engine.onState(state, SystemClock.uptimeMillis())
        _runtime.value = when (state) {
            AdState.NotAd -> RuntimeSnapshot(true, snap.rule.name, "播放正常剧集", "未检测到广告 (${snap.nodes.size} 节点)", now())
            is AdState.Playing -> RuntimeSnapshot(
                true,
                snap.rule.name,
                state.remainingSec?.let { "广告播放中 (${it}s)" } ?: "广告播放中",
                state.evidence,
                now(),
            )
            is AdState.Finished -> RuntimeSnapshot(true, snap.rule.name, "广告已结束", state.evidence, now())
        }
        val key = state::class.simpleName + "|" + decision.reason.substringBefore(':')
        if (key != lastStateKey) {
            lastStateKey = key
            log("state=$state decision=${decision.action} reason=${decision.reason}")
        }
        if (state == AdState.NotAd) nextAttempt = 0
        if (decision.action == AutoNextEngine.Action.NEXT) {
            if (prefs.actEnabled) {
                goNext(snap, decision.reason)
            } else {
                log("DRY-RUN would go next: ${decision.reason}")
                prefs.recordEvent("[仅检测] 广告结束 ${now()}")
            }
        }
    }

    private fun snapshot(): Snapshot? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        var pkg: String? = null
        val ws = try { windows } catch (_: Exception) { emptyList<AccessibilityWindowInfo>() }
        val active = ws.firstOrNull { it.isActive } ?: ws.firstOrNull { it.isFocused }
        val activePkg = active?.root?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString()
        if (activePkg == null || activePkg !in AppRules.ALL_PACKAGES) return null
        for (w in ws) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val r = w.root ?: continue
            if (r.packageName?.toString() == activePkg) { roots += r; pkg = activePkg }
        }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots += it; pkg = activePkg }
        val rule = AppRules.forPackage(pkg) ?: return null
        val nodes = ArrayList<Node>(256)
        val infos = ArrayList<AccessibilityNodeInfo>(256)
        val rect = Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= MAX_NODES || depth > MAX_DEPTH) return
            n.getBoundsInScreen(rect)
            nodes += Node(
                text = n.text?.toString(), desc = n.contentDescription?.toString(),
                viewId = n.viewIdResourceName, className = n.className?.toString(),
                visible = n.isVisibleToUser, clickable = n.isClickable, scrollable = n.isScrollable,
                left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom,
            )
            infos += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        roots.forEach { walk(it, 0) }
        return Snapshot(pkg!!, rule, nodes, infos)
    }

    private fun goNext(snap: Snapshot, reason: String) {
        nextAttempt++
        // Primary: vertical swipe gesture (matches manual swipe, avoids horizontal ViewPagers).
        // Fallback on retry: explicit vertical scroll-down action on the innermost pager.
        val method: String
        if (nextAttempt == 1) {
            swipeUp()
            method = "gesture"
        } else {
            val pagerIdx = findVerticalPager(snap)
            if (pagerIdx >= 0 && snap.infos[pagerIdx].performAction(android.R.id.accessibilityActionScrollDown)) {
                method = "scroll(${snap.nodes[pagerIdx].className?.substringAfterLast('.')})"
            } else {
                swipeUp()
                method = "gesture"
            }
        }
        log("NEXT via $method (attempt $nextAttempt): $reason")
        prefs.recordNext("广告结束→下一集 [$method] ${now()}")
        Toast.makeText(this, "广告已结束，自动下一集", Toast.LENGTH_SHORT).show()
    }

    /** Innermost visible container that explicitly supports vertical scroll-down. */
    private fun findVerticalPager(snap: Snapshot): Int {
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels
        var best = -1
        var bestArea = 0L
        snap.nodes.forEachIndexed { i, n ->
            if (n.scrollable && n.visible && n.area >= bestArea && n.area > screenArea / 2) {
                val canDown = snap.infos[i].actionList.any {
                    it.id == android.R.id.accessibilityActionScrollDown
                }
                if (canDown) { best = i; bestArea = n.area }
            }
        }
        return best
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val path = Path().apply {
            moveTo(x, dm.heightPixels * 0.72f)
            lineTo(x, dm.heightPixels * 0.25f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 280))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun maybeProbe(snap: Snapshot, state: AdState, trigger: String) {
        if (!prefs.probeEnabled) return
        val sig = snap.nodes.filter { it.visible }.fold(17) { acc, n -> 31 * acc + (n.viewId + "|" + n.label).hashCode() }
        val nowMs = SystemClock.uptimeMillis()
        if (sig == lastSignature || nowMs - lastProbeAt < PROBE_GAP_MS) return
        lastSignature = sig
        lastProbeAt = nowMs
        val sb = StringBuilder()
        sb.append("==== ").append(now()).append(" pkg=").append(snap.pkg).append(" trigger=").append(trigger)
            .append(" nodes=").append(snap.nodes.size).append(" state=").append(state).append('\n')
        snap.nodes.forEach { n ->
            val interesting = !n.text.isNullOrBlank() || !n.desc.isNullOrBlank() || n.viewId != null || n.scrollable
            if (!interesting) return@forEach
            sb.append(if (n.visible) "V " else "- ")
                .append(n.className?.substringAfterLast('.')).append(' ')
                .append(n.viewId?.substringAfter(":id/") ?: "").append(' ')
                .append(n.text?.let { "t=\"${it.replace('\n', ' ')}\" " } ?: "")
                .append(n.desc?.let { "d=\"${it.replace('\n', ' ')}\" " } ?: "")
                .append("[${n.left},${n.top},${n.right},${n.bottom}]")
                .append(if (n.clickable) " C" else "").append(if (n.scrollable) " S" else "")
                .append('\n')
        }
        val dump = sb.toString()
        dump.lines().forEach { if (it.isNotEmpty()) Log.d(PROBE_TAG, it) }
        probe.append(dump)
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        if (::probe.isInitialized) probe.append("## ${now()} $msg\n")
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    companion object {
        const val TAG = "DramaNext"
        const val PROBE_TAG = "DNProbe"
        private const val MIN_PROCESS_GAP_MS = 400L
        private const val PROBE_GAP_MS = 1000L
        private const val POLL_KEEPALIVE_MS = 15_000L
        private const val MAX_NODES = 1500
        private const val MAX_DEPTH = 80
        private val _runtime = MutableStateFlow(RuntimeSnapshot())
        val runtime: StateFlow<RuntimeSnapshot> = _runtime.asStateFlow()
    }
}

/** Small append-only log with a size cap, readable via `adb shell run-as`. */
class ProbeLog(private val file: File, private val maxBytes: Long = 4L * 1024 * 1024) {
    fun append(s: String) {
        try {
            if (file.length() > maxBytes) {
                val old = File(file.parentFile, file.name + ".1")
                old.delete(); file.renameTo(old)
            }
            file.appendText(s)
        } catch (_: Exception) {
        }
    }
}
