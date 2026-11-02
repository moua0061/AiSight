//package com.CEN4090c.aisight
//
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.ImageFormat
//import android.graphics.Rect
//import android.graphics.YuvImage
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.meta.wearable.dat.camera.Camera
//import com.meta.wearable.dat.camera.addCamera
//import com.meta.wearable.dat.camera.types.StreamConfiguration
//import com.meta.wearable.dat.camera.types.StreamState
//import com.meta.wearable.dat.camera.types.VideoFrame
//import com.meta.wearable.dat.camera.types.VideoQuality
//import com.meta.wearable.dat.core.Wearables
//import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
//import com.meta.wearable.dat.core.session.DeviceSession
//import com.meta.wearable.dat.core.session.DeviceSessionState
//import java.io.ByteArrayOutputStream
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//
///**
// * Owns the glasses connection: session -> camera -> video stream -> frames.
// * Lives in a ViewModel so it survives screen rotation.
// */
//class StreamViewModel : ViewModel() {
//
//    companion object {
//        private const val TAG = "AiSight"
//        private const val FRAME_RATE = 15 // valid: 2, 7, 15, 24, 30
//    }
//
//    // ---- State the UI reads ----
//    private val _status = MutableStateFlow("Idle")
//    val status: StateFlow<String> = _status
//
//    private val _preview = MutableStateFlow<Bitmap?>(null)
//    val preview: StateFlow<Bitmap?> = _preview
//
//    private val _fps = MutableStateFlow(0)
//    val fps: StateFlow<Int> = _fps
//
//    private val _isActive = MutableStateFlow(false)
//    val isActive: StateFlow<Boolean> = _isActive
//
//    // ---- SDK objects ----
//    private var session: DeviceSession? = null
//    private var camera: Camera? = null
//    private val jobs = mutableListOf<Job>()
//
//    // Frames are handled one at a time, in order, off the main thread
//    private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)
//    private val frameProcessor = FrameProcessor()
//
//    // FPS counter
//    private var frameCount = 0
//    private var windowStart = 0L
//
//    fun showMessage(message: String) {
//        _status.value = message
//    }
//
//    /** Step 6: create and start a session. The camera is added once the session is STARTED. */
//    fun start() {
//        if (session != null) return
//        _isActive.value = true
//        _status.value = "Connecting…"
//
//        Wearables.createSession(AutoDeviceSelector()).fold(
//            onSuccess = { s ->
//                session = s
//                // Subscribe before start() so no state change is missed
//                jobs += viewModelScope.launch {
//                    s.errors.collect { error -> _status.value = "Session error: ${error.description}" }
//                }
//                jobs += viewModelScope.launch {
//                    s.state.collect { state ->
//                        _status.value = "Session: $state"
//                        when (state) {
//                            DeviceSessionState.STARTED -> if (camera == null) addCamera(s)
//                            DeviceSessionState.STOPPED -> cleanup()
//                            else -> Unit
//                        }
//                    }
//                }
//                s.start()
//            },
//            onFailure = { error, _ ->
//                _status.value = "Couldn't create session: ${error.description}"
//                cleanup()
//            },
//        )
//    }
//
//    /** Step 7: attach the camera and start the video stream. */
//    private fun addCamera(s: DeviceSession) {
//        s.addCamera(
//            StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = FRAME_RATE),
//        ).fold(
//            onSuccess = { cam ->
//                camera = cam
//                val stream = cam.stream
//                jobs += viewModelScope.launch {
//                    stream.state.collect { state -> _status.value = "Stream: $state" }
//                }
//                jobs += viewModelScope.launch {
//                    stream.errorStream.collect { error -> _status.value = "Stream error: ${error.description}" }
//                }
//                windowStart = System.currentTimeMillis()
//                jobs += viewModelScope.launch(frameDispatcher) {
//                    stream.videoStream.collect { frame -> onFrame(frame) }
//                }
//                stream.start().onFailure { error, _ ->
//                    _status.value = "Couldn't start stream: ${error.description}"
//                }
//            },
//            onFailure = { error, _ -> _status.value = "Couldn't add camera: ${error.description}" },
//        )
//    }
//
//    /** Runs for every frame from the glasses. */
//    private fun onFrame(frame: VideoFrame) {
//        if (frame.isCompressed) return
//        val bitmap = frameToBitmap(frame) ?: return
//
//        _preview.value = bitmap               // show it on screen
//        frameProcessor.processFrame(bitmap)   // hand it to the AI pipeline
//
//        // Log frames per second once a second
//        frameCount++
//        val now = System.currentTimeMillis()
//        if (now - windowStart >= 1000) {
//            _fps.value = frameCount
//            Log.d(TAG, "FPS: $frameCount (${frame.width}x${frame.height})")
//            frameCount = 0
//            windowStart = now
//        }
//    }
//
//    /** Step 8 equivalent: stop everything. Stopping the session also stops the camera and stream. */
//    fun stop() {
//        val s = session
//        if (s != null) {
//            _status.value = "Stopping…"
//            s.stop() // the STOPPED state then triggers cleanup()
//        } else {
//            cleanup()
//        }
//    }
//
//    private fun cleanup() {
//        jobs.forEach { it.cancel() }
//        jobs.clear()
//        camera = null
//        session = null
//        _preview.value = null
//        _fps.value = 0
//        _isActive.value = false
//        _status.value = "Stopped"
//    }
//
//    override fun onCleared() {
//        session?.stop()
//        cleanup()
//        super.onCleared()
//    }
//
//    /**
//     * The SDK delivers raw YUV pixels. This assumes the I420 layout (Y plane, then U, then V),
//     * converts to NV21, and lets Android turn it into a Bitmap.
//     * If the preview colors look wrong (purple/green tint), the layout is different; see notes.
//     */
//    private fun frameToBitmap(frame: VideoFrame): Bitmap? {
//        val w = frame.width
//        val h = frame.height
//        val buffer = frame.buffer.duplicate()
//        val bytes = ByteArray(buffer.remaining())
//        buffer.get(bytes)
//
//        val ySize = w * h
//        val uvSize = ySize / 4
//        if (bytes.size < ySize + 2 * uvSize) {
//            Log.w(TAG, "Unexpected frame size ${bytes.size} for ${w}x$h")
//            return null
//        }
//
//        val nv21 = ByteArray(ySize + 2 * uvSize)
//        System.arraycopy(bytes, 0, nv21, 0, ySize)
//        val uStart = ySize
//        val vStart = ySize + uvSize
//        var out = ySize
//        for (i in 0 until uvSize) {
//            nv21[out++] = bytes[vStart + i]
//            nv21[out++] = bytes[uStart + i]
//        }
//
//        val jpeg = ByteArrayOutputStream()
//        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 80, jpeg)
//        val data = jpeg.toByteArray()
//        return BitmapFactory.decodeByteArray(data, 0, data.size)
//    }
//}







//USING GOOGLE MEDIA PIPELINE OBJECT DETECTION:
//package com.CEN4090c.aisight
//
//import android.app.Application
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.ImageFormat
//import android.graphics.Rect
//import android.graphics.YuvImage
//import android.util.Log
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.meta.wearable.dat.camera.Camera
//import com.meta.wearable.dat.camera.addCamera
//import com.meta.wearable.dat.camera.types.StreamConfiguration
//import com.meta.wearable.dat.camera.types.VideoFrame
//import com.meta.wearable.dat.camera.types.VideoQuality
//import com.meta.wearable.dat.core.Wearables
//import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
//import com.meta.wearable.dat.core.session.DeviceSession
//import com.meta.wearable.dat.core.session.DeviceSessionState
//import java.io.ByteArrayOutputStream
//import java.util.concurrent.atomic.AtomicBoolean
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//
///**
// * Owns the glasses connection: session -> camera -> video stream -> frames -> detection.
// * AndroidViewModel so it has a Context for loading the detection model.
// */
//class StreamViewModel(application: Application) : AndroidViewModel(application) {
//
//    companion object {
//        private const val TAG = "AiSight"
//        private const val FRAME_RATE = 15 // valid: 2, 7, 15, 24, 30
//    }
//
//    // ---- State the UI reads ----
//    private val _status = MutableStateFlow("Idle")
//    val status: StateFlow<String> = _status
//
//    private val _preview = MutableStateFlow<Bitmap?>(null)
//    val preview: StateFlow<Bitmap?> = _preview
//
//    private val _fps = MutableStateFlow(0)
//    val fps: StateFlow<Int> = _fps
//
//    private val _isActive = MutableStateFlow(false)
//    val isActive: StateFlow<Boolean> = _isActive
//
//    private val _detections = MutableStateFlow<FrameDetections?>(null)
//    val detections: StateFlow<FrameDetections?> = _detections
//
//    // ---- SDK objects ----
//    private var session: DeviceSession? = null
//    private var camera: Camera? = null
//    private val jobs = mutableListOf<Job>()
//    private var lastError: String? = null
//
//    // Frames are converted one at a time, in order, off the main thread
//    private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)
//
//    // Detection runs separately; if it's still busy, new frames skip detection (preview keeps updating)
//    private val isDetecting = AtomicBoolean(false)
//    private val frameProcessor: FrameProcessor? = try {
//        FrameProcessor(application)
//    } catch (e: Exception) {
//        Log.e(TAG, "Could not load detection model", e)
//        lastError = "Detector failed to load: ${e.message}"
//        _status.value = lastError!!
//        null
//    }
//
//    // FPS counter
//    private var frameCount = 0
//    private var windowStart = 0L
//
//    fun showMessage(message: String) {
//        _status.value = message
//    }
//
//    /** Create and start a session. The camera is added once the session is STARTED. */
//    fun start() {
//        if (session != null) return
//        _isActive.value = true
//        _status.value = "Connecting…"
//        lastError = null
//
//        Wearables.createSession(AutoDeviceSelector()).fold(
//            onSuccess = { s ->
//                session = s
//                // Subscribe before start() so no state change is missed
//                jobs += viewModelScope.launch {
//                    s.errors.collect { error ->
//                        lastError = "Session error: ${error.description}"
//                        _status.value = lastError!!
//                    }
//                }
//                jobs += viewModelScope.launch {
//                    s.state.collect { state ->
//                        _status.value = "Session: $state"
//                        when (state) {
//                            DeviceSessionState.STARTED -> if (camera == null) addCamera(s)
//                            DeviceSessionState.STOPPED -> cleanup()
//                            else -> Unit
//                        }
//                    }
//                }
//                s.start()
//            },
//            onFailure = { error, _ ->
//                lastError = "Couldn't create session: ${error.description}"
//                cleanup()
//            },
//        )
//    }
//
//    /** Attach the camera and start the video stream. */
//    private fun addCamera(s: DeviceSession) {
//        s.addCamera(
//            StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = FRAME_RATE),
//        ).fold(
//            onSuccess = { cam ->
//                camera = cam
//                val stream = cam.stream
//                jobs += viewModelScope.launch {
//                    stream.state.collect { state -> _status.value = "Stream: $state" }
//                }
//                jobs += viewModelScope.launch {
//                    stream.errorStream.collect { error ->
//                        _status.value = "Stream error: ${error.description}"
//                    }
//                }
//                windowStart = System.currentTimeMillis()
//                jobs += viewModelScope.launch(frameDispatcher) {
//                    stream.videoStream.collect { frame -> onFrame(frame) }
//                }
//                stream.start().onFailure { error, _ ->
//                    _status.value = "Couldn't start stream: ${error.description}"
//                }
//            },
//            onFailure = { error, _ -> _status.value = "Couldn't add camera: ${error.description}" },
//        )
//    }
//
//    /** Runs for every frame from the glasses. */
//    private fun onFrame(frame: VideoFrame) {
//        if (frame.isCompressed) return
//        val bitmap = frameToBitmap(frame) ?: return
//
//        _preview.value = bitmap
//        runDetection(bitmap)
//
//        // Log frames per second once a second
//        frameCount++
//        val now = System.currentTimeMillis()
//        if (now - windowStart >= 1000) {
//            _fps.value = frameCount
//            Log.d(TAG, "FPS: $frameCount (${frame.width}x${frame.height})")
//            frameCount = 0
//            windowStart = now
//        }
//    }
//
//    /** Runs the detector unless it's still busy with an earlier frame. */
//    private fun runDetection(bitmap: Bitmap) {
//        val processor = frameProcessor ?: return
//        if (!isDetecting.compareAndSet(false, true)) return // busy: skip this frame
//
//        viewModelScope.launch(Dispatchers.Default) {
//            try {
//                val result = processor.processFrame(bitmap)
//                _detections.value = result
//                Log.d(
//                    TAG,
//                    "Detect ${result.inferenceMs} ms: " +
//                            result.objects.joinToString { "${it.label} ${(it.score * 100).toInt()}% ${it.direction}" },
//                )
//            } catch (e: Exception) {
//                Log.e(TAG, "Detection failed", e)
//            } finally {
//                isDetecting.set(false)
//            }
//        }
//    }
//
//    /** Stop everything. Stopping the session also stops the camera and stream. */
//    fun stop() {
//        val s = session
//        if (s != null) {
//            _status.value = "Stopping…"
//            s.stop() // the STOPPED state then triggers cleanup()
//        } else {
//            cleanup()
//        }
//    }
//
//    private fun cleanup() {
//        jobs.forEach { it.cancel() }
//        jobs.clear()
//        camera = null
//        session = null
//        _preview.value = null
//        _detections.value = null
//        _fps.value = 0
//        _isActive.value = false
//        _status.value = lastError ?: "Stopped"
//    }
//
//    override fun onCleared() {
//        session?.stop()
//        cleanup()
//        frameProcessor?.close()
//        super.onCleared()
//    }
//
//    /**
//     * The SDK delivers raw YUV pixels in I420 layout (Y plane, then U, then V).
//     * Converts to NV21 and lets Android turn it into a Bitmap.
//     */
//    private fun frameToBitmap(frame: VideoFrame): Bitmap? {
//        val w = frame.width
//        val h = frame.height
//        val buffer = frame.buffer.duplicate()
//        val bytes = ByteArray(buffer.remaining())
//        buffer.get(bytes)
//
//        val ySize = w * h
//        val uvSize = ySize / 4
//        if (bytes.size < ySize + 2 * uvSize) {
//            Log.w(TAG, "Unexpected frame size ${bytes.size} for ${w}x$h")
//            return null
//        }
//
//        val nv21 = ByteArray(ySize + 2 * uvSize)
//        System.arraycopy(bytes, 0, nv21, 0, ySize)
//        val uStart = ySize
//        val vStart = ySize + uvSize
//        var out = ySize
//        for (i in 0 until uvSize) {
//            nv21[out++] = bytes[vStart + i]
//            nv21[out++] = bytes[uStart + i]
//        }
//
//        val jpeg = ByteArrayOutputStream()
//        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 80, jpeg)
//        val data = jpeg.toByteArray()
//        return BitmapFactory.decodeByteArray(data, 0, data.size)
//    }
//}



//TRYING ROBOFLO:
package com.CEN4090c.aisight

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the glasses connection: session -> camera -> video stream -> frames -> detection.
 * AndroidViewModel so it has a Context for loading the detection model.
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiSight"
        private const val FRAME_RATE = 15 // valid: 2, 7, 15, 24, 30
    }

    // ---- State the UI reads ----
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _detections = MutableStateFlow<FrameDetections?>(null)
    val detections: StateFlow<FrameDetections?> = _detections

    private val _tracks = MutableStateFlow<List<TrackedObject>>(emptyList())
    val tracks: StateFlow<List<TrackedObject>> = _tracks

    // ---- SDK objects ----
    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private val jobs = mutableListOf<Job>()
    private var lastError: String? = null

    // Frames are converted one at a time, in order, off the main thread
    private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)

    // Detection runs separately; if it's still busy, new frames skip detection (preview keeps updating)
    private val isDetecting = AtomicBoolean(false)
    private val frameProcessor: FrameProcessor? = try {
        FrameProcessor(application)
    } catch (e: Exception) {
        Log.e(TAG, "Could not load detection model", e)
        lastError = "Detector failed to load: ${e.message}"
        _status.value = lastError!!
        null
    }

    // Gives detections stable IDs over time, plus distance and time to contact
    private val tracker = ObjectTracker()

    // FPS counter
    private var frameCount = 0
    private var windowStart = 0L

    fun showMessage(message: String) {
        _status.value = message
    }

    /** Create and start a session. The camera is added once the session is STARTED. */
    fun start() {
        if (session != null) return
        _isActive.value = true
        _status.value = "Connecting…"
        lastError = null

        Wearables.createSession(AutoDeviceSelector()).fold(
            onSuccess = { s ->
                session = s
                // Subscribe before start() so no state change is missed
                jobs += viewModelScope.launch {
                    s.errors.collect { error ->
                        lastError = "Session error: ${error.description}"
                        _status.value = lastError!!
                    }
                }
                jobs += viewModelScope.launch {
                    s.state.collect { state ->
                        _status.value = "Session: $state"
                        when (state) {
                            DeviceSessionState.STARTED -> if (camera == null) addCamera(s)
                            DeviceSessionState.STOPPED -> cleanup()
                            else -> Unit
                        }
                    }
                }
                s.start()
            },
            onFailure = { error, _ ->
                lastError = "Couldn't create session: ${error.description}"
                cleanup()
            },
        )
    }

    /** Attach the camera and start the video stream. */
    private fun addCamera(s: DeviceSession) {
        s.addCamera(
            StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = FRAME_RATE),
        ).fold(
            onSuccess = { cam ->
                camera = cam
                val stream = cam.stream
                jobs += viewModelScope.launch {
                    stream.state.collect { state -> _status.value = "Stream: $state" }
                }
                jobs += viewModelScope.launch {
                    stream.errorStream.collect { error ->
                        _status.value = "Stream error: ${error.description}"
                    }
                }
                windowStart = System.currentTimeMillis()
                jobs += viewModelScope.launch(frameDispatcher) {
                    stream.videoStream.collect { frame -> onFrame(frame) }
                }
                stream.start().onFailure { error, _ ->
                    _status.value = "Couldn't start stream: ${error.description}"
                }
            },
            onFailure = { error, _ -> _status.value = "Couldn't add camera: ${error.description}" },
        )
    }

    /** Runs for every frame from the glasses. */
    private fun onFrame(frame: VideoFrame) {
        if (frame.isCompressed) return
        val frameTimeMs = SystemClock.elapsedRealtime()
        val bitmap = frameToBitmap(frame) ?: return

        _preview.value = bitmap
        runDetection(bitmap, frameTimeMs)

        // Log frames per second once a second
        frameCount++
        val now = System.currentTimeMillis()
        if (now - windowStart >= 1000) {
            _fps.value = frameCount
            Log.d(TAG, "FPS: $frameCount (${frame.width}x${frame.height})")
            frameCount = 0
            windowStart = now
        }
    }

    /** Runs the detector unless it's still busy with an earlier frame. */
    private fun runDetection(bitmap: Bitmap, frameTimeMs: Long) {
        val processor = frameProcessor ?: return
        if (!isDetecting.compareAndSet(false, true)) return // busy: skip this frame

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = processor.processFrame(bitmap)
                val tracked = synchronized(tracker) { tracker.update(result, frameTimeMs) }
                _detections.value = result
                _tracks.value = tracked
                Log.d(
                    TAG,
                    "Detect ${result.inferenceMs} ms: " + tracked.joinToString {
                        "#${it.id} ${it.label} ${it.direction}" +
                                " h=${it.box.height().toInt()}px" +           // use h for focal-length calibration
                                (it.distanceMeters?.let { d -> " %.1fm".format(d) } ?: "") +
                                (it.timeToContactSec?.let { t -> " ttc=%.1fs".format(t) } ?: "")
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
            } finally {
                isDetecting.set(false)
            }
        }
    }

    /** Stop everything. Stopping the session also stops the camera and stream. */
    fun stop() {
        val s = session
        if (s != null) {
            _status.value = "Stopping…"
            s.stop() // the STOPPED state then triggers cleanup()
        } else {
            cleanup()
        }
    }

    private fun cleanup() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        camera = null
        session = null
        _preview.value = null
        _detections.value = null
        _tracks.value = emptyList()
        synchronized(tracker) { tracker.reset() }
        _fps.value = 0
        _isActive.value = false
        _status.value = lastError ?: "Stopped"
    }

    override fun onCleared() {
        session?.stop()
        cleanup()
        frameProcessor?.close()
        super.onCleared()
    }

    /**
     * The SDK delivers raw YUV pixels in I420 layout (Y plane, then U, then V).
     * Converts to NV21 and lets Android turn it into a Bitmap.
     */
    private fun frameToBitmap(frame: VideoFrame): Bitmap? {
        val w = frame.width
        val h = frame.height
        val buffer = frame.buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val ySize = w * h
        val uvSize = ySize / 4
        if (bytes.size < ySize + 2 * uvSize) {
            Log.w(TAG, "Unexpected frame size ${bytes.size} for ${w}x$h")
            return null
        }

        val nv21 = ByteArray(ySize + 2 * uvSize)
        System.arraycopy(bytes, 0, nv21, 0, ySize)
        val uStart = ySize
        val vStart = ySize + uvSize
        var out = ySize
        for (i in 0 until uvSize) {
            nv21[out++] = bytes[vStart + i]
            nv21[out++] = bytes[uStart + i]
        }

        val jpeg = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 80, jpeg)
        val data = jpeg.toByteArray()
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }
}