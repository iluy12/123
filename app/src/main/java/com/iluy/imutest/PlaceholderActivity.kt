package com.iluy.imutest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * מסך-דמה כללי לכלים שעדיין לא מיושמים ב-v1 (חיזקי, סרטון חיזוק) וגם
 * לתצוגת עצות. כל טקסט placeholder מסומן ככזה בבירור בקוד.
 */
class PlaceholderActivity : Activity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"

        fun launch(context: Context, title: String, message: String) {
            val intent = Intent(context, PlaceholderActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = title
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })
        container.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        container.addView(Button(this).apply {
            text = "חזרה"
            setPadding(0, 40, 0, 0)
            setOnClickListener { finish() }
        })

        setContentView(scroll)
    }
}
