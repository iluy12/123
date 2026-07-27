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
 * תפריט סיוע — זהה בכל הכניסות (סעיף 9): חיזקי, תקשיב לעצמך, סרטון חיזוק,
 * עצות, חיוג מיידי.
 */
class HelpMenuActivity : Activity() {

    companion object {
        const val EXTRA_SOURCE = "extra_source"

        fun launch(context: Context, source: String) {
            val intent = Intent(context, HelpMenuActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    private lateinit var source: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: "לא ידוע"
        EventLog.log(this, "HELP_MENU_SHOWN", "source=$source")
        setContentView(buildLayout())
    }

    private fun buildLayout(): View {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        scroll.addView(container)

        if (DebugConfig.DEBUG_TAG_ENABLED) {
            container.addView(TextView(this).apply {
                text = "הופעל ע\"י: $source"
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
            })
        }

        container.addView(TextView(this).apply {
            text = "איך אפשר לעזור עכשיו?"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })

        container.addView(menuButton("חיזקי") {
            EventLog.log(this, "HELP_TOOL", "chizki;source=$source")
            PlaceholderActivity.launch(
                this, title = "חיזקי",
                message = "כאן תהיה התכתבות עם בוט (placeholder ל-v1)."
            )
        })

        container.addView(menuButton("תקשיב לעצמך") {
            EventLog.log(this, "HELP_TOOL", "listen_to_yourself;source=$source")
            playPersonalRecording()
        })

        container.addView(menuButton("סרטון חיזוק") {
            EventLog.log(this, "HELP_TOOL", "encouragement_video;source=$source")
            PlaceholderActivity.launch(
                this, title = "סרטון חיזוק",
                message = "כאן יופיע סרטון חיזוק (placeholder ל-v1)."
            )
        })

        container.addView(menuButton("עצות") {
            EventLog.log(this, "HELP_TOOL", "tips;source=$source")
            showTips()
        })

        container.addView(menuButton("חיוג מיידי", primary = true) {
            EventLog.log(this, "HELP_TOOL", "immediate_call;source=$source")
            CallHelper.startCall(this, source = "חיוג מיידי (מ: $source)")
        })

        return scroll
    }

    private fun playPersonalRecording() {
        val path = LocalStore.getRecordingPath(this)
        if (path.isNullOrBlank() || !java.io.File(path).exists()) {
            PlaceholderActivity.launch(
                this, title = "תקשיב לעצמך",
                message = "עדיין לא הקלטת הודעה אישית. אפשר להוסיף אחת דרך השאלון."
            )
            return
        }
        try {
            val player = android.media.MediaPlayer()
            player.setDataSource(path)
            player.prepare()
            player.start()
            android.widget.Toast.makeText(this, "מנגן את ההקלטה שלך…", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "לא הצלחתי לנגן את ההקלטה", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTips() {
        val userTips = LocalStore.getMultiChoice(this, LocalStore.KEY_Q5_HELPS)
        val genericTips = listOf(
            "צא להליכה קצרה, גם רק סביב הבית",
            "שתה כוס מים לאט",
            "תן ל-5 דקות לעבור לפני שתחליט משהו",
            "התקשר לחבר או חברותא"
        )
        val allTips = (userTips + genericTips).distinct()
        val text = if (allTips.isEmpty()) "אין עדיין עצות שמורות." else allTips.joinToString("\n• ", prefix = "• ")
        PlaceholderActivity.launch(this, title = "עצות", message = text)
    }

    private fun menuButton(label: String, primary: Boolean = false, onClick: () -> Unit): Button =
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
            minHeight = 110
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 14
            layoutParams = lp
            setOnClickListener { onClick() }
        }
}
