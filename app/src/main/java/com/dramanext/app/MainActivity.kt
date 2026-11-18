package com.dramanext.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var openA11y: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun text(s: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 2, 0, pad / 2)
        }.also { col.addView(it) }

        text(getString(R.string.app_name), 26f, bold = true)
        text("广告播完后自动划到下一集。只在支持的短剧 App 内工作，不点击、不跳过广告。", 14f)
        status = text("", 18f, bold = true)
        openA11y = Button(this).apply {
            text = "去开启无障碍服务"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }.also { col.addView(it) }

        Switch(this).apply {
            text = "广告结束后自动下一集"
            textSize = 16f
            isChecked = prefs.actEnabled
            setOnCheckedChangeListener { _, v -> prefs.actEnabled = v }
            setPadding(0, pad, 0, pad / 2)
        }.also { col.addView(it) }
        text("关闭后只检测、不划动（用于调试）", 12f)

        Switch(this).apply {
            text = "记录界面探针日志（调试）"
            textSize = 16f
            isChecked = prefs.probeEnabled
            setOnCheckedChangeListener { _, v -> prefs.probeEnabled = v }
            setPadding(0, pad, 0, pad / 2)
        }.also { col.addView(it) }

        stats = text("", 15f)
        text("支持：" + AppRules.ALL.joinToString("、") { it.name }, 13f)

        Button(this).apply {
            text = "打开红果短剧"
            setOnClickListener {
                val pkg = AppRules.HONGGUO.packages.firstNotNullOfOrNull { p ->
                    packageManager.getLaunchIntentForPackage(p)
                }
                if (pkg != null) startActivity(pkg) else stats.text = "未安装红果短剧"
            }
        }.also { col.addView(it) }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = if (on) "● 无障碍服务已开启" else "○ 无障碍服务未开启"
        status.setTextColor(if (on) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
        openA11y.visibility = if (on) View.GONE else View.VISIBLE
        stats.text = "已自动下一集：${prefs.nextCount} 次\n最近：${prefs.lastEvent.ifEmpty { "—" }}"
        stats.gravity = Gravity.START
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, AutoNextService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
