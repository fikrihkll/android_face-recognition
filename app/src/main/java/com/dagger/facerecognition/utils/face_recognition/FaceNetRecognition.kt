package com.dagger.facerecognition.utils.face_recognition

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.entities.ui.ModelInfo
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
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class FaceNetRecognition
@Inject
constructor(
    context: Context,
    val modelInfo: ModelInfo
): FaceRecognitionHelper {

    // Input image size for FaceNet model.
    private val imgSize = modelInfo.inputDims
    // Output embedding size
    val embeddingDim = modelInfo.outputDims

    private val imageTensorProcessor = ImageProcessor.Builder()
        .add( ResizeOp( imgSize , imgSize , ResizeOp.ResizeMethod.BILINEAR ) )
        .add( StandardizeOp() )
        .build()
    private lateinit var interpreter: Interpreter
    lateinit var coroutineScope: CoroutineScope
    private lateinit var faceRecognitionJob: Job
    private lateinit var faceDetectionJob: Job
    private var listener: FaceRecognitionCallback? = null
    lateinit var faceDetectionHelper: FaceDetectionHelper

    init {
        val interpreterOptions = Interpreter.Options().apply {
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate())
            } else {
                // Number of threads for computation
                setNumThreads(4)
            }
            setUseXNNPACK(true)
            setUseNNAPI(true)
        }

        val assetManager = context.assets
        val model = FileUtil.loadMappedFile(context, modelInfo.assetsFilename)
        interpreter = Interpreter(model, interpreterOptions)
    }

    override fun recognizeFace(context: Context, image: Bitmap, face: Face, registeredList: List<Recognition>) {}

    override fun recognizeFace(context: Context, image: Bitmap, registerdList: List<Recognition>) {}

    override fun registerFace(image: Bitmap, name: String) {}

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
                    val newCroppedImage = BitmapUtils.cropImageFaceBitmapWithoutResize(image1, face1.await().first())
                    newCroppedImage?.let {
                        return@async getFaceEmbedding(it)
                    } ?: run {
                        return@async null
                    }
                }
                val vectorImage2 = async {
                    val newCroppedImage = BitmapUtils.cropImageFaceBitmapWithoutResize(image2, face2.await().first())
                    newCroppedImage?.let {
                        return@async getFaceEmbedding(it)
                    } ?: run {
                        return@async null
                    }
                }
                val nameScoreHashmap = HashMap<String,ArrayList<Float>>()
                val metricToBeUsed = "l2"
                var subject = FloatArray( 128 )
                if (vectorImage1.await() != null && vectorImage2.await() != null) {
                    val finalVector2 = vectorImage2.await()!![0]
                    subject = vectorImage1.await()!![0]
                    val faceList = listOf<Pair<String, FloatArray>>(Pair("Image 2", finalVector2))
                    for (i in faceList.indices) {
                        // If this cluster ( i.e an ArrayList with a specific key ) does not exist,
                        // initialize a new one.
                        if ( nameScoreHashmap[ faceList[ i ].first ] == null ) {
                            // Compute the L2 norm and then append it to the ArrayList.
                            val p = ArrayList<Float>()
                            if ( metricToBeUsed == "cosine" ) {
                                p.add( cosineSimilarity( subject , faceList[ i ].second ) )
                            }
                            else {
                                p.add( L2Norm( subject , faceList[ i ].second ) )
                            }
                            nameScoreHashmap[ faceList[ i ].first ] = p
                        }
                        // If this cluster exists, append the L2 norm/cosine score to it.
                        else {
                            if ( metricToBeUsed == "cosine" ) {
                                nameScoreHashmap[faceList[ i ].first]?.add( cosineSimilarity( subject , faceList[ i ].second ) )
                            }
                            else {
                                nameScoreHashmap[faceList[ i ].first]?.add( L2Norm( subject , faceList[ i ].second ) )
                            }
                        }
                    }

                    // Compute the average of all scores norms for each cluster.
                    val avgScores = nameScoreHashmap.values.map { scores -> scores.toFloatArray().average() }
                    Log.i("FKR-CHECK", "Average score for each user : $nameScoreHashmap")

                    val strSim = "$nameScoreHashmap"
                    val names = nameScoreHashmap.keys.toTypedArray()
                    nameScoreHashmap.clear()

                    // Calculate the minimum L2 distance from the stored average L2 norms.
                    val bestScoreUserName = if ( avgScores.minOrNull()!! > 10f ) {
                        "Unknown"
                    }
                    else {
                        names[ avgScores.indexOf( avgScores.minOrNull()!! ) ]
                    }
                    Log.i("FKR-CHECK",  "Person identified as $bestScoreUserName")

                    listener?.onIdenticalCheckFinished(bestScoreUserName != "Unknown", "similarity: ${strSim}")
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
        val t1 = System.currentTimeMillis()
        val faceNetModelOutputs = Array( 1 ) { FloatArray( embeddingDim ) }
        val input = convertBitmapToBuffer(bitmap)
        interpreter.run(input, faceNetModelOutputs)
        Log.i( "Performance" , "${modelInfo.name} Inference Speed in ms : ${System.currentTimeMillis() - t1}")
        return faceNetModelOutputs
    }

    private fun convertBitmapToBuffer( image : Bitmap) : ByteBuffer {
        return imageTensorProcessor.process( TensorImage.fromBitmap( image ) ).buffer
    }

    private fun L2Norm( x1 : FloatArray, x2 : FloatArray ) : Float {
        return sqrt( x1.mapIndexed{ i , xi -> (xi - x2[ i ]).pow( 2 ) }.sum() )
    }


    // Compute the cosine of the angle between x1 and x2.
    private fun cosineSimilarity( x1 : FloatArray , x2 : FloatArray ) : Float {
        val mag1 = sqrt( x1.map { it * it }.sum() )
        val mag2 = sqrt( x2.map { it * it }.sum() )
        val dot = x1.mapIndexed{ i , xi -> xi * x2[ i ] }.sum()
        return dot / (mag1 * mag2)
    }

}

class StandardizeOp : TensorOperator {

    override fun apply(p0: TensorBuffer?): TensorBuffer {
        val pixels = p0!!.floatArray
        val mean = pixels.average().toFloat()
        var std = sqrt( pixels.map{ pi -> ( pi - mean ).pow( 2 ) }.sum() / pixels.size.toFloat() )
        std = max( std , 1f / sqrt( pixels.size.toFloat() ))
        for ( i in pixels.indices ) {
            pixels[ i ] = ( pixels[ i ] - mean ) / std
        }
        val output = TensorBufferFloat.createFixedSize( p0.shape , DataType.FLOAT32 )
        output.loadArray( pixels )
        return output
    }

}