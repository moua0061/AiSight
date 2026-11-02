//package com.CEN4090c.aisight
//
//import android.graphics.Bitmap
//
///**
// * Where the AI team's model plugs in.
// *
// * processFrame() is called for every frame from the glasses, on a background thread,
// * one frame at a time. Keep it fast: if it's slower than the frame rate, frames back up.
// */
//class FrameProcessor {
//
//    fun processFrame(bitmap: Bitmap) {
//        // TODO (AI team): run the .tflite object detection model on this bitmap,
//        // then pass the detections to the audio/haptic feedback layer.
//    }
//}




//USING GOOGLE MEDIA PIPELINE OBJECT DETECTION:
//package com.CEN4090c.aisight
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.RectF
//import android.os.SystemClock
//import com.google.mediapipe.framework.image.BitmapImageBuilder
//import com.google.mediapipe.tasks.core.BaseOptions
//import com.google.mediapipe.tasks.vision.core.RunningMode
//import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
//
///** One detected object, in the frame's pixel coordinates. */
//data class DetectedObject(
//    val label: String,
//    val score: Float,
//    val box: RectF,
//    val direction: String, // "left", "ahead", or "right"
//)
//
///** Everything found in one frame, plus the frame size (needed to draw boxes) and timing. */
//data class FrameDetections(
//    val objects: List<DetectedObject>,
//    val imageWidth: Int,
//    val imageHeight: Int,
//    val inferenceMs: Long,
//)
//
///**
// * Runs object detection on frames from the glasses.
// *
// * For now this uses MediaPipe's stock EfficientDet-Lite0 model (80 COCO objects).
// * When the AI team's model is ready, it replaces the inside of processFrame();
// * the rest of the app only depends on the FrameDetections it returns.
// */
//class FrameProcessor(context: Context) {
//
//    companion object {
//        private const val MODEL_FILE = "efficientdet_lite0.tflite" // in app/src/main/assets
//    }
//
//    private val detector: ObjectDetector = ObjectDetector.createFromOptions(
//        context,
//        ObjectDetector.ObjectDetectorOptions.builder()
//            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
//            .setRunningMode(RunningMode.IMAGE)
//            .setMaxResults(5)
//            .setScoreThreshold(0.5f)
//            .build(),
//    )
//
//    /** Blocking call: run it on a background thread. */
//    fun processFrame(bitmap: Bitmap): FrameDetections {
//        val start = SystemClock.uptimeMillis()
//        val result = detector.detect(BitmapImageBuilder(bitmap).build())
//        val elapsed = SystemClock.uptimeMillis() - start
//
//        val objects = result.detections().mapNotNull { detection ->
//            val category = detection.categories().firstOrNull() ?: return@mapNotNull null
//            val box = detection.boundingBox()
//            DetectedObject(
//                label = category.categoryName(),
//                score = category.score(),
//                box = box,
//                direction = directionOf(box.centerX(), bitmap.width),
//            )
//        }
//        return FrameDetections(objects, bitmap.width, bitmap.height, elapsed)
//    }
//
//    /** Splits the frame into thirds: left / ahead / right. */
//    private fun directionOf(centerX: Float, width: Int): String = when {
//        centerX < width / 3f -> "left"
//        centerX > width * 2f / 3f -> "right"
//        else -> "ahead"
//    }
//
//    fun close() = detector.close()
//}



//TRYING OUT ROBOFLO:
package com.CEN4090c.aisight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/** One detected object, in the frame's pixel coordinates. */
data class DetectedObject(
    val label: String,
    val score: Float,
    val box: RectF,
    val direction: String, // "left", "ahead", or "right"
)

/** Everything found in one frame, plus the frame size (needed to draw boxes) and timing. */
data class FrameDetections(
    val objects: List<DetectedObject>,
    val imageWidth: Int,
    val imageHeight: Int,
    val inferenceMs: Long,
)

/**
 * Runs object detection on frames from the glasses.
 *
 * For now this uses MediaPipe's stock EfficientDet-Lite0 model (80 COCO objects).
 * When the AI team's model is ready, it replaces the inside of processFrame();
 * the rest of the app only depends on the FrameDetections it returns.
 */
class FrameProcessor(context: Context) {

    companion object {
        private const val MODEL_FILE = "efficientdet_lite0.tflite" // in app/src/main/assets
    }

    private val detector: ObjectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(10)
            .setScoreThreshold(0.25f) // low on purpose: the tracker uses weak detections to keep tracks alive
            .build(),
    )

    /** Blocking call: run it on a background thread. */
    fun processFrame(bitmap: Bitmap): FrameDetections {
        val start = SystemClock.uptimeMillis()
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        val elapsed = SystemClock.uptimeMillis() - start

        val objects = result.detections().mapNotNull { detection ->
            val category = detection.categories().firstOrNull() ?: return@mapNotNull null
            val box = detection.boundingBox()
            DetectedObject(
                label = category.categoryName(),
                score = category.score(),
                box = box,
                direction = directionOf(box.centerX(), bitmap.width),
            )
        }
        return FrameDetections(objects, bitmap.width, bitmap.height, elapsed)
    }

    /** Splits the frame into thirds: left / ahead / right. */
    private fun directionOf(centerX: Float, width: Int): String = when {
        centerX < width / 3f -> "left"
        centerX > width * 2f / 3f -> "right"
        else -> "ahead"
    }

    fun close() = detector.close()
}