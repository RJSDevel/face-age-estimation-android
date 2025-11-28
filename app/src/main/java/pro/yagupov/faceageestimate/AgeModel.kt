package pro.yagupov.faceageestimate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import kotlin.math.exp

class AgeModel(context: Context) {

    private val module: Module

    init {
        val assetFilePath = "age_model.pt"
        val file = File(context.cacheDir, assetFilePath)
        context.assets.open(assetFilePath).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Загружаем модель
        module = Module.load(file.absolutePath)
    }

    fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0f
        for (i in logits.indices) {
            exps[i] = exp(logits[i] - maxLogit)
            sum += exps[i]
        }
        return FloatArray(logits.size) { exps[it] / sum }
    }

    fun predict(bitmap: Bitmap): Prediction {
        val resized = bitmap.scale(224, 224)
        val floatValues = FloatArray(224 * 224 * 3)

        // Заполняем каналы отдельно: сначала все R, потом G, потом B
        var i = 0
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized[x, y]
                floatValues[i] = Color.red(pixel).toFloat()
                i++
            }
        }
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized[x, y]
                floatValues[i] = Color.green(pixel).toFloat()
                i++
            }
        }
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized[x, y]
                floatValues[i] = Color.blue(pixel).toFloat()
                i++
            }
        }

        // Создаём тензор в формате NCHW: [1, 3, 224, 224]
        val inputTensor = Tensor.fromBlob(floatValues, longArrayOf(1, 3, 224, 224))

        // Инференс
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val logits = outputTensor.dataAsFloatArray
        val probs = softmax(logits)

        val age = probs.withIndex().fold(0f) { acc, iv ->
            acc + iv.index * iv.value
        }.toInt()

        Log.d("Model", "Age: $age")
        return Prediction(age)
    }

    data class Prediction(val age: Int)
}