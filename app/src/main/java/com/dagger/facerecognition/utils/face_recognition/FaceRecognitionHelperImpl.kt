package com.dagger.facerecognition.utils.face_recognition

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.camera.core.ImageAnalysis
import com.dagger.facerecognition.entities.ui.Recognition
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
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    private lateinit var detector: FaceDetector
    private lateinit var faceDetectorOptions: FaceDetectorOptions
    private lateinit var interpreter: Interpreter
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var faceRecognitionJob: Job
    private lateinit var faceDetectionJob: Job
    private var listener: FaceRecognitionCallback? = null

    override fun initialize(context: Context, coroutineScope: CoroutineScope) {
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
        faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(faceDetectorOptions)
    }

    override fun recognizeFace(context: Context, image: Bitmap, face: Face, registeredList: List<Recognition>) {
        if (::faceRecognitionJob.isInitialized && faceRecognitionJob.isActive) {
            listener?.onFaceRecognitionFinished(FaceRecognitionError("Process is being executed"))
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            cropImageFaceBitmap(image, face)?.let { croppedFaceBitmap ->
                withContext(Dispatchers.Main) {
                    val inputVector = predictWithModel(croppedFaceBitmap)
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
            val faces = findFace(image)
            if (faces.isEmpty()) {
                val face = faces.first()
                cropImageFaceBitmap(image, face)?.let { croppedFaceBitmap ->
                    withContext(Dispatchers.Main) {
                        val inputVector = predictWithModel(croppedFaceBitmap)
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
            val faces = findFace(image)
            if (faces.isNotEmpty()) {
                val face = faces.first()
                cropImageFaceBitmap(image.copy(Bitmap.Config.ARGB_8888, true), face)?.let { croppedFaceBitmap ->
                    withContext(Dispatchers.Main) {
                        val id = UUID.randomUUID().toString()
                        val result = Recognition(
                            id = id,
                            title = name,
                            vector = predictWithModel(croppedFaceBitmap)
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

    override fun detectFace(bitmap: Bitmap) {
        faceDetectionJob = coroutineScope.launch {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = findFace(inputImage)

            listener?.onFaceDetectionFinished(result, bitmap)
        }
    }

    override fun getBitmapFromUri(uri: Uri, context: Context, result: (Bitmap) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val parcelFileDescriptor: ParcelFileDescriptor? =
                context.contentResolver.openFileDescriptor(uri, "r")
            val fileDescriptor = parcelFileDescriptor?.fileDescriptor
            var image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor?.close()
            if (image.width > image.height) {
               image = rotateBitmap(image, -90, true, false)
            }

            withContext(Dispatchers.Main) {
                result.invoke(image)
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
                findFace(image1)
            }
            val face2 = async(Dispatchers.IO) {
                findFace(image2)
            }

            if (face1.await().isNotEmpty() && face2.await().isNotEmpty()) {
                val vectorImage1 = async {
                    val newCroppedImage = cropImageFaceBitmap(image1, face1.await().first())
                    newCroppedImage?.let {
                        return@async predictWithModel(it)
                    } ?: run {
                        return@async null
                    }
                }
                val vectorImage2 = async {
                    val newCroppedImage = cropImageFaceBitmap(image2, face2.await().first())
                    newCroppedImage?.let {
                        return@async predictWithModel(it)
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

    private suspend fun findFace(image: InputImage): List<Face> {
        return suspendCancellableCoroutine { continuation ->
            detector
                .process(image)
                .addOnSuccessListener { faces ->
                    continuation.resume(faces)
                }
                .addOnFailureListener {
                    continuation.resume(listOf())
                }
        }
    }

    private suspend fun findFace(image: Bitmap): List<Face> {
        return suspendCancellableCoroutine { continuation ->
            val faceDetectionInputImage = InputImage.fromBitmap(image, 0)
            detector
                .process(faceDetectionInputImage)
                .addOnSuccessListener { faces ->
                    continuation.resume(faces)
                }
                .addOnFailureListener {
                    continuation.resume(listOf())
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

    private fun predictWithModel(image: Bitmap): Array<FloatArray> {
        val inputArray = arrayOf(getImageByteBuffer(image))
        val outputMap: MutableMap<Int, Any> = HashMap()
        val embeeding = Array(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeeding

        interpreter.runForMultipleInputsOutputs(inputArray, outputMap)
        return embeeding
    }

    private fun getImageByteBuffer(imageBitmap: Bitmap): ByteBuffer {
        val imageBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        imageBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        imageBitmap.getPixels(
            intValues,
            0,
            imageBitmap.width,
            0,
            0,
            imageBitmap.width,
            imageBitmap.height
        )
        imageBuffer.rewind()
        val isModelQuantized = false
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val pixelValue: Int = intValues.get(i * INPUT_SIZE + j)
                if (isModelQuantized) {
                    // Quantized model
                    imageBuffer.put((pixelValue shr 16 and 0xFF).toByte())
                    imageBuffer.put((pixelValue shr 8 and 0xFF).toByte())
                    imageBuffer.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imageBuffer.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imageBuffer.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imageBuffer.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        return imageBuffer
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

    private fun cropImageFaceBitmap(image: Bitmap, face: Face): Bitmap? {
        val boundingBox = RectF(face.boundingBox)
        val croppedFace = getCropBitmapByBoundingBox(image, boundingBox) ?: run {
            return null
        }
        val scaled = getResizedBitmap(croppedFace, 112, 112) ?: run {
            return null
        }
        return scaled
    }

    override fun rotateBitmap(
        bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean
    ): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    private fun getCropBitmapByBoundingBox(source: Bitmap, cropRectF: RectF): Bitmap? {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        canvas.drawRect(
            RectF(0f, 0f, cropRectF.width(), cropRectF.height()),
            paint
        )
        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)

        // draw image
        canvas.drawBitmap(source, matrix, paint)
        if (!source.isRecycled) {
            source.recycle()
        }
        return resultBitmap
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap? {
        val width = bm.width
        val height = bm.height

        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height

        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }
}