package com.dagger.facerecognition.utils.face_recognition

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.dagger.facerecognition.entities.ui.ModelInfo
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import javax.inject.Inject

class FaceRecognitionUtilityImpl
@Inject
constructor(
    private val context: Context
): FaceRecognitionUtility {

    private var modelInfo: ModelInfo = FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET)
    private var imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(modelInfo.inputDims, modelInfo.inputDims, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    private lateinit var interpreter: Interpreter

    override fun getFaceEmbedding(bitmap: Bitmap): Array<FloatArray> {
        if (!::interpreter.isInitialized) return arrayOf()
        val t1 = System.currentTimeMillis()
        val faceNetModelOutputs = Array( 1 ) { FloatArray(modelInfo.outputDims) }
        val input = convertBitmapToBuffer(bitmap)
        interpreter.run(input, faceNetModelOutputs)
        Log.i( "Performance" , "${modelInfo.name} Inference Speed in ms : ${System.currentTimeMillis() - t1}")
        return faceNetModelOutputs
    }

    override fun setModel(modelInfo: ModelInfo) {
        this.modelInfo = modelInfo
        imageTensorProcessor = ImageProcessor.Builder()
            .add(ResizeOp(modelInfo.inputDims, modelInfo.inputDims, ResizeOp.ResizeMethod.BILINEAR))
            .add(StandardizeOp())
            .build()
    }

    private fun convertBitmapToBuffer(image: Bitmap): ByteBuffer {
        return imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer
    }

    override fun init() {
        val interpreterOptions = Interpreter.Options().apply {
//            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
//                addDelegate(GpuDelegate())
//            } else {
//                setNumThreads(4)
//            }
            setNumThreads(4)
            setUseXNNPACK(true)
            setUseNNAPI(true)
        }

        val model = FileUtil.loadMappedFile(context, modelInfo.assetsFilename)
        interpreter = Interpreter(model, interpreterOptions)
    }

}