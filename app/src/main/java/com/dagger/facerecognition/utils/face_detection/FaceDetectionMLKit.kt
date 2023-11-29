package com.dagger.facerecognition.utils.face_detection

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class FaceDetectionMLKit
@Inject
constructor(): FaceDetectionHelper {

    private lateinit var detector: FaceDetector
    private lateinit var faceDetectorOptions: FaceDetectorOptions

    override fun init() {
        // Initialize Face Detector
        faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(faceDetectorOptions)
    }

    override suspend fun findFace(image: InputImage): List<Face> {
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

    override suspend fun findFace(image: Bitmap): List<Face> {
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
}