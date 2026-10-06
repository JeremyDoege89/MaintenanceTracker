package dev.maintracker.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.maintracker.domain.IdScan
import dev.maintracker.domain.ScanStabilizer
import dev.maintracker.domain.VinValidator
import dev.maintracker.ui.common.PrimaryButton
import java.time.LocalDate
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class ScanTarget { VIN, HIN }

fun Context.hasCamera(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/**
 * Full-screen camera that reads a VIN (barcode or printed plate) or a HIN (printed or stamped
 * text) and hands back the value once it has been read consistently. Everything runs on the phone
 * with ML Kit's bundled models; no frame or text leaves the device. The owner confirms before use.
 */
@Composable
fun IdScanDialog(target: ScanTarget, knownMic: (String) -> Boolean, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            val context = LocalContext.current
            fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            var granted by remember { mutableStateOf(allowed()) }
            var asked by remember { mutableStateOf(false) }
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
            LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
            // Back from system settings with access turned on.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = allowed() }

            when {
                granted -> Scanner(target, knownMic, onResult, onDismiss)
                asked -> NoCamera(
                    "Camera access is off, so the app can't scan. You can allow it in Settings, or close this and type the ${target.name}.",
                    onDismiss,
                    settings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun NoCamera(message: String, onDismiss: () -> Unit, settings: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        settings?.let { PrimaryButton("Open settings", it, Modifier.fillMaxWidth()) }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Close", color = Color.White)
        }
    }
}

@Composable
private fun Scanner(target: ScanTarget, knownMic: (String) -> Boolean, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(context) }
    var found by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var failed by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    val currentKnownMic by rememberUpdatedState(knownMic)
    val analyzer = remember { IdAnalyzer(target, { currentKnownMic(it) }) { value -> main.execute { found = value } } }
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView: a SurfaceView can end up behind the dialog window.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var bound: Pair<ProcessCameraProvider, Array<androidx.camera.core.UseCase>>? = null
        future.addListener({
            if (disposed) return@addListener
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                // Small print on a dashboard plate needs pixels; 1080p keeps OCR quick enough.
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                bound = provider to arrayOf(preview, analysis)
            } catch (e: Exception) {
                failed = true
            }
        }, main)
        onDispose {
            disposed = true
            bound?.let { (provider, cases) -> provider.unbind(*cases) }
            camera = null
            executor.execute { analyzer.close() }
            executor.shutdown()
        }
    }

    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(found) { if (found != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    if (failed) {
        NoCamera("This phone's back camera couldn't be opened. Close this and type the ${target.name}.", onDismiss)
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                previewView.apply {
                    // Tap to focus: plates sit behind glass at an angle, autofocus often hunts.
                    setOnTouchListener { v, e ->
                        if (e.action == MotionEvent.ACTION_UP) {
                            val point = meteringPointFactory.createPoint(e.x, e.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                            v.performClick()
                        }
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White) }
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(onClick = { torch = !torch }) {
                    Icon(
                        if (torch) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                        contentDescription = if (torch) "Turn light off" else "Turn light on",
                        tint = Color.White,
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f)
                    .border(2.dp, if (found != null) MaterialTheme.colorScheme.primary else Color.White, RoundedCornerShape(12.dp)),
            )
            Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(8.dp)) {
                Text(
                    when (target) {
                        ScanTarget.VIN -> "Fit the VIN inside the box: the plate at the bottom of the windshield, " +
                            "or the barcode on the driver's door frame. Tap to focus."
                        ScanTarget.HIN -> "Fit the HIN inside the box: usually on the upper right of the transom, " +
                            "or on the hull near the stern for a PWC. Tap to focus."
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        found?.let { value ->
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Found ${target.name}", style = MaterialTheme.typography.titleMedium)
                    Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                    val note = if (target == ScanTarget.VIN && VinValidator.validate(value) is VinValidator.Result.BadCheckDigit)
                        "The check digit doesn't match. Normal for some vehicles built outside North America; otherwise compare it with the plate."
                    else "Compare it with the plate before using it."
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { found = null; analyzer.resume() },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Scan again") }
                        PrimaryButton("Use", { onResult(value) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Runs barcode (VIN only) and text recognition on each frame, on the analysis thread. Stops after a
 * hit until [resume]; [onFound] is called on that thread.
 */
private class IdAnalyzer(
    private val target: ScanTarget,
    private val knownMic: (String) -> Boolean,
    private val onFound: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodes: BarcodeScanner? = if (target == ScanTarget.VIN) BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_128, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE)
            .build(),
    ) else null
    private val stabilizer = ScanStabilizer()
    private val year = LocalDate.now().year
    private val sameThread = Executor { it.run() }

    @Volatile private var paused = false
    @Volatile private var resetPending = false

    fun resume() { resetPending = true; paused = false }

    fun close() { text.close(); barcodes?.close() }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (paused || media == null) { proxy.close(); return }
        if (resetPending) { synchronized(stabilizer) { stabilizer.reset() }; resetPending = false }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        val barcodeTask = barcodes?.process(image)
        val textTask = text.process(image)
        // The listener runs on ML Kit's callback thread; the frame is closed only when both are done,
        // so KEEP_ONLY_LATEST never starts a second frame meanwhile.
        Tasks.whenAllComplete(listOfNotNull<Task<*>>(barcodeTask, textTask)).addOnCompleteListener(sameThread) {
            try {
                if (paused) return@addOnCompleteListener
                val frame = mutableListOf<IdScan.Candidate>()
                if (barcodeTask?.isSuccessful == true) {
                    barcodeTask.result.mapNotNullTo(frame) { b -> b.rawValue?.let(IdScan::vinFromBarcode)?.let { IdScan.Candidate(it, true) } }
                }
                if (textTask.isSuccessful) {
                    for (block in textTask.result.textBlocks) for (line in block.lines) {
                        frame += when (target) {
                            ScanTarget.VIN -> IdScan.vinCandidates(line.text)
                            ScanTarget.HIN -> IdScan.hinCandidates(line.text, year, knownMic)
                        }
                    }
                }
                synchronized(stabilizer) { stabilizer.offer(frame) }?.let { paused = true; onFound(it) }
            } finally {
                proxy.close()
            }
        }
    }
}
