package com.iluy.imutest

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * נפתח מההתראה "עילוי שלחו לך סרטון" (פעם ראשונה בשעה, הקשה — סעיף 9).
 * חובה: אין autoplay. המשתמש לוחץ "הפעל" בעצמו, גם אם זה placeholder ב-v1.
 */
class VideoPlaceholderActivity : Activity() {

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertHelper.wakeScreen(this)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "לא ידוע"
        EventLog.log(this, "VIDEO_OPENED", "source=$source")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 28)
        }

        val status = TextView(this).apply {
            text = "סרטון חיזוק ממתין לך"
            textSize = 16f
            gravity = Gravity.CENTER
        }
        container.addView(status)

        // כפתור הפעלה ידני בלבד — אין autoplay בשום מצב.
        container.addView(Button(this).apply {
            text = "▶ הפעל"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 24
            layoutParams = lp
            setOnClickListener {
                EventLog.log(this@VideoPlaceholderActivity, "VIDEO_PLAYED", "source=$source")
                status.text = "כאן יתנגן הסרטון בפועל (placeholder ל-v1)."
                isEnabled = false
            }
        })

        setContentView(container)
    }
}
