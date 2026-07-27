package com.iluy.imutest

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * לוג מקומי פשוט לכל אירועי המערכת — טריגר, תוצאה, כלי שנבחר. זהו המקבילה
 * המקומית ל-event_log שמתוכנן לשרת בעתיד (סעיף 13 במסמך המסירה), אבל בלי
 * שרת: קובץ CSV בזיכרון הפנימי של האפליקציה בלבד.
 *
 * משמש גם את תג-הדיבאג (סעיף 12): כל שורה כאן היא בדיוק המידע שמוצג
 * למשתמש-הבודק על המסך ברגע הטריגר.
 */
object EventLog {

    private const val LOG_FILE_NAME = "iluy_events.csv"
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun log(context: Context, eventType: String, detail: String) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        val ts = fmt.format(Date())
        val line = "$ts,$eventType,$detail"
        try {
            FileWriter(file, true).use { it.write(line + "\n") }
        } catch (e: Exception) {
            // לוג-דיבאג בלבד — לא קריטי אם כתיבה בודדת נכשלת.
        }
    }

    fun readAll(context: Context): List<String> {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines()
    }

    fun readLastN(context: Context, n: Int): List<String> =
        readAll(context).takeLast(n)
}
