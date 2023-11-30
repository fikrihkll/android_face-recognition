package com.dagger.facerecognition.utils.camera;

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dagger.facerecognition.utils.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrameAnalyser(
    private val listener: CameraFrameListener
): ImageAnalysis.Analyzer {

    private var isProcessing = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        if (isProcessing) {
            image.close()
            return
        } else {
            isProcessing = true

            processBitmap(image)
        }
    }
    @OptIn(ExperimentalGetImage::class)
    private fun processBitmap(imageFromProxy: ImageProxy) {
        imageFromProxy.image?.let { image ->
            CoroutineScope(Dispatchers.IO).launch {
                var frameBitmap = Bitmap.createBitmap(imageFromProxy.width, imageFromProxy.height, Bitmap.Config.ARGB_8888)
                frameBitmap.copyPixelsFromBuffer(image.planes[0].buffer)
                frameBitmap = BitmapUtils.rotateBitmap(frameBitmap, imageFromProxy.imageInfo.rotationDegrees, false, false)
                listener.onFrameReceived(frameBitmap)
                imageFromProxy.close()
            }
        } ?: run {
            imageFromProxy.close()
            isProcessing = false
        }
    }

    fun setProcessingDone() {
        isProcessing = false
    }

}

interface CameraFrameListener {

    fun onFrameReceived(image: Bitmap)

}