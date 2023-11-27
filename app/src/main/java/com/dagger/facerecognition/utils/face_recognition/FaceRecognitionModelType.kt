package com.dagger.facerecognition.utils.face_recognition

import com.dagger.facerecognition.entities.ui.ModelInfo

object FaceRecognitionModel {

    enum class Type {
        FACE_NET,
        MOBILE_FACE_NET,
        FACE_NET_QUANTIZED,
        FACE_NET_512,
        FACE_NET_512_QUANTIZED,
    }

    private val faceNetModel = ModelInfo(
        "FaceNet" ,
        "facenet.tflite" ,
        0.4f ,
        10f ,
        128 ,
        160
    )
    private val mobileFaceNetModel = ModelInfo(
        "MobileFaceNet" ,
        "mobile_face_net.tflite" ,
        0.4f ,
        10f ,
        192 ,
        112
    )
    private val faceNet512 = ModelInfo(
        "FaceNet-512" ,
        "facenet_512.tflite" ,
        0.3f ,
        23.56f ,
        512 ,
        160
    )
    private val faceNetQuantized = ModelInfo(
        "FaceNet Quantized" ,
        "facenet_int_quantized.tflite" ,
        0.4f ,
        10f ,
        128 ,
        160
    )
    private val faceNet512Quantized = ModelInfo(
        "FaceNet-512 Quantized" ,
        "facenet_512_int_quantized.tflite" ,
        0.3f ,
        23.56f ,
        512 ,
        160
    )

    fun getModelInfo(model: Type): ModelInfo {
        return when (model) {
            Type.FACE_NET -> {
                faceNetModel
            }
            Type.MOBILE_FACE_NET -> {
                mobileFaceNetModel
            }
            Type.FACE_NET_512 -> {
                faceNet512
            }
            Type.FACE_NET_QUANTIZED -> {
                faceNetQuantized
            }
            Type.FACE_NET_512_QUANTIZED -> {
                faceNet512Quantized
            }
        }
    }

}