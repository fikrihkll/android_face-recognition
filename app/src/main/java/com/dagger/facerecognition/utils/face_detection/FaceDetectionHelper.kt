package com.dagger.facerecognition.utils.face_detection

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face

interface FaceDetectionHelper {

    fun init()
    suspend fun findFace(image: InputImage): List<Face>
    suspend fun findFace(image: Bitmap): List<Face>

}