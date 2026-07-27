package com.iluy.imutest

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * כפתור "מצב-רוח" (סעיף 8-9 במסמך): לחיצה ישירה בתוך האפליקציה (לא גלוי
 * על מסך השעון). המשתמש בוחר מה הוא מרגיש, ואז ישר ל-RISK A.
 *
 * "מכריח Active מלא ל-30 דק', עוקף חיסכון סוללה" — שמור כהערה ל-v2 (תלוי
 * בהפרדת Sleep/Active/Moving עם חיישן פיזיולוגי, לא רלוונטי ל-v1).
 */
class MoodPickerActivity : Activity() {

    private val moods = listOf("שיעמום", "עצבנות", "עצבות", "לחץ", "עייפות", "בדידות")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "מה אתה מרגיש עכשיו?"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })

        for (mood in moods) {
            container.addView(Button(this).apply {
                text = mood
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 10
                layoutParams = lp
                setOnClickListener { onMoodSelected(mood) }
            })
        }

        setContentView(scroll)
    }

    private fun onMoodSelected(mood: String) {
        EventLog.log(this, "TRIGGER", "mood_button;mood=$mood")
        RiskFlowActivity.launch(this, source = "כפתור מצב-רוח ($mood)")
        finish()
    }
}
