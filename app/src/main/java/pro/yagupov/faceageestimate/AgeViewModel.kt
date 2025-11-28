package pro.yagupov.faceageestimate

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AgeViewModel(application: Application) : AndroidViewModel(application) {

    private val _prediction = MutableStateFlow<AgeModel.Prediction?>(null)
    val prediction: StateFlow<AgeModel.Prediction?> = _prediction
    private val _faceFound = MutableStateFlow(false)
    val faceFound = _faceFound

    private lateinit var ageModel: AgeModel

    fun initialize(context: Context) {
        ageModel = AgeModel(context)
    }

    suspend fun detectFace(bitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()

        val detector = FaceDetection.getClient(options)

        try{
            val image = InputImage.fromBitmap(bitmap, 0)

            val faces = detector.process(image).await() // из kotlinx-coroutines-play-services
            if (faces.isEmpty()) {
                faceFound.value = false
                return@withContext null
            } else {
                faceFound.value = true
            }

            val face = faces.first()

            // Применяем margin = 0.4 (как в demo.py)
            val margin = 0.4f
            val bbox = face.boundingBox
            val w = bbox.width()
            val h = bbox.height()
            val marginW = (w * margin).toInt()
            val marginH = (h * margin).toInt()

            val cropRect = Rect(
                (bbox.left - marginW).coerceAtLeast(0),
                (bbox.top - marginH).coerceAtLeast(0),
                (bbox.right + marginW).coerceAtMost(bitmap.width),
                (bbox.bottom + marginH).coerceAtMost(bitmap.height)
            )

            Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        } catch (e: Exception) {
            null
        } finally {
            detector.close()
        }
    }

    private var lastProcessTimeMs = 0L
    val minIntervalMs = 250 // ≈ 2 FPS (1000 / 500 = 2 кадра/сек)

    fun startCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(Size(640, 480))
            .setOutputImageRotationEnabled(true)
            .build()

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(getApplication())) { imageProxy ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTimeMs < minIntervalMs) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastProcessTimeMs = currentTime

            try {
                val bitmap = imageProxy.toBitmap()

                // Запускаем ВЕСЬ пайплайн (детекция + предсказание) в фоне
                viewModelScope.launch(Dispatchers.Default) {
                    try {
                        val faceBitmap = detectFace(bitmap) ?: return@launch

                        val prediction = ageModel.predict(faceBitmap)

                        // Обновление StateFlow можно делать из любого потока
                        _prediction.value = prediction
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } finally {
                imageProxy.close() // ← закрываем в ОСНОВНОМ потоке, как требует CameraX
            }
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = surfaceProvider
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val cameraProviderFuture = ProcessCameraProvider.getInstance(getApplication())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(getApplication()))
    }
}