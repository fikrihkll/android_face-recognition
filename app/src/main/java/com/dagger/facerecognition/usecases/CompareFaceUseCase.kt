package com.dagger.facerecognition.usecases

import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtility
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtils
import com.google.mlkit.vision.common.InputImage
import javax.inject.Inject

class CompareFaceUseCase
@Inject
constructor(
    private val faceRecognitionHelper: FaceRecognitionUtility,
    private val faceDetectionHelper: FaceDetectionHelper,
) {

    suspend fun execute(
        metricToBeUsed: String = "l2",
        image1: Bitmap,
        image2: Bitmap,
        cosineThreshold: Float,
        l2Threshold: Float
    ): Pair<Boolean, Double> {
        val inputImage1 = InputImage.fromBitmap(image1 , 0)
        val inputImage2 = InputImage.fromBitmap(image2 , 0)

        val face1 = faceDetectionHelper.findFace(inputImage1)
        val face2 = faceDetectionHelper.findFace(inputImage2)

        if (face1.isEmpty() || face2.isEmpty()) return Pair(false, 0.0)
        val t1 = System.currentTimeMillis()

        val croppedFace1 = BitmapUtils.cropImageFaceBitmapWithoutResize(image1, face1.first())
        val croppedFace2 = BitmapUtils.cropImageFaceBitmapWithoutResize(image2, face2.first())

        if (croppedFace1 == null || croppedFace2 == null) return Pair(false, 0.0)

        val faceVector1 = faceRecognitionHelper.getFaceEmbedding(croppedFace1)[0]
        val faceVector2 = faceRecognitionHelper.getFaceEmbedding(croppedFace2)[0]

        val nameScoreHashmap = FaceRecognitionUtils.calculateScore(subject = faceVector1, faceList = listOf(Pair("Comparator", faceVector2)))
        val bestScoreUserName = FaceRecognitionUtils.getPersonNameFromAverageScore(
            metricToBeUsed,
            nameScoreHashmap,
            cosineThreshold,
            l2Threshold
        )
        Log.e( "Performance" , "Inference time -> ${System.currentTimeMillis() - t1}")
        return Pair(bestScoreUserName.first != "Unknown", bestScoreUserName.second)
    }

}