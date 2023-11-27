package com.dagger.facerecognition.utils.camera;

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.Image
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
    private var currentImageProxy: ImageProxy? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        if (isProcessing) {
            image.close()
            return
        } else {
            isProcessing = true
            currentImageProxy = image

            image.image?.let { imageFromProxy ->
                processBitmap(imageFromProxy)
            } ?: run {
                setProcessingDone()
            }
        }
    }

    private fun processBitmap(imageFromProxy: Image) {
        currentImageProxy?.let { image ->
            CoroutineScope(Dispatchers.IO).launch {
                var frameBitmap = Bitmap.createBitmap(imageFromProxy.width, imageFromProxy.height, Bitmap.Config.ARGB_8888)
                frameBitmap.copyPixelsFromBuffer(image.planes[0].buffer)
                frameBitmap = BitmapUtils.rotateBitmap(frameBitmap, image.imageInfo.rotationDegrees, false, false)
                listener.onFrameReceived(frameBitmap)
            }
        }
    }

    fun setProcessingDone() {
        isProcessing = false
        currentImageProxy?.close()
    }

}

interface CameraFrameListener {

    fun onFrameReceived(image: Bitmap)

}