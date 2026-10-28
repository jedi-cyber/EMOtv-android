package pe.unheval.emotv.ui.session

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pe.unheval.emotv.domain.repository.FrameSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Cuadros de la cámara frontal, solo en memoria. `ImageAnalysis` entrega el cuadro más
 * reciente; se convierte a JPEG cuando el ViewModel lo pide y nunca se escribe en el
 * almacenamiento, la galería ni la caché. Los cuadros que nadie pidió se descartan.
 */
class CameraFrameSource : FrameSource {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val pending = AtomicReference<CompletableDeferred<Bitmap?>?>(null)

    val analysis: ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                // La web envía 640 × 480; el servidor acepta hasta 1 000 000 bytes.
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(FRAME_WIDTH, FRAME_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { it.setAnalyzer(executor, ::analyze) }

    private fun analyze(image: ImageProxy) {
        image.use {
            val request = pending.getAndSet(null) ?: return
            val bitmap = runCatching { it.toUprightBitmap() }.getOrNull()
            if (!request.complete(bitmap)) bitmap?.recycle()
        }
    }

    override suspend fun capture(jpegQuality: Int): ByteArray? {
        val request = CompletableDeferred<Bitmap?>()
        pending.getAndSet(request)?.cancel()
        val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { request.await() }
        if (bitmap == null) {
            pending.compareAndSet(request, null)
            request.cancel()
            return null
        }
        return withContext(Dispatchers.Default) {
            try {
                encodeJpeg(bitmap, jpegQuality)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun close() {
        analysis.clearAnalyzer()
        pending.getAndSet(null)?.cancel()
        executor.shutdown()
    }

    private companion object {
        const val FRAME_WIDTH = 640
        const val FRAME_HEIGHT = 480
        const val CAPTURE_TIMEOUT_MS = 2_000L
    }
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

/** Comprime en memoria y borra el búfer intermedio: solo queda el arreglo que se envía. */
private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val stream = WipingByteStream(INITIAL_JPEG_BUFFER)
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArrayAndWipe()
}

private const val INITIAL_JPEG_BUFFER = 64 * 1024

private class WipingByteStream(size: Int) : ByteArrayOutputStream(size) {
    fun toByteArrayAndWipe(): ByteArray = toByteArray().also { buf.fill(0) }
}

/**
 * Abre la cámara frontal con [useCases] mientras la pantalla está en composición y la
 * libera al salir. [onResult] indica si se pudo abrir.
 */
@Composable
private fun FrontCameraEffect(useCases: List<UseCase>, onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    DisposableEffect(lifecycleOwner, useCases) {
        var provider: ProcessCameraProvider? = null
        val job = scope.launch {
            val opened = try {
                val instance = ProcessCameraProvider.awaitInstance(context)
                provider = instance
                instance.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, *useCases.toTypedArray())
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin cámara frontal, sin permiso o en uso por otra aplicación.
                false
            }
            currentOnResult(opened)
        }
        onDispose {
            job.cancel()
            provider?.unbind(*useCases.toTypedArray())
        }
    }
}

/** Vista previa para revisar la iluminación. Solo se muestra: no se captura ni se guarda nada. */
@Composable
fun CameraPreview(onReadyChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val preview = remember {
        Preview.Builder().build().apply { setSurfaceProvider { request -> surfaceRequest = request } }
    }
    val useCases = remember(preview) { listOf<UseCase>(preview) }
    val currentOnReadyChange by rememberUpdatedState(onReadyChange)
    FrontCameraEffect(useCases = useCases, onResult = currentOnReadyChange)
    DisposableEffect(Unit) {
        onDispose { currentOnReadyChange(false) }
    }
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

/**
 * Cámara del modo sesión, sin vista previa: la persona evaluada ve solo el cuestionario y los
 * estímulos. Se entrega al ViewModel como [FrameSource] y se libera al salir.
 */
@Composable
fun SessionCameraEffect(onAttach: (FrameSource) -> Unit, onDetach: () -> Unit) {
    val source = remember { CameraFrameSource() }
    val useCases = remember(source) { listOf<UseCase>(source.analysis) }
    val currentOnAttach by rememberUpdatedState(onAttach)
    val currentOnDetach by rememberUpdatedState(onDetach)
    FrontCameraEffect(useCases = useCases) { opened -> if (opened) currentOnAttach(source) }
    DisposableEffect(source) {
        onDispose {
            currentOnDetach()
            source.close()
        }
    }
}
