package com.iluy.imutest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // רק אם השאלון כבר הושלם — אחרת עדיפה פתיחה יזומה של האפליקציה קודם
            if (LocalStore.isQuestionnaireDone(context)) {
                TapDetectorService.start(context)
                EventLog.log(context, "INFO", "boot_auto_start")
            }
        }
    }
}
