package com.iluy.imutest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * פופאפ RISK A — "רגע איתך. הכל טוב?" (סעיף 9 במסמך המסירה).
 *
 * עקרון-על שאסור לשבור: המשתמש תמיד בוחר בעצמו. שום הסלמה אוטומטית,
 * גם בגרסת "פעם שנייה בשעה" — רק הניסוח משתנה, לא חופש הבחירה.
 */
class RiskFlowActivity : Activity() {

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_VARIANT = "extra_variant"
        const val VARIANT_NORMAL = "normal"
        const val VARIANT_SECOND_TAP_IN_HOUR = "second_tap_in_hour"

        fun launch(context: Context, source: String, variant: String = VARIANT_NORMAL) {
            val intent = Intent(context, RiskFlowActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_VARIANT, variant)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    private lateinit var source: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertHelper.wakeScreen(this)
        AlertHelper.playAlertSound(this)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: "לא ידוע"
        val variant = intent.getStringExtra(EXTRA_VARIANT) ?: VARIANT_NORMAL

        EventLog.log(this, "RISK_A_SHOWN", "source=$source;variant=$variant")

        val root = buildLayout(variant)
        setContentView(root)
    }

    private fun buildLayout(variant: String): View {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg_white))
        }
        scroll.addView(container)

        if (DebugConfig.DEBUG_TAG_ENABLED) {
            container.addView(debugTag("הופעל ע\"י: $source"))
        }

        val title = if (variant == VARIANT_SECOND_TAP_IN_HOUR)
            "זו כבר פעם שנייה בשעה האחרונה. בוא נצא מזה."
        else
            "רגע איתך. הכל טוב?"

        container.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })

        container.addView(bigButton("הכל טוב") { onAllGood() })
        container.addView(bigButton("עייף / עצבני") { onNeedsHelp("עייף/עצבני") })
        container.addView(bigButton("אני צריך עזרה") { onNeedsHelp("אני צריך עזרה") })
        container.addView(bigButton("אני צריך לדבר עכשיו", primary = true) { onNeedsToTalkNow() })

        return scroll
    }

    private fun onAllGood() {
        EventLog.log(this, "RISK_A_RESULT", "all_good;source=$source")
        LocalStore.setCooldownUntil(this, System.currentTimeMillis() + DebugConfig.COOLDOWN_MS)
        android.widget.Toast.makeText(this, "שמח לשמוע. אני כאן אם תצטרך.", android.widget.Toast.LENGTH_LONG).show()
        finish()
    }

    private fun onNeedsHelp(moodLabel: String) {
        EventLog.log(this, "RISK_A_RESULT", "needs_help;$moodLabel;source=$source")
        HelpMenuActivity.launch(this, source = "$source → $moodLabel")
        finish()
    }

    private fun onNeedsToTalkNow() {
        // "הניסוח עצמו = הסכמה, בלי אישור כפול" — ישר ל-RISK B, בלי מסך ביניים
        EventLog.log(this, "RISK_A_RESULT", "needs_to_talk_now;source=$source")
        CallHelper.startCall(this, source = "אני צריך לדבר עכשיו (מ: $source)")
        finish()
    }

    private fun debugTag(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.WHITE)
        setBackgroundColor(ContextCompat.getColor(context, R.color.danger))
        setPadding(12, 6, 12, 6)
        gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = 20
        layoutParams = lp
    }

    private fun bigButton(label: String, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (primary) R.color.emerald_primary else R.color.emerald_primary_hover
                )
            )
            textSize = 15f
            minHeight = 130
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 16
            layoutParams = lp
            setOnClickListener { onClick() }
        }
}
