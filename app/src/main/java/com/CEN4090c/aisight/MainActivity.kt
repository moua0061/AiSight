//BARE BONES: REGISTERING THE META GLASSES ONLY & GIVING PERMISSION:
//package com.CEN4090c.aisight
//
//import android.Manifest
//import android.os.Build
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.activity.viewModels
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.unit.dp
//import androidx.lifecycle.lifecycleScope
//import com.CEN4090c.aisight.ui.theme.AiSightTheme
//import com.meta.wearable.dat.core.Wearables
//import com.meta.wearable.dat.core.types.Permission
//import com.meta.wearable.dat.core.types.PermissionStatus
//import kotlinx.coroutines.launch
//
//class MainActivity : ComponentActivity() {
//
//    private val streamViewModel: StreamViewModel by viewModels()
//
//    // Android's Bluetooth permission, needed before registering
//    private val bluetoothPermission = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { granted ->
//        if (granted) Wearables.startRegistration(this)
//    }
//
//    // The glasses' camera permission, granted through the Meta AI app
//    private val cameraPermission = registerForActivityResult(
//        Wearables.RequestPermissionContract()
//    ) { result ->
//        result
//            .onSuccess { status ->
//                if (status == PermissionStatus.Granted) streamViewModel.start()
//                else streamViewModel.showMessage("Camera permission denied")
//            }
//            .onFailure { error, _ -> streamViewModel.showMessage("Permission error: ${error.description}") }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContent {
//            AiSightTheme {
//                var registration by remember { mutableStateOf("Unknown") }
//                LaunchedEffect(Unit) {
//                    Wearables.registrationState.collect { registration = it.toString() }
//                }
//
//                val status by streamViewModel.status.collectAsState()
//                val preview by streamViewModel.preview.collectAsState()
//                val fps by streamViewModel.fps.collectAsState()
//                val isActive by streamViewModel.isActive.collectAsState()
//
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Column(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(innerPadding)
//                            .padding(16.dp),
//                        verticalArrangement = Arrangement.spacedBy(12.dp),
//                    ) {
//                        Text("AiSight", style = MaterialTheme.typography.headlineMedium)
//                        Text("Registration: $registration")
//                        Text("$status  ·  $fps FPS")
//
//                        // Preview area
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .weight(1f)
//                                .background(Color.Black),
//                            contentAlignment = Alignment.Center,
//                        ) {
//                            val frame = preview
//                            if (frame != null) {
//                                Image(
//                                    bitmap = frame.asImageBitmap(),
//                                    contentDescription = "Live view from the glasses camera",
//                                    modifier = Modifier.fillMaxSize(),
//                                    contentScale = ContentScale.Fit,
//                                )
//                            } else {
//                                Text("No video", color = Color.White)
//                            }
//                        }
//
//                        // Connect + start/stop
//                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                            OutlinedButton(
//                                onClick = { requestRegistration() },
//                                modifier = Modifier.weight(1f),
//                            ) { Text("Connect glasses") }
//
//                            Button(
//                                onClick = { if (isActive) streamViewModel.stop() else startStreaming() },
//                                modifier = Modifier.weight(1f),
//                            ) { Text(if (isActive) "Stop stream" else "Start stream") }
//                        }
//
//                        TextButton(
//                            onClick = { Wearables.startUnregistration(this@MainActivity) },
//                            modifier = Modifier.align(Alignment.CenterHorizontally),
//                        ) { Text("Unregister") }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun requestRegistration() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
//        } else {
//            Wearables.startRegistration(this)
//        }
//    }
//
//    /** Check the glasses camera permission; ask for it through Meta AI if needed, then stream. */
//    private fun startStreaming() {
//        lifecycleScope.launch {
//            Wearables.checkPermissionStatus(Permission.CAMERA)
//                .onSuccess { status ->
//                    if (status == PermissionStatus.Granted) streamViewModel.start()
//                    else cameraPermission.launch(Permission.CAMERA)
//                }
//                .onFailure { error, _ ->
//                    streamViewModel.showMessage("Permission check failed: ${error.description}")
//                }
//        }
//    }
//}



//LIVE STREAM META GLASSES W/O AI MODEL:
//package com.CEN4090c.aisight

//import android.Manifest
//import android.os.Build
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.activity.viewModels
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.unit.dp
//import androidx.lifecycle.lifecycleScope
//import com.CEN4090c.aisight.ui.theme.AiSightTheme
//import com.meta.wearable.dat.core.Wearables
//import com.meta.wearable.dat.core.types.Permission
//import com.meta.wearable.dat.core.types.PermissionStatus
//import kotlinx.coroutines.launch
//
//class MainActivity : ComponentActivity() {
//
//    private val streamViewModel: StreamViewModel by viewModels()
//
//    // Android's Bluetooth permission, needed before registering
//    private val bluetoothPermission = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { granted ->
//        if (granted) Wearables.startRegistration(this)
//    }
//
//    // The glasses' camera permission, granted through the Meta AI app
//    private val cameraPermission = registerForActivityResult(
//        Wearables.RequestPermissionContract()
//    ) { result ->
//        result
//            .onSuccess { status ->
//                if (status == PermissionStatus.Granted) streamViewModel.start()
//                else streamViewModel.showMessage("Camera permission denied")
//            }
//            .onFailure { error, _ -> streamViewModel.showMessage("Permission error: ${error.description}") }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContent {
//            AiSightTheme {
//                var registration by remember { mutableStateOf("Unknown") }
//                LaunchedEffect(Unit) {
//                    Wearables.registrationState.collect { registration = it.toString() }
//                }
//
//                val status by streamViewModel.status.collectAsState()
//                val preview by streamViewModel.preview.collectAsState()
//                val fps by streamViewModel.fps.collectAsState()
//                val isActive by streamViewModel.isActive.collectAsState()
//
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Column(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(innerPadding)
//                            .padding(16.dp),
//                        verticalArrangement = Arrangement.spacedBy(12.dp),
//                    ) {
//                        Text("AiSight", style = MaterialTheme.typography.headlineMedium)
//                        Text("Registration: $registration")
//                        Text("$status  ·  $fps FPS")
//
//                        // Preview area
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .weight(1f)
//                                .background(Color.Black),
//                            contentAlignment = Alignment.Center,
//                        ) {
//                            val frame = preview
//                            if (frame != null) {
//                                Image(
//                                    bitmap = frame.asImageBitmap(),
//                                    contentDescription = "Live view from the glasses camera",
//                                    modifier = Modifier.fillMaxSize(),
//                                    contentScale = ContentScale.Fit,
//                                )
//                            } else {
//                                Text("No video", color = Color.White)
//                            }
//                        }
//
//                        // Connect + start/stop
//                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                            OutlinedButton(
//                                onClick = { requestRegistration() },
//                                modifier = Modifier.weight(1f),
//                            ) { Text("Connect glasses") }
//
//                            Button(
//                                onClick = { if (isActive) streamViewModel.stop() else startStreaming() },
//                                modifier = Modifier.weight(1f),
//                            ) { Text(if (isActive) "Stop stream" else "Start stream") }
//                        }
//
//                        TextButton(
//                            onClick = { Wearables.startUnregistration(this@MainActivity) },
//                            modifier = Modifier.align(Alignment.CenterHorizontally),
//                        ) { Text("Unregister") }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun requestRegistration() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
//        } else {
//            Wearables.startRegistration(this)
//        }
//    }
//
//    /** Check the glasses camera permission; ask for it through Meta AI if needed, then stream. */
//    private fun startStreaming() {
//        lifecycleScope.launch {
//            Wearables.checkPermissionStatus(Permission.CAMERA)
//                .onSuccess { status ->
//                    if (status == PermissionStatus.Granted) streamViewModel.start()
//                    else cameraPermission.launch(Permission.CAMERA)
//                }
//                .onFailure { error, _ ->
//                    streamViewModel.showMessage("Permission check failed: ${error.description}")
//                }
//        }
//    }
//}





//USING GOOGLE MEDIA PIPELINE OBJECT DETECTION:
//package com.CEN4090c.aisight
//
//import android.Manifest
//import android.graphics.Paint
//import android.os.Build
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.activity.viewModels
//import androidx.compose.foundation.Canvas
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.geometry.Size
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.graphics.drawscope.Stroke
//import androidx.compose.ui.graphics.nativeCanvas
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.unit.dp
//import androidx.lifecycle.lifecycleScope
//import com.CEN4090c.aisight.ui.theme.AiSightTheme
//import com.meta.wearable.dat.core.Wearables
//import com.meta.wearable.dat.core.types.Permission
//import com.meta.wearable.dat.core.types.PermissionStatus
//import kotlin.math.max
//import kotlin.math.min
//import kotlinx.coroutines.launch
//
//class MainActivity : ComponentActivity() {
//
//    private val streamViewModel: StreamViewModel by viewModels()
//
//    // Android's Bluetooth permission, needed before registering
//    private val bluetoothPermission = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { granted ->
//        if (granted) Wearables.startRegistration(this)
//    }
//
//    // The glasses' camera permission, granted through the Meta AI app
//    private val cameraPermission = registerForActivityResult(
//        Wearables.RequestPermissionContract()
//    ) { result ->
//        result
//            .onSuccess { status ->
//                if (status == PermissionStatus.Granted) streamViewModel.start()
//                else streamViewModel.showMessage("Camera permission denied")
//            }
//            .onFailure { error, _ -> streamViewModel.showMessage("Permission error: ${error.description}") }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContent {
//            AiSightTheme {
//                var registration by remember { mutableStateOf("Unknown") }
//                LaunchedEffect(Unit) {
//                    Wearables.registrationState.collect { registration = it.toString() }
//                }
//
//                val status by streamViewModel.status.collectAsState()
//                val preview by streamViewModel.preview.collectAsState()
//                val fps by streamViewModel.fps.collectAsState()
//                val isActive by streamViewModel.isActive.collectAsState()
//                val detections by streamViewModel.detections.collectAsState()
//
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Column(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(innerPadding)
//                            .padding(16.dp),
//                        verticalArrangement = Arrangement.spacedBy(12.dp),
//                    ) {
//                        Text("AiSight", style = MaterialTheme.typography.headlineMedium)
//                        Text("Registration: $registration")
//                        Text(
//                            "$status  ·  $fps FPS" +
//                                    (detections?.let { "  ·  detect ${it.inferenceMs} ms" } ?: "")
//                        )
//                        Text(
//                            "Seeing: " + (detections?.objects
//                                ?.takeIf { it.isNotEmpty() }
//                                ?.joinToString { "${it.label} (${it.direction})" }
//                                ?: "nothing")
//                        )
//
//                        // Preview area with detection boxes drawn on top
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .weight(1f)
//                                .background(Color.Black),
//                            contentAlignment = Alignment.Center,
//                        ) {
//                            val frame = preview
//                            if (frame != null) {
//                                Image(
//                                    bitmap = frame.asImageBitmap(),
//                                    contentDescription = "Live view from the glasses camera",
//                                    modifier = Modifier.fillMaxSize(),
//                                    contentScale = ContentScale.Fit,
//                                )
//                                detections?.let { DetectionOverlay(it) }
//                            } else {
//                                Text("No video", color = Color.White)
//                            }
//                        }
//
//                        // Connect + start/stop
//                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                            OutlinedButton(
//                                onClick = { requestRegistration() },
//                                modifier = Modifier.weight(1f),
//                            ) { Text("Connect glasses") }
//
//                            Button(
//                                onClick = { if (isActive) streamViewModel.stop() else startStreaming() },
//                                modifier = Modifier.weight(1f),
//                            ) { Text(if (isActive) "Stop stream" else "Start stream") }
//                        }
//
//                        TextButton(
//                            onClick = { Wearables.startUnregistration(this@MainActivity) },
//                            modifier = Modifier.align(Alignment.CenterHorizontally),
//                        ) { Text("Unregister") }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun requestRegistration() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
//        } else {
//            Wearables.startRegistration(this)
//        }
//    }
//
//    /** Check the glasses camera permission; ask for it through Meta AI if needed, then stream. */
//    private fun startStreaming() {
//        lifecycleScope.launch {
//            Wearables.checkPermissionStatus(Permission.CAMERA)
//                .onSuccess { status ->
//                    if (status == PermissionStatus.Granted) streamViewModel.start()
//                    else cameraPermission.launch(Permission.CAMERA)
//                }
//                .onFailure { error, _ ->
//                    streamViewModel.showMessage("Permission check failed: ${error.description}")
//                }
//        }
//    }
//}
//
///** Draws boxes and labels, scaled the same way ContentScale.Fit scales the preview image. */
//@Composable
//private fun DetectionOverlay(detections: FrameDetections) {
//    val textPaint = remember {
//        Paint().apply {
//            color = android.graphics.Color.GREEN
//            textSize = 40f
//            isAntiAlias = true
//        }
//    }
//    Canvas(modifier = Modifier.fillMaxSize()) {
//        val scale = min(size.width / detections.imageWidth, size.height / detections.imageHeight)
//        val offsetX = (size.width - detections.imageWidth * scale) / 2f
//        val offsetY = (size.height - detections.imageHeight * scale) / 2f
//
//        detections.objects.forEach { obj ->
//            val left = offsetX + obj.box.left * scale
//            val top = offsetY + obj.box.top * scale
//            val width = obj.box.width() * scale
//            val height = obj.box.height() * scale
//
//            drawRect(
//                color = Color.Green,
//                topLeft = Offset(left, top),
//                size = Size(width, height),
//                style = Stroke(width = 4f),
//            )
//            drawContext.canvas.nativeCanvas.drawText(
//                "${obj.label} ${(obj.score * 100).toInt()}% · ${obj.direction}",
//                left,
//                max(top - 10f, 40f),
//                textPaint,
//            )
//        }
//    }
//}




//TRYING ROBOFLO:
package com.CEN4090c.aisight

import android.Manifest
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.CEN4090c.aisight.ui.theme.AiSightTheme
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val streamViewModel: StreamViewModel by viewModels()

    // Android's Bluetooth permission, needed before registering
    private val bluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Wearables.startRegistration(this)
    }

    // The glasses' camera permission, granted through the Meta AI app
    private val cameraPermission = registerForActivityResult(
        Wearables.RequestPermissionContract()
    ) { result ->
        result
            .onSuccess { status ->
                if (status == PermissionStatus.Granted) streamViewModel.start()
                else streamViewModel.showMessage("Camera permission denied")
            }
            .onFailure { error, _ -> streamViewModel.showMessage("Permission error: ${error.description}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiSightTheme {
                var registration by remember { mutableStateOf("Unknown") }
                LaunchedEffect(Unit) {
                    Wearables.registrationState.collect { registration = it.toString() }
                }

                val status by streamViewModel.status.collectAsState()
                val preview by streamViewModel.preview.collectAsState()
                val fps by streamViewModel.fps.collectAsState()
                val isActive by streamViewModel.isActive.collectAsState()
                val detections by streamViewModel.detections.collectAsState()
                val tracks by streamViewModel.tracks.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("AiSight", style = MaterialTheme.typography.headlineMedium)
                        Text("Registration: $registration")
                        Text(
                            "$status  ·  $fps FPS" +
                                    (detections?.let { "  ·  detect ${it.inferenceMs} ms" } ?: "")
                        )
                        Text(
                            "Seeing: " + (tracks.takeIf { it.isNotEmpty() }
                                ?.joinToString { describe(it) }
                                ?: "nothing")
                        )

                        // Preview area with detection boxes drawn on top
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            val frame = preview
                            if (frame != null) {
                                Image(
                                    bitmap = frame.asImageBitmap(),
                                    contentDescription = "Live view from the glasses camera",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                                detections?.let { DetectionOverlay(tracks, it.imageWidth, it.imageHeight) }
                            } else {
                                Text("No video", color = Color.White)
                            }
                        }

                        // Connect + start/stop
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { requestRegistration() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Connect glasses") }

                            Button(
                                onClick = { if (isActive) streamViewModel.stop() else startStreaming() },
                                modifier = Modifier.weight(1f),
                            ) { Text(if (isActive) "Stop stream" else "Start stream") }
                        }

                        TextButton(
                            onClick = { Wearables.startUnregistration(this@MainActivity) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Unregister") }
                    }
                }
            }
        }
    }

    private fun requestRegistration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            Wearables.startRegistration(this)
        }
    }

    /** Check the glasses camera permission; ask for it through Meta AI if needed, then stream. */
    private fun startStreaming() {
        lifecycleScope.launch {
            Wearables.checkPermissionStatus(Permission.CAMERA)
                .onSuccess { status ->
                    if (status == PermissionStatus.Granted) streamViewModel.start()
                    else cameraPermission.launch(Permission.CAMERA)
                }
                .onFailure { error, _ ->
                    streamViewModel.showMessage("Permission check failed: ${error.description}")
                }
        }
    }
}

/** Short text for one tracked object, e.g. "person #3 ahead 2.4m, closing 1.8s". */
private fun describe(t: TrackedObject): String =
    "${t.label} #${t.id} ${t.direction}" +
            (t.distanceMeters?.let { " %.1fm".format(it) } ?: if (t.cutOff) " very close" else "") +
            (t.timeToContactSec?.let { ", closing %.1fs".format(it) } ?: "")

/** Draws tracked boxes, scaled the same way ContentScale.Fit scales the preview image. */
@Composable
private fun DetectionOverlay(tracks: List<TrackedObject>, imageWidth: Int, imageHeight: Int) {
    val textPaint = remember {
        Paint().apply {
            textSize = 38f
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = min(size.width / imageWidth, size.height / imageHeight)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f

        tracks.forEach { t ->
            // Red when closing in under 3 seconds, yellow when approaching, green otherwise
            val urgent = (t.timeToContactSec ?: Float.MAX_VALUE) < 3f
            val color = when {
                urgent -> Color.Red
                t.approaching -> Color.Yellow
                else -> Color.Green
            }
            val left = offsetX + t.box.left * scale
            val top = offsetY + t.box.top * scale

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(t.box.width() * scale, t.box.height() * scale),
                style = Stroke(width = if (urgent) 8f else 4f),
            )
            textPaint.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(describe(t), left, max(top - 10f, 40f), textPaint)
        }
    }
}