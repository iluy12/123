package com.iluy.imutest

/**
 * מנוע-הזיהוי הטהור של צרור-דפיקות — מנותק מ-Service/Activity/חיישנים,
 * כדי שאפשר להשתמש בו גם ב-TapDetectorService (רקע, טריגר אמיתי) וגם
 * במסך-התרגול בשאלון (חזית, לאישור-בלבד שהזיהוי עובד). כל השכבות
 * שנבנו ותועדו קודם ב-TapDetectorService גרות כאן עכשיו במקור אחד,
 * כדי ששני המקומות לעולם לא יתדרדרו להתנהגות שונה בטעות.
 */
class TapClusterDetector(
    private val onLog: (level: String, message: String) -> Unit,
    private val onPatternDetected: (spikeCount: Int) -> Unit
) {
    private val recentSpikes = ArrayDeque<Long>()
    private var referenceMagnitude: Double? = null
    private var referencePulseSamples: Int? = null
    private var sustainedMotionStartMs: Long? = null
    private var lastSpikeAboveThresholdMs: Long = 0L
    private var suppressed = false
    private var lastMagnitude: Double? = null
    private var lastAcceptedSpikeMs: Long = 0L
    private var consecutiveAboveThresholdSamples = 0

    /** worn-gating מוזרק מבחוץ — ברירת מחדל "לבוש" (לא חוסם). */
    var worn: Boolean = true

    /** אם false, worn לא נבדק בכלל (למשל במסך-תרגול בחזית — ברור שהמכשיר ביד). */
    var wornGatingActive: Boolean = false

    fun onAccelerometerSample(x: Float, y: Float, z: Float, now: Long) {
        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())

        val prevMagnitude = lastMagnitude
        lastMagnitude = magnitude

        val aboveThreshold = magnitude > DebugConfig.TAP_MAGNITUDE_THRESHOLD
        val jumpedSuddenly = prevMagnitude != null &&
            Math.abs(magnitude - prevMagnitude) > DebugConfig.TAP_MIN_DELTA

        if (aboveThreshold) {
            consecutiveAboveThresholdSamples++
            trackSustainedMotion(now)

            val debounced = now - lastAcceptedSpikeMs < DebugConfig.TAP_REFRACTORY_MS
            when {
                suppressed -> { /* מושתק בגלל תנועה רציפה */ }
                debounced -> onLog("DEBUG", "tap_candidate_rejected_refractory")
                !jumpedSuddenly -> { /* אין קפיצה חדה — כנראה המשך אותו פולס */ }
                else -> evaluateCandidate(now, magnitude, consecutiveAboveThresholdSamples)
            }
        } else {
            consecutiveAboveThresholdSamples = 0
            sustainedMotionStartMs = null
            if (suppressed) {
                suppressed = false
                onLog("INFO", "tap_detection_resumed_after_stillness")
            }
        }
    }

    private fun trackSustainedMotion(now: Long) {
        if (sustainedMotionStartMs == null || now - lastSpikeAboveThresholdMs > 1_500L) {
            sustainedMotionStartMs = now
        }
        lastSpikeAboveThresholdMs = now
        val sustainedFor = now - (sustainedMotionStartMs ?: now)
        if (sustainedFor > DebugConfig.SUSTAINED_MOTION_SUPPRESS_MS && !suppressed) {
            suppressed = true
            onLog("INFO", "tap_detection_suppressed_sustained_motion")
        }
    }

    private fun evaluateCandidate(now: Long, magnitude: Double, pulseSamples: Int) {
        if (pulseSamples > DebugConfig.TAP_MAX_CONSECUTIVE_ABOVE_THRESHOLD_SAMPLES) {
            onLog("DEBUG", "tap_candidate_rejected_sustained_pulse")
            return
        }

        if (wornGatingActive && !worn) {
            onLog("DEBUG", "tap_candidate_rejected_not_worn")
            return
        }

        if (recentSpikes.isNotEmpty() && now - recentSpikes.last() > DebugConfig.TAP_MAX_INTERVAL_MS) {
            onLog("DEBUG", "tap_cluster_reset_interval_too_long")
            recentSpikes.clear()
            referenceMagnitude = null
            referencePulseSamples = null
        }

        if (recentSpikes.isEmpty()) {
            referenceMagnitude = magnitude
            referencePulseSamples = pulseSamples
            acceptIntoCluster(now)
            return
        }

        val refMag = referenceMagnitude ?: magnitude
        val refPulse = referencePulseSamples ?: pulseSamples
        val magDiffRatio = if (refMag > 0) Math.abs(magnitude - refMag) / refMag else 0.0
        val pulseDiff = Math.abs(pulseSamples - refPulse)

        if (magDiffRatio > DebugConfig.TAP_SIMILARITY_MAGNITUDE_TOLERANCE ||
            pulseDiff > DebugConfig.TAP_SIMILARITY_PULSE_TOLERANCE_SAMPLES
        ) {
            onLog(
                "DEBUG",
                "tap_candidate_rejected_dissimilar_from_first;magDiff=${"%.2f".format(magDiffRatio)};pulseDiff=$pulseDiff"
            )
            return
        }

        acceptIntoCluster(now)
    }

    private fun acceptIntoCluster(now: Long) {
        lastAcceptedSpikeMs = now
        recentSpikes.addLast(now)
        while (recentSpikes.isNotEmpty() && now - recentSpikes.first() > DebugConfig.TAP_WINDOW_MS) {
            recentSpikes.removeFirst()
        }

        if (recentSpikes.size >= DebugConfig.TAP_COUNT_THRESHOLD) {
            if (isRhythmRegular(recentSpikes)) {
                val count = recentSpikes.size
                recentSpikes.clear()
                referenceMagnitude = null
                referencePulseSamples = null
                onPatternDetected(count)
            } else {
                onLog("DEBUG", "tap_candidate_rejected_irregular")
                recentSpikes.removeFirst()
            }
        }
    }

    private fun isRhythmRegular(spikes: ArrayDeque<Long>): Boolean {
        if (spikes.size < 3) return true
        val gaps = spikes.zipWithNext { a: Long, b: Long -> (b - a).toDouble() }
        val mean = gaps.average()
        val variance = gaps.sumOf { (it - mean) * (it - mean) } / gaps.size
        val stddev = Math.sqrt(variance)
        return stddev <= DebugConfig.TAP_RHYTHM_MAX_STDDEV_MS
    }
}
