package com.dagger.facerecognition.utils.face_recognition

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dagger.facerecognition.entities.ui.Recognition
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer

interface FaceRecognitionHelper {
    fun recognizeFace(context: Context, image: Bitmap, face: Face, registeredList: List<Recognition>)
    fun recognizeFace(context: Context, image: Bitmap, registeredList: List<Recognition>)
    fun isIdentical(context: Context, image1: Bitmap, image2: Bitmap)
    fun registerFace(image: Bitmap, name: String)
    fun getFaceEmbedding(bitmap: Bitmap): Array<FloatArray>
    fun setListener(listener: FaceRecognitionCallback)
    fun isProcessingRecognition(): Boolean
    fun isProcessingDetection(): Boolean

}

interface FaceRecognitionCallback {
    /**
     * FaceRecognitionState
     * - [FaceRecognitionSuccess]
     * - [FaceRecognitionError]
     * - [FaceRecognitionNotFound]
     * */
    fun onFaceDetectionFinished(faces: List<Face>, image: Bitmap)
    fun onFaceRecognitionFinished(state: FaceRecognitionState)
    fun onFaceRegistrationFinished(state: FaceRecognitionState)
    fun onIdenticalCheckFinished(isIdentical: Boolean, message: String)
}