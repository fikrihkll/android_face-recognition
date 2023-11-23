package com.dagger.facerecognition.utils.face_recognition

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

class FaceRecognitionHelperImpl
@Inject
constructor(): FaceRecognitionHelper {

    companion object {
        const val modelName = "high-pre-model.tflite"
        
        const val OUTPUT_SIZE = 192
        const val INPUT_SIZE = 112
        const val IMAGE_MEAN = 128.0f
        const val IMAGE_STD = 128.0f
    }

    private lateinit var interpreter: Interpreter
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var faceRecognitionJob: Job
    private lateinit var faceDetectionJob: Job
    private var listener: FaceRecognitionCallback? = null
    @Inject
    lateinit var faceDetectionHelper: FaceDetectionHelper

    fun initialize(context: Context, coroutineScope: CoroutineScope) {
        // load model
        try {
            this.coroutineScope = coroutineScope
            val interpreterOptions = Interpreter.Options()
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                interpreterOptions.addDelegate(GpuDelegate())
            }
            val assetManager = context.assets
            val model = loadModelFile(assetManager, modelName)
            interpreter = Interpreter(model, interpreterOptions)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Initialize Face Detector
        faceDetectionHelper.initialize()
    }

    override fun recognizeFace(context: Context, image: Bitmap, face: Face, registeredList: List<Recognition>) {
        if (::faceRecognitionJob.isInitialized && faceRecognitionJob.isActive) {
            listener?.onFaceRecognitionFinished(FaceRecognitionError("Process is being executed"))
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            BitmapUtils.cropImageFaceBitmap(image, face)?.let { croppedFaceBitmap ->
                withContext(Dispatchers.Main) {
                    val inputVector = getFaceEmbedding(croppedFaceBitmap)
                    val matchingResult = findResembledFace(inputVector, registeredList)
                    handleFindingResembledResult(matchingResult)
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    listener?.onFaceRecognitionFinished(FaceRecognitionError(message = "Something wrong!"))
                }
            }
        }
    }

    override fun recognizeFace(context: Context, image: Bitmap, registerdList: List<Recognition>) {
        if (::faceRecognitionJob.isInitialized && faceRecognitionJob.isActive) {
            listener?.onFaceRecognitionFinished(FaceRecognitionError("Process is being executed"))
            return
        }

        faceRecognitionJob = coroutineScope.launch(Dispatchers.IO) {
            val faces = faceDetectionHelper.findFace(image)
            if (faces.isEmpty()) {
                val face = faces.first()
                BitmapUtils.cropImageFaceBitmap(image, face)?.let { croppedFaceBitmap ->
                    withContext(Dispatchers.Main) {
                        val inputVector = getFaceEmbedding(croppedFaceBitmap)
                        val matchingResult = findResembledFace(inputVector, registerdList)
                        handleFindingResembledResult(matchingResult)
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        listener?.onFaceRecognitionFinished(FaceRecognitionError(message = "Something wrong!"))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    listener?.onFaceRecognitionFinished(FaceRecognitionError(message = "No face detected"))
                }
            }
        }
    }

    override fun registerFace(image: Bitmap, name: String) {
        if (::faceRecognitionJob.isInitialized && faceRecognitionJob.isActive) {
            listener?.onFaceRegistrationFinished(FaceRecognitionError("Process is being executed"))
            return
        }

        faceRecognitionJob = coroutineScope.launch(Dispatchers.IO) {
            val faces = faceDetectionHelper.findFace(image)
            if (faces.isNotEmpty()) {
                val face = faces.first()
                BitmapUtils.cropImageFaceBitmap(image.copy(Bitmap.Config.ARGB_8888, true), face)?.let { croppedFaceBitmap ->
                    withContext(Dispatchers.Main) {
                        val id = UUID.randomUUID().toString()
                        val result = Recognition(
                            id = id,
                            title = name,
                            vector = getFaceEmbedding(croppedFaceBitmap)
                        )
                        listener?.onFaceRegistrationFinished(FaceRecognitionSuccess(data = result))
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        listener?.onFaceRegistrationFinished(FaceRecognitionError(message = "Something wrong!"))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    listener?.onFaceRegistrationFinished(FaceRecognitionError(message = "No face detected!"))
                }
            }
        }
    }
    
    override fun setListener(listener: FaceRecognitionCallback) {
        this.listener = listener
    }

    override fun isProcessingRecognition(): Boolean {
        return ::faceRecognitionJob.isInitialized && faceRecognitionJob.isActive
    }

    override fun isProcessingDetection(): Boolean {
        return ::faceDetectionJob.isInitialized && faceDetectionJob.isActive
    }

    override fun isIdentical(context: Context, image1: Bitmap, image2: Bitmap) {
        coroutineScope.launch {
            val face1 = async(Dispatchers.IO) {
                faceDetectionHelper.findFace(image1)
            }
            val face2 = async(Dispatchers.IO) {
                faceDetectionHelper.findFace(image2)
            }

            if (face1.await().isNotEmpty() && face2.await().isNotEmpty()) {
                val vectorImage1 = async {
                    val newCroppedImage = BitmapUtils.cropImageFaceBitmap(image1, face1.await().first())
                    newCroppedImage?.let {
                        return@async getFaceEmbedding(it)
                    } ?: run {
                        return@async null
                    }
                }
                val vectorImage2 = async {
                    val newCroppedImage = BitmapUtils.cropImageFaceBitmap(image2, face2.await().first())
                    newCroppedImage?.let {
                        return@async getFaceEmbedding(it)
                    } ?: run {
                        return@async null
                    }
                }
                if (vectorImage1.await() != null && vectorImage2.await() != null) {
                    val resembledFace = findResembledFace(
                        vectorImage1.await()!!,
                        listOf(
                            Recognition(
                                id = UUID.randomUUID().toString(),
                                title = "Image 2",
                                vector = vectorImage2.await()!!
                            )
                        )
                    )

                    listener?.onIdenticalCheckFinished(resembledFace.first, "similarity: ${resembledFace.second?.similarity}")
                } else {
                    listener?.onIdenticalCheckFinished(false, "Something wrong!")
                }
            } else {
                listener?.onIdenticalCheckFinished(false, "Something wrong!")
            }
        }
    }

    private fun handleFindingResembledResult(matchingResult: Triple<Boolean, Recognition?, String>) {
        if (matchingResult.first) {
            matchingResult.second?.let {
                listener?.onFaceRecognitionFinished(FaceRecognitionSuccess(data = it))

            } ?: run {
                listener?.onFaceRecognitionFinished(FaceRecognitionError(message = "Face not found!"))
            }
        } else {
            listener?.onFaceRecognitionFinished(FaceRecognitionNotFound(message = matchingResult.third))
        }
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream?.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        val buffer = fileChannel?.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )

        fileChannel?.close()
        return buffer ?: throw RuntimeException("Error loading model file.")
    }

    override fun getFaceEmbedding(bitmap: Bitmap): Array<FloatArray> {
        val inputArray = arrayOf(FaceRecognitionUtils.getImageByteBuffer(bitmap))
        val outputMap: MutableMap<Int, Any> = HashMap()
        val embeeding = Array(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeeding

        interpreter.runForMultipleInputsOutputs(inputArray, outputMap)
        return embeeding
    }

    private fun findResembledFace(faceVector: Array<FloatArray>, registerdList: List<Recognition>): Triple<Boolean, Recognition?, String> {
        val recognition = mutableListOf<Recognition>()
        var similarityLogs = ""
        for (faceData in registerdList) {
            val similarity = FaceRecognitionUtils.cosineSimilarityArray(faceVector, faceData.vector)
            similarityLogs += "${faceData.title}: ${similarity}\n"
            if (similarity >= 0.8) {
                recognition.add(faceData.copy(similarity = similarity))
            }
        }
        return if (recognition.isNotEmpty()) {
            val result = recognition.maxBy { it.similarity }
            Triple(true, result, similarityLogs)
        } else {
            Triple(false, null, similarityLogs)
        }
    }

}