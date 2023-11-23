package com.dagger.facerecognition.utils.face_recognition

import android.graphics.Bitmap
import com.dagger.facerecognition.entities.ui.Recognition
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FaceRecognitionUtils {
    private fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Double {
        val v1: RealVector = ArrayRealVector(vector1.map { it.toDouble() }.toDoubleArray())
        val v2: RealVector = ArrayRealVector(vector2.map { it.toDouble() }.toDoubleArray())

        // Compute cosine similarity
        return v1.dotProduct(v2) / (v1.norm * v2.norm)
    }

    fun cosineSimilarityArray(array1: Array<FloatArray>, array2: Array<FloatArray>): Double {
        require(array1.size == array2.size) { "Arrays must have the same size" }

        var totalSimilarity = 0.0

        for (i in array1.indices) {
            require(array1[i].size == array2[i].size) { "Inner arrays must have the same size" }

            val similarity = cosineSimilarity(array1[i], array2[i])
            totalSimilarity += similarity
        }

        return totalSimilarity / array1.size
    }

    fun getImageByteBuffer(imageBitmap: Bitmap): ByteBuffer {
        val imageBuffer = ByteBuffer.allocateDirect(1 * FaceRecognitionHelperImpl.INPUT_SIZE * FaceRecognitionHelperImpl.INPUT_SIZE * 3 * 4)
        imageBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(FaceRecognitionHelperImpl.INPUT_SIZE * FaceRecognitionHelperImpl.INPUT_SIZE)
        imageBitmap.getPixels(
            intValues,
            0,
            imageBitmap.width,
            0,
            0,
            imageBitmap.width,
            imageBitmap.height
        )
        imageBuffer.rewind()
        val isModelQuantized = false
        for (i in 0 until FaceRecognitionHelperImpl.INPUT_SIZE) {
            for (j in 0 until FaceRecognitionHelperImpl.INPUT_SIZE) {
                val pixelValue: Int = intValues.get(i * FaceRecognitionHelperImpl.INPUT_SIZE + j)
                if (isModelQuantized) {
                    // Quantized model
                    imageBuffer.put((pixelValue shr 16 and 0xFF).toByte())
                    imageBuffer.put((pixelValue shr 8 and 0xFF).toByte())
                    imageBuffer.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imageBuffer.putFloat(((pixelValue shr 16 and 0xFF) - FaceRecognitionHelperImpl.IMAGE_MEAN) / FaceRecognitionHelperImpl.IMAGE_STD)
                    imageBuffer.putFloat(((pixelValue shr 8 and 0xFF) - FaceRecognitionHelperImpl.IMAGE_MEAN) / FaceRecognitionHelperImpl.IMAGE_STD)
                    imageBuffer.putFloat(((pixelValue and 0xFF) - FaceRecognitionHelperImpl.IMAGE_MEAN) / FaceRecognitionHelperImpl.IMAGE_STD)
                }
            }
        }
        return imageBuffer
    }

    fun findResembledFace(faceVector: Array<FloatArray>, registeredList: List<Recognition>): Triple<Boolean, Recognition?, String> {
        val recognition = mutableListOf<Recognition>()
        var similarityLogs = ""
        for (faceData in registeredList) {
            val similarity = cosineSimilarityArray(faceVector, faceData.vector)
            similarityLogs += "${faceData.title}: ${similarity}\n"
            if (similarity >= 0.8) {
                recognition.add(faceData.copy(similarity = similarity))
            }
        }
        return if (recognition.isNotEmpty()) {
            val result = recognition.maxBy { it.similarity }
            Triple(true, result, similarityLogs)
        } else {
            Triple(false, null, similarityLogs)
        }
    }

}