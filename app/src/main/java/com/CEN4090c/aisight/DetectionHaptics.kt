package com.CEN4090c.aisight

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Three phone haptic patterns based on the tracker's EXPERIMENTAL distance estimate.
 *
 * 1 short pulse: a newly confirmed object, unknown distance, or > 3 m.
 * 2 short pulses: an estimated 1.5–3 m away.
 * 3 pronounced pulses: an estimated <= 1.5 m away.
 *
 * Each tracked object alerts at discovery and when it first moves into a higher
 * proximity tier. It never buzzes on every frame. If multiple tracks qualify,
 * the most urgent one wins. These are *not* verified obstacle warnings: the
 * underlying camera model uses placeholder calibration and assumed object sizes.
 */
class DetectionHaptics(context: Context) {
    companion object {
        private const val CLOSE_DISTANCE_M = 1.5f
        private const val NEAR_DISTANCE_M = 3.0f
        private const val GENERAL_COOLDOWN_MS = 1500L
        private const val CLOSE_COOLDOWN_MS = 650L

        // Alternating vibration timings in milliseconds: wait, buzz, wait, buzz...
        private val DETECTED_PATTERN = longArrayOf(0L, 120L)
        private val NEAR_PATTERN = longArrayOf(0L, 110L, 130L, 110L)
        private val CLOSE_PATTERN = longArrayOf(0L, 220L, 110L, 220L, 110L, 220L)
    }

    private enum class Level(val urgency: Int) {
        DETECTED(1), NEAR(2), CLOSE(3)
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    // Highest tier already announced for each confirmed track in the active view.
    private val announcedLevels = mutableMapOf<Int, Level>()
    private var lastAlertTimeMs = -GENERAL_COOLDOWN_MS

    private fun levelOf(track: TrackedObject): Level {
        val meters = track.distanceMeters
            ?.takeIf { it.isFinite() && it > 0f }
            ?: return Level.DETECTED
        return when {
            meters <= CLOSE_DISTANCE_M -> Level.CLOSE
            meters <= NEAR_DISTANCE_M -> Level.NEAR
            else -> Level.DETECTED
        }
    }

    @Synchronized
    fun onTrackedObjects(objects: List<TrackedObject>, frameTimeMs: Long = SystemClock.elapsedRealtime()) {
        val activeIds = objects.map { it.id }.toSet()
        announcedLevels.keys.retainAll(activeIds)

        // Only a newly confirmed track, or escalation on an existing track, alerts.
        val eligible = objects.mapNotNull { track ->
            val level = levelOf(track)
            val previouslyAnnounced = announcedLevels[track.id]
            if (previouslyAnnounced == null || level.urgency > previouslyAnnounced.urgency) {
                track.id to level
            } else null
        }
        if (eligible.isEmpty()) return

        val topLevel = eligible.maxByOrNull { it.second.urgency }!!.second
        val interval = if (topLevel == Level.CLOSE) CLOSE_COOLDOWN_MS else GENERAL_COOLDOWN_MS
        if (frameTimeMs - lastAlertTimeMs < interval) return
        if (vibrator?.hasVibrator() != true) return

        val pattern = when (topLevel) {
            Level.DETECTED -> DETECTED_PATTERN
            Level.NEAR -> NEAR_PATTERN
            Level.CLOSE -> CLOSE_PATTERN
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        lastAlertTimeMs = frameTimeMs

        // Mark the tracks whose level was included in this alert. We do not mark
        // lower-priority tracks as announced; they can still alert later.
        eligible.filter { it.second == topLevel }.forEach { (id, level) ->
            announcedLevels[id] = level
        }
    }

    @Synchronized
    fun reset() {
        announcedLevels.clear()
        lastAlertTimeMs = -GENERAL_COOLDOWN_MS
    }
}
