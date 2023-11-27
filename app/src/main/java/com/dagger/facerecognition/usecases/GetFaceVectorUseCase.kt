package com.dagger.facerecognition.usecases

import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtility
import com.google.mlkit.vision.common.InputImage
import java.util.UUID
import javax.inject.Inject

class GetFaceVectorUseCase
@Inject
constructor(
    private val faceRecognitionHelper: FaceRecognitionUtility,
    private val faceDetectionHelper: FaceDetectionHelper,
) {

    suspend fun execute(
        frameBitmap: Bitmap,
        firstFaceOnly: Boolean = true
    ): List<Recognition> {
        val inputImage = InputImage.fromBitmap(frameBitmap , 0)
        val faces = faceDetectionHelper.findFace(inputImage)
        val t1 = System.currentTimeMillis()
        val recognitions = mutableListOf<Recognition>()

        val finalFaces = if (firstFaceOnly) listOf(faces.first()) else faces
        for (face in finalFaces) {
            try {
                BitmapUtils.cropImageFaceBitmapWithoutResize(frameBitmap, face)?.let {
                    val subject = faceRecognitionHelper.getFaceEmbedding(it)
                    recognitions.add(Recognition(id = UUID.randomUUID().toString(), title = "", vector = subject))
                    Log.e( "Performance" , "Inference time -> ${System.currentTimeMillis() - t1}")
                }
            } catch ( e : Exception ) {
                Log.e( "Model" , "Exception in FrameAnalyser : ${e.message}" )
                continue
            }

        }
        return recognitions
    }

}