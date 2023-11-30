package com.dagger.facerecognition.utils.face_recognition

import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.entities.ui.FacePrediction
import com.dagger.facerecognition.entities.ui.ModelInfo
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class FaceRecognitionHelper
@Inject
constructor(
    private val faceRecognitionHelper: FaceRecognitionUtility,
    private val faceDetectionHelper: FaceDetectionHelper
) {

    companion object {
        const val TAG = "FaceRecognitionHelper"
    }

    private lateinit var cpuDispatcher: CoroutineDispatcher
    private lateinit var listener: FaceRecognitionResultListener
    private var isModelReady = false
    private lateinit var modelInfo: ModelInfo

    suspend fun init(
        modelInfo: ModelInfo,
        listener: FaceRecognitionResultListener
    ) {
        isModelReady = false
        this.listener = listener
        this.modelInfo = modelInfo
        cpuDispatcher = Dispatchers.Default
        withContext(cpuDispatcher) {
            faceRecognitionHelper.setModel(modelInfo)
            faceDetectionHelper.init()
            faceRecognitionHelper.init()
        }
        isModelReady = true
    }

    suspend fun recognizeFace(
        metricToBeUsed: String = "l2",
        frameBitmap: Bitmap,
        registeredFace: List<Pair<String,FloatArray>>,
        cosineThreshold: Float,
        l2Threshold: Float,
        firstFaceOnly: Boolean = false
    ) {
        withContext(cpuDispatcher) {
            if (!isModelReady) {
                withContext(Dispatchers.Main) {
                    listener.onFaceRecognized(listOf())
                }
                return@withContext
            }
            if (registeredFace.isEmpty()) {
                withContext(Dispatchers.Main) {
                    listener.onFaceRecognized(listOf())
                }
                return@withContext
            }

            val inputImage = InputImage.fromBitmap(frameBitmap , 0)
            val faces = faceDetectionHelper.findFace(inputImage)
            if (faces.isEmpty()) {
                withContext(Dispatchers.Main) {
                    listener.onFaceRecognized(listOf())
                }
                return@withContext
            }

            val t1 = System.currentTimeMillis()
            val predictions = mutableListOf<FacePrediction>()

            val finalFaces = if (firstFaceOnly) listOf(faces.first()) else faces
            for (face in finalFaces) {
                try {
                    val croppedBitmap = BitmapUtils.cropImageFaceBitmapWithoutResize(frameBitmap, face.boundingBox)
                    val subject = withContext(cpuDispatcher) {
                        faceRecognitionHelper.getFaceEmbedding(croppedBitmap)[0]
                    }

                    val nameScoreHashmap = FaceRecognitionUtils.calculateScore(subject = subject, faceList = registeredFace)
                    var strFinal = """
                                ${modelInfo.name}
                                Average score for each user : $nameScoreHashmap
                            """.trimIndent()

                    val bestScoreUserName = FaceRecognitionUtils.getPersonNameFromAverageScore(
                        metricToBeUsed,
                        nameScoreHashmap,
                        cosineThreshold,
                        l2Threshold
                    )
                    Log.i(TAG,  "Person identified as ${bestScoreUserName.first}")
                    predictions.add(
                        FacePrediction(
                            boundingBox = face.boundingBox,
                            label =  bestScoreUserName.first,
                            score = bestScoreUserName.second
                        )
                    )
                    strFinal += "\n\nPerson identified as ${bestScoreUserName.first}"
                    Log.i(TAG,  "RECOGNITION RESULT:\n${strFinal}")
                    Log.e(TAG , "[Performance] Inference time -> ${System.currentTimeMillis() - t1}")
                } catch ( e : Exception ) {
                    Log.e(TAG , "Exception in FrameAnalyser : ${e.message}" )
                    continue
                }
            }
            withContext(Dispatchers.Main) {
                listener.onFaceRecognized(predictions)
            }
        }
    }

    suspend fun getFaceVector(
        frameBitmap: Bitmap,
        firstFaceOnly: Boolean = true
    ) {
        if (!isModelReady) {
            listener.onFaceRecognized(listOf())
            return
        }
        withContext(cpuDispatcher) {
            val inputImage = InputImage.fromBitmap(frameBitmap , 0)
            val faces = faceDetectionHelper.findFace(inputImage)
            val t1 = System.currentTimeMillis()
            val recognitions = mutableListOf<Recognition>()

            val finalFaces = if (firstFaceOnly) listOf(faces.first()) else faces
            for (face in finalFaces) {
                try {
                    BitmapUtils.cropImageFaceBitmapWithoutResize(frameBitmap, face)?.let {
                        val subject = withContext(cpuDispatcher) {
                            faceRecognitionHelper.getFaceEmbedding(it)
                        }
                        recognitions.add(Recognition(id = UUID.randomUUID().toString(), title = "", vector = subject))
                        Log.e(TAG , "[Performance] Inference time -> ${System.currentTimeMillis() - t1}")
                    }
                } catch ( e : Exception ) {
                    Log.e(TAG , "Exception in FrameAnalyser : ${e.message}" )
                    continue
                }
            }
            withContext(Dispatchers.Main) {
                listener.onFaceVectorExtracted(recognitions)
            }
        }
    }

}

interface FaceRecognitionResultListener {

    fun onFaceRecognized(result: List<FacePrediction>)
    fun onFaceVectorExtracted(result: List<Recognition>)

}