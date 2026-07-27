package com.iluy.imutest

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * השאלון המלא — סעיף 11 במסמך המסירה. בחירה בלבד בכל השאלות, אין שדה
 * טקסט חופשי בשום מקום (השאלה היחידה עם קלט "חופשי" היא הקלטה קולית,
 * לא טקסט). שאלה 5 (מה עוזר לך) מוזנת ישירות לתפריט הסיוע כברירת מחדל.
 *
 * ניווט: שלושה שלבים — QUESTIONS (0..totalSteps-1) → INSTRUCTION
 * (תרגול-דפיקה) → RECORDING (הקלטה אישית). אפשר לחזור אחורה מכל שלב,
 * ואפשר למלא את השאלון מחדש מהמסך הראשי (MainActivity).
 */
class QuestionnaireActivity : Activity() {

    private enum class Phase { QUESTIONS, INSTRUCTION, RECORDING }

    private var phase = Phase.QUESTIONS
    private var step = 0
    private val totalSteps = 9

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var isRecording = false
    private lateinit var recordingFile: File

    // --- תרגול-דפיקה (שלב ה-INSTRUCTION) ---
    private var practiceDetector: TapClusterDetector? = null
    private var practiceSensorManager: SensorManager? = null
    private var practiceListener: SensorEventListener? = null
    private var practiceCaptured = false
    private val practiceHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_RECORD_AUDIO = 601
        private const val PRACTICE_TIMEOUT_MS = 8_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordingFile = File(filesDir, "personal_recording.3gp")
        renderStep()
    }

    override fun onPause() {
        stopPractice()
        super.onPause()
    }

    private fun renderStep() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "שלב ${step + 1} מתוך $totalSteps"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            gravity = Gravity.CENTER
        })

        when (step) {
            0 -> renderMultiChoice(
                container, "מתי זה בד\"כ קורה? (בחירה מרובה)",
                listOf("בוקר", "צהריים", "לילה"),
                LocalStore.KEY_Q1_TIMES
            )
            1 -> renderSingleChoice(
                container, "כל כמה זמן?",
                listOf("כל יום", "כמה פעמים בשבוע", "פעם בשבוע", "כל שבועיים", "כל חודש", "כל חודשיים"),
                LocalStore.KEY_Q2_FREQUENCY
            )
            2 -> renderSingleChoice(
                container, "אותו חדר בכל פעם, או משתנה?",
                listOf("אותו חדר", "משתנה"),
                LocalStore.KEY_Q3_PLACE
            )
            3 -> renderMultiChoice(
                container, "באיזו תנוחה בד\"כ? (בחירה מרובה)",
                listOf("יושב", "שוכב", "עומד"),
                LocalStore.KEY_Q3_POSITION
            )
            4 -> renderMultiChoice(
                container, "מה בד\"כ מקדים את זה? (בחירה מרובה)",
                listOf("שיעמום", "עצבנות", "עצבות", "לחץ", "עייפות", "בדידות"),
                LocalStore.KEY_Q4_MOODS
            )
            5 -> renderMultiChoice(
                container, "מה עוזר לך לצאת מזה? (בחירה מרובה)",
                listOf("הליכה קצרה", "שתיית מים", "שיחה עם חבר", "תפילה", "לימוד", "מוזיקה", "נשימות עמוקות", "יציאה מהחדר"),
                LocalStore.KEY_Q5_HELPS
            )
            6 -> renderSingleChoice(
                container, "כמה זמן זה בד\"כ נמשך?",
                listOf("0–3 דקות", "3–7 דקות", "7–12 דקות", "מעל 12 דקות"),
                LocalStore.KEY_Q6_DURATION
            )
            7 -> renderConsent(
                container, "בסדר שאתקשר מדי פעם?",
                LocalStore.KEY_Q7_CONSENT_CALL
            )
            8 -> renderConsent(
                container, "בסדר שאשלח הודעה מדי פעם?",
                LocalStore.KEY_Q8_CONSENT_MESSAGE
            )
        }

        addBackButtonIfApplicable(container)
        setContentView(scroll)
    }

    // ---------- רינדור סוגי שאלות ----------
    // כל שאלה טוענת תשובה קודמת אם קיימת — כדי שחזרה-אחורה לא תמחק בחירה.

    private fun renderMultiChoice(container: LinearLayout, title: String, options: List<String>, key: String) {
        addTitle(container, title)
        val previouslySelected = LocalStore.getMultiChoice(this, key).toSet()
        val checkBoxes = mutableListOf<CheckBox>()
        for (opt in options) {
            val cb = CheckBox(this).apply {
                text = opt
                textSize = 15f
                setPadding(0, 10, 0, 10)
                isChecked = previouslySelected.contains(opt)
            }
            checkBoxes.add(cb)
            container.addView(cb)
        }
        addNextButton(container) {
            val chosen = checkBoxes.filter { it.isChecked }.map { it.text.toString() }
            LocalStore.saveMultiChoice(this, key, chosen)
            goForward()
        }
    }

    private fun renderSingleChoice(container: LinearLayout, title: String, options: List<String>, key: String) {
        addTitle(container, title)
        val previouslySelected = LocalStore.getSingleChoice(this, key)
        val group = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        for (opt in options) {
            val rb = RadioButton(this).apply {
                text = opt
                textSize = 15f
                setPadding(0, 10, 0, 10)
                isChecked = opt == previouslySelected
            }
            group.addView(rb)
        }
        container.addView(group)
        addNextButton(container) {
            val checkedId = group.checkedRadioButtonId
            val chosen = if (checkedId != -1) group.findViewById<RadioButton>(checkedId).text.toString() else ""
            LocalStore.saveSingleChoice(this, key, chosen)
            goForward()
        }
    }

    private fun renderConsent(container: LinearLayout, title: String, key: String) {
        addTitle(container, title)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val yes = Button(this).apply {
            text = "כן"
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                LocalStore.saveBoolean(this@QuestionnaireActivity, key, true)
                goForward()
            }
        }
        val no = Button(this).apply {
            text = "לא"
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = 12
            layoutParams = lp
            setOnClickListener {
                LocalStore.saveBoolean(this@QuestionnaireActivity, key, false)
                goForward()
            }
        }
        row.addView(yes)
        row.addView(no)
        container.addView(row)
    }

    /**
     * הוראת-הדפיקה: "דפוק כמו על דלת" — עכשיו אינטראקטיבית, לא רק טקסט.
     * בדיוק כמו שלב ההקלטה: קודם לוחצים "התחל", ואז זה הזמן לדפוק, ויש
     * אישור-קליטה לפני שממשיכים — לא עוד ניחוש "מתי בדיוק צריך לדפוק".
     */
    private fun renderKnockInstructionStep() {
        practiceCaptured = false
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        scroll.addView(container)

        addTitle(container, "איך לדווח")
        container.addView(TextView(this).apply {
            text = "בכל פעם שהתגברת על ניסיון — דפוק על גוף השעון עצמו, " +
                "כמו שהיית דופק בדלת של מישהו שבאת לבקר אצלו.\n\n" +
                "3–4 דפיקות, בקצב טבעי — לא לחפז ולא להאט במיוחד."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 0, 0, 20)
        })

        val statusText = TextView(this).apply {
            text = "לחץ \"התחל תרגול\", ואז דפוק על גוף השעון"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        container.addView(statusText)

        lateinit var startButton: Button
        val nextButton = Button(this).apply {
            text = "המשך"
            isEnabled = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp
            setOnClickListener {
                stopPractice()
                phase = Phase.RECORDING
                renderRecordingStep()
            }
        }

        startButton = Button(this).apply {
            text = "▶ התחל תרגול"
            setOnClickListener {
                isEnabled = false
                statusText.text = "מקשיב… דפוק עכשיו"
                startPractice(statusText, nextButton, this)
            }
        }
        container.addView(startButton)
        container.addView(nextButton)

        addBackButtonIfApplicable(container)
        setContentView(scroll)
    }

    private fun startPractice(statusText: TextView, nextButton: Button, startButton: Button) {
        stopPractice()

        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            statusText.text = "לא נמצא חיישן תנועה על המכשיר הזה — אפשר להמשיך בלי תרגול"
            nextButton.isEnabled = true
            return
        }

        val detector = TapClusterDetector(
            onLog = { _, _ -> /* לא רושמים ללוג בתרגול — זו לא בדיקת-אמת */ },
            onPatternDetected = {
                runOnUiThread {
                    practiceCaptured = true
                    statusText.text = "✓ נקלט! זוהתה דפיקה"
                    nextButton.isEnabled = true
                    stopPractice()
                }
            }
        )
        detector.wornGatingActive = false // בתרגול המכשיר ודאי ביד — אין טעם לחסום

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    detector.onAccelerometerSample(
                        event.values[0], event.values[1], event.values[2], System.currentTimeMillis()
                    )
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        practiceDetector = detector
        practiceSensorManager = sm
        practiceListener = listener

        practiceHandler.postDelayed({
            if (!practiceCaptured) {
                statusText.text = "לא זוהה — אפשר לנסות שוב או להמשיך בכל זאת"
                nextButton.isEnabled = true
                startButton.isEnabled = true
                stopPractice()
            }
        }, PRACTICE_TIMEOUT_MS)
    }

    private fun stopPractice() {
        practiceHandler.removeCallbacksAndMessages(null)
        practiceListener?.let { practiceSensorManager?.unregisterListener(it) }
        practiceListener = null
        practiceSensorManager = null
        practiceDetector = null
    }

    private fun renderRecordingStep() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        scroll.addView(container)

        addTitle(container, "הקלטה אישית — למה אני לא רוצה בזה")
        container.addView(TextView(this).apply {
            text = "ההקלטה נשמעת לך בלבד, ברגע קשה. מוקלטת עכשיו, ברגע רגוע — לא בזמן אמת."
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 0, 0, 20)
        })

        val statusText = TextView(this).apply {
            text = if (recordingFile.exists()) "יש הקלטה שמורה" else "אין עדיין הקלטה"
            textSize = 13f
            gravity = Gravity.CENTER
        }
        container.addView(statusText)

        val recordButton = Button(this)
        recordButton.text = "🎙 הקלט"
        recordButton.setOnClickListener {
            if (!isRecording) startRecording(recordButton, statusText) else stopRecording(recordButton, statusText)
        }
        container.addView(recordButton)

        container.addView(Button(this).apply {
            text = "▶ נגן"
            setOnClickListener { playRecording() }
        })

        addNextButton(container, label = "סיום השאלון") {
            if (recordingFile.exists()) {
                LocalStore.setRecordingPath(this, recordingFile.absolutePath)
            }
            LocalStore.setQuestionnaireDone(this, true)
            EventLog.log(this, "INFO", "questionnaire_completed")
            MainActivity.start(this)
            finish()
        }

        addBackButtonIfApplicable(container)
        setContentView(scroll)
    }

    private fun startRecording(button: Button, status: TextView) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO
            )
            return
        }
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(recordingFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            button.text = "⏹ עצור"
            status.text = "מקליט…"
        } catch (e: Exception) {
            status.text = "לא הצלחתי להתחיל הקלטה"
        }
    }

    private fun stopRecording(button: Button, status: TextView) {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            // ייתכן שההקלטה הייתה קצרה מדי — מתעלמים, הקובץ פשוט לא יהיה תקין
        }
        recorder = null
        isRecording = false
        button.text = "🎙 הקלט מחדש"
        status.text = "הקלטה נשמרה"
    }

    private fun playRecording() {
        if (!recordingFile.exists()) return
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(recordingFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // אין מה לעשות אם הניגון נכשל — לא קריטי לשאלון
        }
    }

    // ---------- ניווט ----------

    private fun addTitle(container: LinearLayout, title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        })
    }

    private fun addNextButton(container: LinearLayout, label: String = "המשך", onClick: () -> Unit) {
        container.addView(Button(this).apply {
            text = label
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 24
            layoutParams = lp
            setOnClickListener { onClick() }
        })
    }

    private fun addBackButtonIfApplicable(container: LinearLayout) {
        val isVeryFirstStep = phase == Phase.QUESTIONS && step == 0
        if (isVeryFirstStep) return
        container.addView(Button(this).apply {
            text = "← חזרה"
            textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 8
            layoutParams = lp
            setOnClickListener { goBack() }
        })
    }

    private fun goForward() {
        when (phase) {
            Phase.QUESTIONS -> {
                if (step < totalSteps - 1) {
                    step++
                    renderStep()
                } else {
                    phase = Phase.INSTRUCTION
                    renderKnockInstructionStep()
                }
            }
            Phase.INSTRUCTION -> {
                phase = Phase.RECORDING
                renderRecordingStep()
            }
            Phase.RECORDING -> { /* מטופל ישירות בכפתור "סיום השאלון" */ }
        }
    }

    private fun goBack() {
        stopPractice()
        when (phase) {
            Phase.QUESTIONS -> {
                if (step > 0) {
                    step--
                    renderStep()
                }
            }
            Phase.INSTRUCTION -> {
                phase = Phase.QUESTIONS
                step = totalSteps - 1
                renderStep()
            }
            Phase.RECORDING -> {
                phase = Phase.INSTRUCTION
                renderKnockInstructionStep()
            }
        }
    }

    override fun onDestroy() {
        stopPractice()
        recorder?.release()
        player?.release()
        super.onDestroy()
    }
}
