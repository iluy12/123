package com.iluy.imutest

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * RISK B ל-v1: מחייג לקו-הבדיקה שלך (DebugConfig.TEST_LINE_PHONE_NUMBER),
 * לא למרכזייה האמיתית (סעיף 12: "חיוג לקו-בדיקה שלך, לא למרכזייה
 * האמיתית"). כשעוברים לגרסת ייצור — להחליף למספר המרכזייה האמיתי.
 */
object CallHelper {

    private const val REQUEST_CALL_PERMISSION = 501

    fun startCall(activity: Activity, source: String) {
        EventLog.log(activity, "RISK_B_CALL", "source=$source;number=${DebugConfig.TEST_LINE_PHONE_NUMBER}")

        val hasPermission = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            placeCallDirectly(activity)
        } else {
            // אין הרשאה עדיין — פותחים חייגן עם המספר ממולא, המשתמש מאשר בעצמו.
            // גם מבקשים הרשאה לפעם הבאה.
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PERMISSION
            )
            openDialer(activity)
        }
    }

    private fun placeCallDirectly(activity: Activity) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${DebugConfig.TEST_LINE_PHONE_NUMBER}")
        }
        try {
            activity.startActivity(intent)
        } catch (e: SecurityException) {
            openDialer(activity)
        }
    }

    private fun openDialer(activity: Activity) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${DebugConfig.TEST_LINE_PHONE_NUMBER}")
        }
        activity.startActivity(intent)
    }
}
