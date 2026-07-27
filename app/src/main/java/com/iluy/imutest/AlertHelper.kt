package com.iluy.imutest

import android.app.Activity
import android.content.Context
import android.media.RingtoneManager
import android.view.WindowManager

/**
 * תיקון שהיה שמור: צליל + הדלקת מסך בכל טריגר/פופאפ (RISK A וכו').
 * בלי זה, אם המסך כבוי כשנפתח הטריגר, המשתמש עלול לא לשים לב בכלל.
 */
object AlertHelper {

    /** מדליק את המסך ומעביר מעל מסך-נעילה אם צריך, גם אם השעון היה כבוי-מסך. */
    @Suppress("DEPRECATION")
    fun wakeScreen(activity: Activity) {
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    /** מנגן את צליל-ההתראה הדיפולטיבי של המכשיר. לא צריך קובץ-סאונד משלנו. */
    fun playAlertSound(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (e: Exception) {
            // אם אין ברירת-מחדל/נכשל הניגון — לא קריטי, המסך עדיין נדלק
        }
    }
}
