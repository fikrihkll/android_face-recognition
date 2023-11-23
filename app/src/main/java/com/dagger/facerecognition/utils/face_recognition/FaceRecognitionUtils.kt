package com.dagger.facerecognition.utils.face_recognition

import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector

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
}