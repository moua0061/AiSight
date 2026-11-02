package com.CEN4090c.aisight

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/*
 * Multi-object tracker for AiSight.
 *
 * Tracking logic ported from ByteTrack as implemented in roboflow/trackers
 * (https://github.com/roboflow/trackers, Apache License 2.0), simplified for a phone:
 *   - per-track Kalman filter (constant velocity) on box center, width and height
 *   - two-stage matching: confident detections first, then weak ones to keep tracks alive
 *   - greedy IoU matching instead of the Hungarian algorithm (fine for < ~10 objects)
 *   - tracks only match detections with the same label
 *   - uses real timestamps, because detection runs at a variable rate
 *
 * Added for navigation:
 *   - distance estimate for objects of known size (pinhole camera model)
 *   - time to contact from how fast the box is growing ("looming")
 */

/** One tracked object, ready for the overlay and the audio/haptic alerts. */
data class TrackedObject(
    val id: Int,
    val label: String,
    val score: Float,
    val box: RectF,                 // smoothed box, frame pixels
    val direction: String,          // "left", "ahead", "right"
    val distanceMeters: Float?,     // null if the size is unknown or the box is cut off
    val timeToContactSec: Float?,   // null if not approaching
    val cutOff: Boolean,            // box touches the top or bottom edge (likely very close)
) {
    val approaching: Boolean get() = timeToContactSec != null
}

class ObjectTracker(
    /** Detections at or above this score can start tracks and match first. */
    private val highScore: Float = 0.5f,
    /** Minimum overlap for a detection to continue a track. */
    private val minIou: Float = 0.2f,
    /** Hits needed before a track gets an ID (filters one-frame flickers). */
    private val minHits: Int = 2,
    /** How long a track survives without being seen (brief occlusion). */
    private val lostTimeoutMs: Long = 1000,
    /**
     * Camera focal length in pixels, for the 504x896 stream.
     * PLACEHOLDER: calibrate it (see calibrateFocalPx) and replace this value.
     */
    private val focalPx: Float = 640f,
) {
    companion object {
        /** Typical real-world heights in meters. Add your own classes here. */
        val KNOWN_HEIGHTS_M = mapOf(
            "person" to 1.70f,
            "door" to 2.03f,
            "chair" to 0.90f,
            "bench" to 0.85f,
            "couch" to 0.85f,
            "dining table" to 0.75f,
            "bicycle" to 1.00f,
            "car" to 1.50f,
            "trash_can" to 0.80f,
            "fire hydrant" to 0.60f,
            "potted plant" to 0.60f,
        )

        /** Ignore growth slower than this (fraction of box height per second). */
        private const val MIN_GROWTH_PER_SEC = 0.05f
        private const val MAX_TTC_SEC = 15f
        private const val EDGE_MARGIN_PX = 4f

        /**
         * One-time calibration. Stand an object of known height at a measured distance,
         * read its box height from Logcat, and compute:
         *   focalPx = boxHeightPx * distanceMeters / realHeightMeters
         * Repeat a few times and average.
         */
        fun calibrateFocalPx(boxHeightPx: Float, distanceMeters: Float, realHeightMeters: Float) =
            boxHeightPx * distanceMeters / realHeightMeters
    }

    private val tracks = mutableListOf<Track>()
    private var nextId = 1
    private var lastTimeMs = -1L

    /** Feed every detection result here, with the time the frame arrived. */
    fun update(detections: FrameDetections, timeMs: Long): List<TrackedObject> {
        val dt = if (lastTimeMs < 0) 0f else ((timeMs - lastTimeMs) / 1000f).coerceIn(0f, 1f)
        lastTimeMs = timeMs

        // 1. Predict where every track should be now
        tracks.forEach { it.predict(dt) }

        val high = detections.objects.filter { it.score >= highScore }
        val low = detections.objects.filter { it.score < highScore }
        val matchedNow = mutableSetOf<Track>()

        // 2. Match confident detections to all tracks
        val unmatchedHigh = associate(tracks, high, matchedNow, timeMs)

        // 3. Match weak detections to tracks still unmatched (keeps partly hidden objects alive)
        associate(tracks.filter { it !in matchedNow }, low, matchedNow, timeMs)

        // 4. Unmatched confident detections start new tracks
        for (det in unmatchedHigh) {
            val t = Track(det, timeMs)
            tracks += t
            matchedNow += t
        }

        // 5. Drop tracks not seen for too long, and new tracks that missed a frame
        tracks.removeAll { t ->
            val missed = t !in matchedNow
            (missed && timeMs - t.lastSeenMs > lostTimeoutMs) || (missed && t.id == 0)
        }

        // 6. Report confirmed tracks seen in this frame
        return tracks
            .filter { it in matchedNow && it.id != 0 }
            .map { it.toTrackedObject(detections.imageWidth, detections.imageHeight) }
    }

    fun reset() {
        tracks.clear()
        lastTimeMs = -1L
    }

    /** Greedy IoU matching, same label only. Returns detections that matched nothing. */
    private fun associate(
        candidates: List<Track>,
        dets: List<DetectedObject>,
        matchedNow: MutableSet<Track>,
        timeMs: Long,
    ): List<DetectedObject> {
        val pairs = ArrayList<Triple<Float, Track, DetectedObject>>()
        for (t in candidates) {
            val predicted = t.box()
            for (d in dets) {
                if (d.label != t.label) continue
                val overlap = iou(predicted, d.box)
                if (overlap >= minIou) pairs += Triple(overlap, t, d)
            }
        }
        pairs.sortByDescending { it.first }

        val usedDets = mutableSetOf<DetectedObject>()
        for ((_, t, d) in pairs) {
            if (t in matchedNow || d in usedDets) continue
            t.correct(d, timeMs)
            if (t.id == 0 && t.hits >= minHits) t.id = nextId++
            matchedNow += t
            usedDets += d
        }
        return dets.filter { it !in usedDets }
    }

    private inner class Track(det: DetectedObject, timeMs: Long) {
        var id = 0 // 0 = not confirmed yet
        val label = det.label
        var score = det.score
        var hits = 1
        var lastSeenMs = timeMs

        private val cx = Kalman1D(det.box.centerX())
        private val cy = Kalman1D(det.box.centerY())
        private val w = Kalman1D(det.box.width())
        private val h = Kalman1D(det.box.height())

        init {
            if (minHits <= 1) id = nextId++
        }

        fun predict(dt: Float) {
            cx.predict(dt); cy.predict(dt); w.predict(dt); h.predict(dt)
        }

        fun correct(det: DetectedObject, timeMs: Long) {
            cx.update(det.box.centerX()); cy.update(det.box.centerY())
            w.update(det.box.width()); h.update(det.box.height())
            score = det.score
            hits++
            lastSeenMs = timeMs
        }

        fun box(): RectF {
            val halfW = max(w.x, 1f) / 2f
            val halfH = max(h.x, 1f) / 2f
            return RectF(cx.x - halfW, cy.x - halfH, cx.x + halfW, cy.x + halfH)
        }

        fun toTrackedObject(imageW: Int, imageH: Int): TrackedObject {
            val b = box()
            val cutOff = b.top <= EDGE_MARGIN_PX || b.bottom >= imageH - EDGE_MARGIN_PX

            // Pinhole camera model: distance = real height * focal length / pixel height
            val distance = KNOWN_HEIGHTS_M[label]
                ?.takeIf { !cutOff && h.x > 1f }
                ?.let { it * focalPx / h.x }

            // Looming: time to contact = height / rate of growth
            val growth = h.v
            val ttc = if (h.x > 1f && growth > MIN_GROWTH_PER_SEC * h.x) {
                (h.x / growth).takeIf { it <= MAX_TTC_SEC }
            } else {
                null
            }

            val direction = when {
                b.centerX() < imageW / 3f -> "left"
                b.centerX() > imageW * 2f / 3f -> "right"
                else -> "ahead"
            }
            return TrackedObject(id, label, score, b, direction, distance, ttc, cutOff)
        }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interW = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val interH = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = interW * interH
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }
}

/**
 * Constant-velocity Kalman filter for one value (position + velocity).
 * The same kind of state estimator a drone uses to smooth noisy sensor readings.
 */
private class Kalman1D(
    initial: Float,
    private val measurementNoise: Float = 64f,   // detector jitter, ~8 px
    private val accelNoise: Float = 90_000f,     // how fast motion can change, (~300 px/s²)²
) {
    var x = initial   // value (pixels)
        private set
    var v = 0f        // rate of change (pixels per second)
        private set

    // Covariance
    private var p00 = measurementNoise
    private var p01 = 0f
    private var p11 = 10_000f // unsure about initial speed

    fun predict(dt: Float) {
        if (dt <= 0f) return
        x += v * dt
        val dt2 = dt * dt
        val q = accelNoise
        val n00 = p00 + 2f * dt * p01 + dt2 * p11 + q * dt2 * dt2 / 4f
        val n01 = p01 + dt * p11 + q * dt2 * dt / 2f
        val n11 = p11 + q * dt2
        p00 = n00; p01 = n01; p11 = n11
    }

    fun update(z: Float) {
        val y = z - x
        val s = p00 + measurementNoise
        val k0 = p00 / s
        val k1 = p01 / s
        x += k0 * y
        v += k1 * y
        val n00 = (1f - k0) * p00
        val n01 = (1f - k0) * p01
        val n11 = p11 - k1 * p01
        p00 = n00; p01 = n01; p11 = n11
    }
}