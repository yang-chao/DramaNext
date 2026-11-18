package com.dramanext.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDetectorTest {
    private val rule = AppRules.HONGGUO
    private fun n(text: String? = null, desc: String? = null, id: String? = null) = Node(text, desc, id, "android.widget.TextView")

    @Test fun normalEpisodeIsNotAd() {
        val s = AdDetector.classify(listOf(n("第12集"), n("选集"), n("开通会员免广告")), rule)
        assertEquals(AdState.NotAd, s)
    }

    @Test fun adTagWithCountdownIsPlaying() {
        val s = AdDetector.classify(listOf(n("广告"), n("12s后可继续观看"), n("立即下载")), rule)
        assertTrue(s is AdState.Playing)
        assertEquals(12, (s as AdState.Playing).remainingSec)
    }

    @Test fun skipCountdownForms() {
        assertEquals(5, AdDetector.findCountdown(listOf("跳过 5"), rule)?.first)
        assertEquals(3, AdDetector.findCountdown(listOf("3s | 跳过"), rule)?.first)
        assertEquals(15, AdDetector.findCountdown(listOf("15秒"), rule)?.first)
        assertEquals(null, AdDetector.findCountdown(listOf("第15集"), rule))
    }

    @Test fun ctaAloneIsNotAd() {
        assertEquals(AdState.NotAd, AdDetector.classify(listOf(n("查看详情")), rule))
    }

    @Test fun sdkViewIdPlusCtaIsAd() {
        val s = AdDetector.classify(listOf(n("立即下载", id = "com.x:id/tt_reward_ad_download")), rule)
        assertTrue(s is AdState.Playing)
    }

    @Test fun replayMeansFinished() {
        val s = AdDetector.classify(listOf(n("广告"), n("重播"), n("立即下载")), rule)
        assertTrue(s is AdState.Finished)
    }
}

class AutoNextEngineTest {
    private val playing = AdState.Playing(5, "t")
    private val noCd = AdState.Playing(null, "t")

    @Test fun finishedSettlesThenSwipesOnce() {
        val e = AutoNextEngine(settleMs = 800)
        assertEquals(AutoNextEngine.Action.NONE, e.onState(AdState.Finished("x"), 0).action)
        assertEquals(AutoNextEngine.Action.NONE, e.onState(AdState.Finished("x"), 500).action)
        assertEquals(AutoNextEngine.Action.NEXT, e.onState(AdState.Finished("x"), 900).action)
        assertEquals(AutoNextEngine.Action.NONE, e.onState(AdState.Finished("x"), 1500).action)
        assertEquals(AutoNextEngine.Action.NEXT, e.onState(AdState.Finished("x"), 3500).action)
        assertEquals(AutoNextEngine.Action.NONE, e.onState(AdState.Finished("x"), 9000).action) // max 2
    }

    @Test fun countdownDisappearingTriggersNext() {
        val e = AutoNextEngine()
        assertEquals(AutoNextEngine.Action.NONE, e.onState(playing, 0).action)
        assertEquals(AutoNextEngine.Action.NONE, e.onState(noCd, 1000).action)
        assertEquals(AutoNextEngine.Action.NEXT, e.onState(noCd, 1600).action)
    }

    @Test fun adWithoutCountdownNeverTriggersOnItsOwn() {
        val e = AutoNextEngine()
        for (t in 0L..60_000L step 500) assertEquals(AutoNextEngine.Action.NONE, e.onState(noCd, t).action)
    }

    @Test fun leavingAdResets() {
        val e = AutoNextEngine()
        e.onState(AdState.Finished("x"), 0)
        e.onState(AdState.NotAd, 100)
        assertEquals(false, e.inAdSession)
    }
}
