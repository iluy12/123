package com.iluy.imutest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * שירות שרץ ברקע תמיד (כולל מסך כבוי) ומזהה צרור-דפיקות על גוף השעון.
 * לוגיקת-הזיהוי עצמה גרה ב-TapClusterDetector (משותפת גם עם מסך-התרגול
 * בשאלון) — השירות הזה רק מזין אותה מהחיישנים ומטפל בתוצאה (התראה/
 * RISK A, קירור, "היכון" שעתי).
 *
 * שכבות ההגנה מפני false positives — מלא ב-TapClusterDetector, תיעוד
 * מלא שם. worn-gating (זיהוי אם השעון על היד) גר כאן, כי הוא תלוי-חיישן
 * ולא חלק מהלוגיקה הטהורה.
 */
class TapDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // --- worn-gating ---
    private var offBodySensor: Sensor? = null
    private var heartRateSensor: Sensor? = null
    private var wornSensorAvailable = false

    private var isListening = false
    private lateinit var detector: TapClusterDetector

    companion object {
        const val CHANNEL_ID = "iluy_tap_service_channel"
        const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, TapDetectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TapDetectorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        detector = TapClusterDetector(
            onLog = { level, message -> EventLog.log(this, level, message) },
            onPatternDetected = { count -> onTapPatternDetected(count) }
        )

        setupWornGating()
    }

    private fun setupWornGating() {
        detector.wornGatingActive = false
        if (!DebugConfig.WORN_GATING_ENABLED) return

        offBodySensor = sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        if (offBodySensor != null) {
            wornSensorAvailable = true
            detector.wornGatingActive = true
            EventLog.log(this, "INFO", "worn_gating_using_offbody_sensor")
            return
        }

        val hasBodySensorPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasBodySensorPermission) {
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (heartRateSensor != null) {
                wornSensorAvailable = true
                detector.wornGatingActive = true
                EventLog.log(this, "INFO", "worn_gating_using_heart_rate_sensor")
                return
            }
        }

        wornSensorAvailable = false
        EventLog.log(this, "INFO", "worn_gating_unavailable_defaulting_to_worn")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!isListening) {
            val sensor = accelerometer
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                offBodySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
                heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
                isListening = true
                EventLog.log(this, "INFO", "tap_service_started")
            } else {
                EventLog.log(this, "ERROR", "no_accelerometer_sensor_found")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        isListening = false
        EventLog.log(this, "INFO", "tap_service_stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                detector.onAccelerometerSample(
                    event.values[0], event.values[1], event.values[2], System.currentTimeMillis()
                )
            }
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> {
                detector.worn = (event.values.getOrNull(0) ?: 1f) >= 0.5f
            }
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values.getOrNull(0) ?: 0f
                detector.worn = hr > 0f
            }
        }
    }

    private fun onTapPatternDetected(spikeCount: Int) {
        val now = System.currentTimeMillis()

        if (now < LocalStore.getCooldownUntil(this)) {
            EventLog.log(this, "INFO", "tap_ignored_cooldown_active")
            return
        }

        val debugDetail = "הקשה ($spikeCount זוהו, קצב-דלת)"
        val standbyUntil = LocalStore.getTapStandbyUntil(this)

        if (now < standbyUntil) {
            EventLog.log(this, "TRIGGER", "tap_second_in_hour;$debugDetail")
            RiskFlowActivity.launch(
                this,
                source = debugDetail,
                variant = RiskFlowActivity.VARIANT_SECOND_TAP_IN_HOUR
            )
        } else {
            LocalStore.setTapStandbyUntil(this, now + DebugConfig.STANDBY_DURATION_MS)
            EventLog.log(this, "TRIGGER", "tap_first_in_hour;$debugDetail")
            sendVideoNotification(debugDetail)
        }
    }

    private fun sendVideoNotification(debugDetail: String) {
        val intent = Intent(this, VideoPlaceholderActivity::class.java).apply {
            putExtra(VideoPlaceholderActivity.EXTRA_SOURCE, debugDetail)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 1, intent, flags)

        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "iluy_video_channel", "עילוי — הודעות", NotificationManager.IMPORTANCE_HIGH
            )
            nm?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "iluy_video_channel")
            .setContentTitle("עילוי שלחו לך סרטון")
            .setContentText("לחץ לצפייה")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm?.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "עילוי — שירות פעיל", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("עילוי פעיל")
            .setContentText("איתך ברקע")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* not used */ }
}
