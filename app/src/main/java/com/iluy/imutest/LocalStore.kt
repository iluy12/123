package com.iluy.imutest

import android.content.Context
import android.content.SharedPreferences

/**
 * כל האחסון של v1 מקומי בלבד — SharedPreferences על השעון עצמו. אין שרת,
 * אין רשת, אין דאטה שיוצא מהמכשיר (ראו סעיף 12 במסמך המסירה: "לא צריך
 * שרת! רק אחסון מקומי על השעון").
 *
 * ריבוי-בחירה (checkboxes) נשמר כמחרוזת מופרדת בפסיקים — פשוט מספיק
 * למשתמש בודד, בלי צורך בספריית JSON חיצונית.
 */
object LocalStore {

    private const val PREFS_NAME = "iluy_v1_store"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- שאלון ----------

    fun isQuestionnaireDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_Q_DONE, false)

    fun setQuestionnaireDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_Q_DONE, done).apply()
    }

    fun saveMultiChoice(context: Context, key: String, values: List<String>) {
        prefs(context).edit().putString(key, values.joinToString(",")).apply()
    }

    fun getMultiChoice(context: Context, key: String): List<String> {
        val raw = prefs(context).getString(key, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    fun saveSingleChoice(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getSingleChoice(context: Context, key: String): String =
        prefs(context).getString(key, "") ?: ""

    fun saveBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    // ---------- מצב הקשה / "היכון" לשעה ----------

    /** אם עדיין בתוקף (עתיד) — השעון כבר ב"היכון" מהקשה קודמת באותה שעה. */
    fun getTapStandbyUntil(context: Context): Long =
        prefs(context).getLong(KEY_TAP_STANDBY_UNTIL, 0L)

    fun setTapStandbyUntil(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_TAP_STANDBY_UNTIL, epochMillis).apply()
    }

    // ---------- קירור אחרי "הכל טוב" ----------

    fun getCooldownUntil(context: Context): Long =
        prefs(context).getLong(KEY_COOLDOWN_UNTIL, 0L)

    fun setCooldownUntil(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_COOLDOWN_UNTIL, epochMillis).apply()
    }

    // ---------- הקלטה אישית (שלב 9 בשאלון) ----------

    fun setRecordingPath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_RECORDING_PATH, path).apply()
    }

    fun getRecordingPath(context: Context): String? =
        prefs(context).getString(KEY_RECORDING_PATH, null)

    // ---------- מפתחות שאלון ----------

    const val KEY_Q1_TIMES = "q1_times"
    const val KEY_Q2_FREQUENCY = "q2_frequency"
    const val KEY_Q3_PLACE = "q3_place"
    const val KEY_Q3_POSITION = "q3_position"
    const val KEY_Q4_MOODS = "q4_moods"
    const val KEY_Q5_HELPS = "q5_helps"
    const val KEY_Q6_DURATION = "q6_duration"
    const val KEY_Q7_CONSENT_CALL = "q7_consent_call"
    const val KEY_Q8_CONSENT_MESSAGE = "q8_consent_message"

    private const val KEY_Q_DONE = "questionnaire_done"
    private const val KEY_TAP_STANDBY_UNTIL = "tap_standby_until"
    private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
    private const val KEY_RECORDING_PATH = "recording_path"
}
