package com.dagger.facerecognition.utils.face_recognition

import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.entities.ui.ModelInfo
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FaceComparisonHelper
@Inject
constructor(
    private val faceRecognitionHelper: FaceRecognitionUtility,
    private val faceDetectionHelper: FaceDetectionHelper
) {

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var cpuDispatcher: CoroutineDispatcher
    private lateinit var listener: FaceComparisonResultListener
    private var isModelReady = false
    private lateinit var modelInfo: ModelInfo

    fun init(
        coroutineScope: CoroutineScope,
        modelInfo: ModelInfo,
        listener: FaceComparisonResultListener
    ) {
        isModelReady = false
        this.listener = listener
        this.coroutineScope = coroutineScope
        this.modelInfo = modelInfo
        cpuDispatcher = Dispatchers.Default
    }

    private fun reInitModel(
        modelInfo: ModelInfo,
    ) {
        isModelReady = false
        this.modelInfo = modelInfo
    }

    fun compareWithAllModels(
        image1: Bitmap,
        image2: Bitmap,
        metricToBeUsed: String = "l2",
    ) {
        coroutineScope.launch(Dispatchers.Default) {
            val inputImage1 = InputImage.fromBitmap(image1 , 0)
            val inputImage2 = InputImage.fromBitmap(image2 , 0)

            val face1 = faceDetectionHelper.findFace(inputImage1)
            val face2 = faceDetectionHelper.findFace(inputImage2)

            if (face1.isEmpty() || face2.isEmpty()) return@launch

            val croppedFace1 = BitmapUtils.cropImageFaceBitmapWithoutResize(image1, face1.first())
            val croppedFace2 = BitmapUtils.cropImageFaceBitmapWithoutResize(image2, face2.first())

            if (croppedFace1 == null || croppedFace2 == null) return@launch
            withContext(Dispatchers.Main) {
                listener.onImageCropped(croppedFace1, croppedFace2)
            }

            var finalResult = ""
            // FaceNet
            reInitModel(FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET))
            val resultFaceNet = processCroppedFace(
                croppedFace1,
                croppedFace2,
                metricToBeUsed,
                modelInfo.cosineThreshold,
                modelInfo.l2Threshold
            )
            finalResult += """

                FaceNet: (${resultFaceNet.second})
                ${if (resultFaceNet.first) "Identical" else "Not identical"}
            """.trimIndent()

            // FaceNet512
            reInitModel(FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET_512))
            val resultFaceNet512 = processCroppedFace(
                croppedFace1,
                croppedFace2,
                metricToBeUsed,
                modelInfo.cosineThreshold,
                modelInfo.l2Threshold
            )
            finalResult += """

                FaceNet512: (${resultFaceNet512.second})
                ${if (resultFaceNet512.first) "Identical" else "Not identical"}
            """.trimIndent()
//
            // FaceNet512Quantized
            reInitModel(FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET_512_QUANTIZED))
            val resultFaceNet512Quantized = processCroppedFace(
                croppedFace1,
                croppedFace2,
                metricToBeUsed,
                modelInfo.cosineThreshold,
                modelInfo.l2Threshold
            )
            finalResult += """

                FaceNet512Quantized: (${resultFaceNet512Quantized.second})
                ${if (resultFaceNet512Quantized.first) "Identical" else "Not identical"}
            """.trimIndent()

            // FaceNet512Quantized
            reInitModel(FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET_QUANTIZED))
            val resultFaceNetQuantized = processCroppedFace(
                croppedFace1,
                croppedFace2,
                metricToBeUsed,
                modelInfo.cosineThreshold,
                modelInfo.l2Threshold
            )
            finalResult += """

                FaceNetQuantized: (${resultFaceNetQuantized.second})
                ${if (resultFaceNetQuantized.first) "Identical" else "Not identical"}
            """.trimIndent()

            // MobileFaceNet
            reInitModel(FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.MOBILE_FACE_NET))
            val resultMobileFaceNet = processCroppedFace(
                croppedFace1,
                croppedFace2,
                metricToBeUsed,
                modelInfo.cosineThreshold,
                modelInfo.l2Threshold
            )
            finalResult += """

                MobileFaceNet: (${resultMobileFaceNet.second})
                ${if (resultMobileFaceNet.first) "Identical" else "Not identical"}
            """.trimIndent()
            
            withContext(Dispatchers.Main) {
                listener.onComparingFinished(finalResult)
            }
        }
    }

    private fun processCroppedFace(
        croppedFace1: Bitmap,
        croppedFace2: Bitmap,
        metricToBeUsed: String = "l2",
        cosineThreshold: Float,
        l2Threshold: Float
    ): Pair<Boolean, Double> {
        if (!isModelReady) {
            faceRecognitionHelper.setModel(modelInfo)
            faceDetectionHelper.init()
            faceRecognitionHelper.init()
            isModelReady = true
        }

        val t1 = System.currentTimeMillis()
        val faceVector1 = faceRecognitionHelper.getFaceEmbedding(croppedFace1)[0]
        val faceVector2 = faceRecognitionHelper.getFaceEmbedding(croppedFace2)[0]

        val nameScoreHashmap = FaceRecognitionUtils.calculateScore(
            metricToBeUsed = metricToBeUsed,
            subject = faceVector1,
            faceList = listOf(Pair("Comparator", faceVector2))
        )
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


interface FaceComparisonResultListener {

    fun onComparingFinished(result: String)
    fun onImageCropped(image1: Bitmap, image2: Bitmap)

}