package com.dagger.facerecognition.utils.face_recognition

import android.graphics.Bitmap
import com.dagger.facerecognition.entities.ui.ModelInfo

interface FaceRecognitionUtility {
    fun setModel(modelInfo: ModelInfo)
    fun getFaceEmbedding(bitmap: Bitmap): Array<FloatArray>
    fun init()

}