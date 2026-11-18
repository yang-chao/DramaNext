package com.dramanext.app

/**
 * Turns a stream of [AdState] observations into at most a couple of "go to next episode" actions
 * per ad. Pure and clock-injected so it can be unit tested.
 */
class AutoNextEngine(
    private val settleMs: Long = 800,
    /** If a countdown was seen and then disappears for this long, treat the ad as finished. */
    private val countdownGoneMs: Long = 1500,
    /** Fallback: countdown predicted the end and that moment has passed by this margin. */
    private val countdownOverrunMs: Long = 2500,
    private val retryIntervalMs: Long = 2500,
    private val maxSwipesPerAd: Int = 2,
) {
    enum class Action { NONE, NEXT }

    data class Decision(val action: Action, val reason: String)

    private var sessionStart: Long? = null
    private var lastCountdownAt: Long? = null
    private var predictedEnd: Long? = null
    private var finishedSince: Long? = null
    private var swipes = 0
    private var lastSwipeAt = 0L

    val inAdSession: Boolean get() = sessionStart != null

    fun reset() {
        sessionStart = null; lastCountdownAt = null; predictedEnd = null
        finishedSince = null; swipes = 0; lastSwipeAt = 0L
    }

    fun onState(state: AdState, now: Long): Decision {
        when (state) {
            AdState.NotAd -> {
                val wasInAd = inAdSession
                reset()
                return Decision(Action.NONE, if (wasInAd) "left ad" else "")
            }
            is AdState.Playing -> {
                if (sessionStart == null) sessionStart = now
                if (state.remainingSec != null) {
                    lastCountdownAt = now
                    predictedEnd = now + state.remainingSec * 1000L
                    finishedSince = null
                    if (state.remainingSec > 0) return Decision(Action.NONE, "playing ${state.remainingSec}s")
                }
                val cdAt = lastCountdownAt
                val end = predictedEnd
                val gone = cdAt != null && now - cdAt >= countdownGoneMs
                val overrun = end != null && now - end >= countdownOverrunMs
                if (!gone && !overrun) return Decision(Action.NONE, "playing")
                if (finishedSince == null) finishedSince = now - settleMs // inferred end: no extra settle
                return maybeNext(now, if (overrun) "countdown overrun" else "countdown gone")
            }
            is AdState.Finished -> {
                if (sessionStart == null) sessionStart = now
                if (finishedSince == null) finishedSince = now
                return maybeNext(now, "finished: ${state.evidence}")
            }
        }
    }

    private fun maybeNext(now: Long, reason: String): Decision {
        val since = finishedSince ?: return Decision(Action.NONE, reason)
        if (now - since < settleMs) return Decision(Action.NONE, "settling")
        if (swipes >= maxSwipesPerAd) return Decision(Action.NONE, "gave up after $swipes")
        if (swipes > 0 && now - lastSwipeAt < retryIntervalMs) return Decision(Action.NONE, "waiting after swipe")
        swipes++
        lastSwipeAt = now
        return Decision(Action.NEXT, reason)
    }
}
