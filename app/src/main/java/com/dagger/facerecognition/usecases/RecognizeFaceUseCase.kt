package com.dagger.facerecognition.usecases

import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.entities.ui.FacePrediction
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtility
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtils
import com.google.mlkit.vision.common.InputImage
import javax.inject.Inject

class RecognizeFaceUseCase
@Inject
constructor(
    private val faceRecognitionHelper: FaceRecognitionUtility,
    private val faceDetectionHelper: FaceDetectionHelper,
) {

    suspend fun execute(
        metricToBeUsed: String = "l2",
        frameBitmap: Bitmap,
        registeredFace: List<Pair<String,FloatArray>>,
        cosineThreshold: Float,
        l2Threshold: Float,
        firstFaceOnly: Boolean = false
    ): List<FacePrediction> {
        if (registeredFace.isEmpty()) return listOf()

        val inputImage = InputImage.fromBitmap(frameBitmap , 0)
        val faces = faceDetectionHelper.findFace(inputImage)
        if (faces.isEmpty()) return listOf()
        val t1 = System.currentTimeMillis()
        val predictions = mutableListOf<FacePrediction>()

        val finalFaces = if (firstFaceOnly) listOf(faces.first()) else faces
        for (face in finalFaces) {
            try {
                val croppedBitmap = BitmapUtils.cropImageFaceBitmapWithoutResize(frameBitmap, face)
                val subject = faceRecognitionHelper.getFaceEmbedding(croppedBitmap!!)[0]

                val nameScoreHashmap = FaceRecognitionUtils.calculateScore(subject = subject, faceList = registeredFace)
                var strFinal = """
                                Average score for each user : $nameScoreHashmap
                            """.trimIndent()

                val bestScoreUserName = FaceRecognitionUtils.getPersonNameFromAverageScore(
                    metricToBeUsed,
                    nameScoreHashmap,
                    cosineThreshold,
                    l2Threshold
                )
                Log.i("FKR-CHECK",  "Person identified as ${bestScoreUserName.first}")
                predictions.add(
                    FacePrediction(
                        boundingBox = face.boundingBox,
                        label =  bestScoreUserName.first,
                        score = bestScoreUserName.second
                    )
                )
                strFinal += "\n\nPerson identified as ${bestScoreUserName.first}"
                Log.i("FKR-CHECK",  "RECOGNITION RESULT:\n${strFinal}")
                Log.e( "Performance" , "Inference time -> ${System.currentTimeMillis() - t1}")
            } catch ( e : Exception ) {
                Log.e( "Model" , "Exception in FrameAnalyser : ${e.message}" )
                continue
            }
        }
        return predictions
    }

}