package com.dagger.facerecognition.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dagger.facerecognition.entities.ui.FacePrediction
import com.dagger.facerecognition.entities.ui.ModelInfo
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.modules.FaceNet
import com.dagger.facerecognition.usecases.CompareFaceUseCase
import com.dagger.facerecognition.usecases.GetFaceVectorUseCase
import com.dagger.facerecognition.usecases.RecognizeFaceUseCase
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FaceAnalyzerViewModel
@Inject
constructor(
    private val recognizeFaceUseCase: RecognizeFaceUseCase,
    private val getFaceVectorUseCase: GetFaceVectorUseCase,
    @FaceNet val compareFaceFaceNetUseCase: CompareFaceUseCase,
    private val faceRecognitionHelper: FaceRecognitionUtility,
//    @FaceNetQuantized val compareFaceFaceNetQuantizedUseCase: CompareFaceUseCase,
//    @FaceNet512 val compareFaceFaceNet512UseCase: CompareFaceUseCase,
//    @FaceNet512Quantized val compareFaceFaceNet512QuantizedUseCase: CompareFaceUseCase,
//    @MobileFaceNet val compareFaceMobileFaceNetUseCase: CompareFaceUseCase
): ViewModel() {

    private val faceNetModel: ModelInfo = ModelInfo(
        "FaceNet" ,
        "facenet.tflite" ,
        0.4f ,
        10f ,
        128 ,
        160
    )

    private val _faceComparisonMutableLiveData: MutableLiveData<String> = MutableLiveData()
    val faceComparisonLiveData: LiveData<String> = _faceComparisonMutableLiveData

    private var isProcessing = false

    fun compareFace(image1: Bitmap, image2: Bitmap) {
        isProcessing = true
        val strResult = mutableListOf<String>()

        // FaceNet
        viewModelScope.launch {
            val resultFaceNet = compareFaceFaceNetUseCase.execute(
                image1 = image1,
                image2 = image2,
                cosineThreshold = faceNetModel.cosineThreshold,
                l2Threshold = faceNetModel.l2Threshold,
            )
            withContext(Dispatchers.Main) {
                val str = """
                    ----------------
                    FaceNet: ${resultFaceNet.second}
                    [${if(resultFaceNet.first) "Identical" else "Not Identical"}]
                """.trimIndent()
                strResult.add(str)
                _faceComparisonMutableLiveData.value = strResult.joinToString { "\n\n" }
                isProcessing = false
            }

//            // FaceNetQuantized
//            val resultFaceNetQuantized = compareFaceFaceNetQuantizedUseCase.execute(
//                image1 = image1,
//                image2 = image2,
//                cosineThreshold = faceNetModel.cosineThreshold,
//                l2Threshold = faceNetModel.l2Threshold,
//            )
//            withContext(Dispatchers.Main) {
//                val str = """
//                    ----------------
//                    FaceNetQuantized: ${resultFaceNetQuantized.second}
//                    [${if(resultFaceNetQuantized.first) "Identical" else "Not Identical"}]
//                """.trimIndent()
//                strResult.add(str)
//                _faceComparisonMutableLiveData.value = strResult.joinToString { "\n\n" }
//                isProcessing = false
//            }
//
//            // FaceNet512Quantized
//            val resultFaceNet512Quantized = compareFaceFaceNet512QuantizedUseCase.execute(
//                image1 = image1,
//                image2 = image2,
//                cosineThreshold = faceNetModel.cosineThreshold,
//                l2Threshold = faceNetModel.l2Threshold,
//            )
//            withContext(Dispatchers.Main) {
//                val str = """
//                    ----------------
//                    FaceNet512Quantized: ${resultFaceNet512Quantized.second}
//                    [${if(resultFaceNet512Quantized.first) "Identical" else "Not Identical"}]
//                """.trimIndent()
//                strResult.add(str)
//                _faceComparisonMutableLiveData.value = strResult.joinToString { "\n\n" }
//                isProcessing = false
//            }
//
//            // FaceNet512
//            val resultFaceNet512 = compareFaceFaceNet512UseCase.execute(
//                image1 = image1,
//                image2 = image2,
//                cosineThreshold = faceNetModel.cosineThreshold,
//                l2Threshold = faceNetModel.l2Threshold,
//            )
//            withContext(Dispatchers.Main) {
//                val str = """
//                    ----------------
//                    FaceNet512: ${resultFaceNet512.second}
//                    [${if(resultFaceNet512.first) "Identical" else "Not Identical"}]
//                """.trimIndent()
//                strResult.add(str)
//                _faceComparisonMutableLiveData.value = strResult.joinToString { "\n\n" }
//                isProcessing = false
//            }
//
//            // MobileFaceNet
//            val resultMobileFaceNet = compareFaceMobileFaceNetUseCase.execute(
//                image1 = image1,
//                image2 = image2,
//                cosineThreshold = faceNetModel.cosineThreshold,
//                l2Threshold = faceNetModel.l2Threshold,
//            )
//            withContext(Dispatchers.Main) {
//                val str = """
//                    ----------------
//                    MobileFaceNet: ${resultMobileFaceNet.second}
//                    [${if(resultMobileFaceNet.first) "Identical" else "Not Identical"}]
//                """.trimIndent()
//                strResult.add(str)
//                _faceComparisonMutableLiveData.value = strResult.joinToString { "\n\n" }
//                isProcessing = false
//            }
        }
    }

}