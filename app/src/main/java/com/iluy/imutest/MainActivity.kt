package com.iluy.imutest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * מסך הבית. "שום דבר על מסך השעון הראשי לא חושף כלום" (סעיף 10) — זה
 * מתייחס ל-watch face של המכשיר עצמו, לא למסך הזה שבתוך האפליקציה; שני
 * הכפתורים (מצב-רוח, נפלתי) מותרים כאן כי הם *בתוך* האפליקציה, לא גלויים
 * כברירת מחדל על השעון.
 */
class MainActivity : Activity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!LocalStore.isQuestionnaireDone(this)) {
            startActivity(Intent(this, QuestionnaireActivity::class.java))
            finish()
            return
        }

        // worn-gating: אם יש חיישן דופק אבל אין הרשאה עדיין — מבקשים.
        // אם המשתמש ידחה, TapDetectorService פשוט לא יחסום כלום לפי זה
        // (נופל בחזרה ל"תמיד נחשב לבוש") — לא קריטי לתפקוד הבסיסי.
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.BODY_SENSORS), 701
            )
        }

        // השאלון כבר הושלם — מפעילים את שירות זיהוי ההקשה ברקע באופן קבוע
        TapDetectorService.start(this)

        renderHome()
    }

    private fun renderHome() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 28)
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "עילוי"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6)
        })
        container.addView(TextView(this).apply {
            text = "פעיל ברקע"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        container.addView(bigButton("מצב-רוח") {
            startActivity(Intent(this, MoodPickerActivity::class.java))
        })

        container.addView(bigButton("נפלתי", primary = true) {
            EventLog.log(this, "TRIGGER", "fell_button;note=no_physio_buffer_v1")
            RiskFlowActivity.launch(this, source = "כפתור 'נפלתי'")
        })

        container.addView(secondaryButton("מלא את השאלון מחדש") {
            LocalStore.setQuestionnaireDone(this, false)
            startActivity(Intent(this, QuestionnaireActivity::class.java))
            finish()
        })

        if (DebugConfig.DEBUG_TAG_ENABLED) {
            container.addView(TextView(this).apply {
                text = "מצב בדיקה (v1)"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                setPadding(0, 32, 0, 6)
            })
            container.addView(secondaryButton("הצג לוג אירועים ← העתק ללוח") {
                showAndCopyLog()
            })
        }

        setContentView(scroll)
    }

    private fun showAndCopyLog() {
        val lines = EventLog.readAll(this)
        val full = lines.joinToString("\n")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("iluy_events", full))
        Toast.makeText(this, "כל הלוג הועתק ללוח (${lines.size} שורות)", Toast.LENGTH_LONG).show()
    }

    private fun bigButton(label: String, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(android.graphics.Color.WHITE)
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
            lp.topMargin = 12
            layoutParams = lp
            setOnClickListener { onClick() }
        }

    private fun secondaryButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 8
            layoutParams = lp
            setOnClickListener { onClick() }
        }
}
