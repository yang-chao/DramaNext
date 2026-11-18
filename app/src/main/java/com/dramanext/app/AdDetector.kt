package com.dramanext.app

/** A flattened, platform-independent view of one accessibility node. */
data class Node(
    val text: String?,
    val desc: String?,
    val viewId: String?,
    val className: String?,
    val visible: Boolean = true,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val label: String get() = listOfNotNull(text, desc).joinToString(" ").trim()
    val area: Long get() = (right - left).toLong().coerceAtLeast(0) * (bottom - top).toLong().coerceAtLeast(0)
}

sealed interface AdState {
    data object NotAd : AdState
    /** An ad is on screen and still playing; [remainingSec] is the visible countdown, if any. */
    data class Playing(val remainingSec: Int?, val evidence: String) : AdState
    /** An ad is on screen and has signalled that it finished. */
    data class Finished(val evidence: String) : AdState
}

/** Per-app detection rules. Kept data-only so they can later be loaded from JSON. */
data class AppRule(
    val name: String,
    val packages: Set<String>,
    /** Short "tag" labels that by themselves mark a page as an ad (e.g. "广告"). Matched on the whole label. */
    val adTags: List<Regex>,
    /** View id fragments typical for ad SDK views (Pangle, GDT, Kuaishou...). */
    val adViewIds: List<Regex>,
    /** Call-to-action texts typical for ads; weak evidence on their own. */
    val ctaTexts: List<Regex>,
    /** Countdown matcher; group 1 must capture the remaining seconds. */
    val countdown: List<Regex>,
    /** Labels that only appear once the ad has finished. */
    val finishedTexts: List<Regex>,
    /** Labels that, if present, mean this is NOT an ad page (e.g. promotions for ad-free VIP). */
    val excludeTexts: List<Regex> = emptyList(),
)

object AdDetector {

    fun classify(nodes: List<Node>, rule: AppRule): AdState {
        val visible = nodes.filter { it.visible }
        val labels = visible.map { it.label }.filter { it.isNotEmpty() }

        if (labels.any { l -> rule.excludeTexts.any { it.containsMatchIn(l) } }) return AdState.NotAd

        val evidence = mutableListOf<String>()
        var score = 0

        labels.firstOrNull { l -> rule.adTags.any { it.matches(l) } }?.let { score += 2; evidence += "tag:$it" }
        visible.firstOrNull { n -> n.viewId != null && rule.adViewIds.any { it.containsMatchIn(n.viewId) } }
            ?.let { score += 2; evidence += "id:${it.viewId}" }
        labels.firstOrNull { l -> rule.ctaTexts.any { it.containsMatchIn(l) } }?.let { score += 1; evidence += "cta:$it" }

        val countdown = findCountdown(labels, rule)
        if (countdown != null) {
            score += 1
            evidence += "cd:${countdown.second}"
        }
        val finished = labels.firstOrNull { l -> rule.finishedTexts.any { it.containsMatchIn(l) } }
        if (finished != null) evidence += "fin:$finished"

        if (score < 2) return AdState.NotAd
        val ev = evidence.joinToString(", ")
        return when {
            finished != null -> AdState.Finished(ev)
            countdown != null && countdown.first <= 0 -> AdState.Finished(ev)
            else -> AdState.Playing(countdown?.first, ev)
        }
    }

    /** Returns (seconds, matched label) for the first countdown found. */
    fun findCountdown(labels: List<String>, rule: AppRule): Pair<Int, String>? {
        for (l in labels) {
            if (l.length > 40) continue
            for (r in rule.countdown) {
                val m = r.find(l) ?: continue
                val sec = m.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
                if (sec in 0..300) return sec to l
            }
        }
        return null
    }
}

object AppRules {
    private fun rx(vararg p: String) = p.map { Regex(it, RegexOption.IGNORE_CASE) }

    val HONGGUO = AppRule(
        name = "红果短剧",
        packages = setOf("com.phoenix.read", "com.phoenix.read.oversea.gp"),
        adTags = rx(
            """^[\[【(（]?\s*(广告|AD|推广|赞助)\s*[\]】)）]?$""",
            """^(广告|AD)\s*[|·•丨:]\s*.{0,20}$""",
            """^.{0,20}\s*[|·•丨]\s*广告$""",
        ),
        adViewIds = rx(
            """openadsdk""", """:id/tt_""", """pangle""", """com\.qq\.e""", """gdt_""", """ksad_""",
            """[:/_]ad_(label|tag|logo|title|desc|close|skip|count|countdown|cta|button|container|icon)""",
        ),
        ctaTexts = rx(
            "^立即下载$", "^查看详情$", "^了解更多$", "^立即打开$", "^立即咨询$", "^立即购买$",
            "^去看看$", "^立即领取$", "^去逛逛$", "^立即安装$", "^打开看看$", "^进入小程序$", "^立即体验$",
        ),
        countdown = rx(
            """(\d{1,3})\s*[sS秒]\s*(后|之后)?\s*(可|跳过|关闭|继续|领取|结束|进入)""",
            """(?:跳过|关闭|广告)\s*[|丨]?\s*(\d{1,3})\s*[sS秒]?""",
            """^(\d{1,3})\s*[sS秒]?\s*[|丨]?\s*(跳过|关闭)""",
            """^(\d{1,3})\s*[sS秒]$""",
            """倒计时\s*(\d{1,3})""",
        ),
        finishedTexts = rx(
            "重播", "再看一遍", "^继续观看", "已获得奖励", "奖励已(领取|发放)", "广告已结束", "已解锁", "^观看下一集",
        ),
        excludeTexts = rx("^免广告", "^去广告", "开通会员免广告"),
    )

    val ALL = listOf(HONGGUO)
    val ALL_PACKAGES: Set<String> = ALL.flatMap { it.packages }.toSet()

    fun forPackage(pkg: CharSequence?): AppRule? = pkg?.toString()?.let { p -> ALL.firstOrNull { p in it.packages } }
}
