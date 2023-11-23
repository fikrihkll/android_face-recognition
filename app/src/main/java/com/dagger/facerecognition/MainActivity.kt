package com.dagger.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.dagger.facerecognition.cache.CacheDatabaseManager
import com.dagger.facerecognition.databinding.ActivityMainBinding
import com.dagger.facerecognition.entities.ui.RequestError
import com.dagger.facerecognition.entities.ui.RequestSuccess
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionCallback
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionError
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelperImpl
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionNotFound
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionState
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionSuccess
import com.dagger.facerecognition.viewmodels.MainViewModel
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class MainActivity :
    AppCompatActivity(),
    View.OnClickListener,
    View.OnLongClickListener,
    CoroutineScope,
    FaceRecognitionCallback {

    companion object {
        const val TAG = "SPN-FR"
    }

    @Inject
    lateinit var faceRecognitionHelper: FaceRecognitionHelper
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
    )
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        job = Job()
        cameraExecutor = Executors.newSingleThreadExecutor()

        faceRecognitionHelper = FaceRecognitionHelperImpl()
        faceRecognitionHelper.setListener(this)
        faceRecognitionHelper.initialize(this, lifecycleScope)

        binding.registerButton.setOnClickListener(this)
        binding.registerButton.setOnLongClickListener(this)
        binding.registerSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.registerButton.isVisible = isChecked
        }
        binding.registerSwitch.setOnLongClickListener {
            startActivity(Intent(this@MainActivity, PictureComparisonActivity::class.java))
            true
        }

        viewModel.getFaceList()

        requestPermission()
        observerLiveData()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onLongClick(v: View?): Boolean {
        when (v?.id) {
            R.id.registerButton -> {
                viewModel.deleteFace()
                return true
            }
        }
        return false
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.registerButton -> {
                takePicture(true)
            }
        }
    }

    override fun onFaceDetectionFinished(faces: List<Face>, image: Bitmap) {
        if (faces.isEmpty()) {
            return
        }

        binding.overlay.setResults(
            faces.toMutableList(),
            bitmapBuffer.height,
            bitmapBuffer.width
        )

        // Force a redraw
        binding.overlay.invalidate()

        if (binding.registerSwitch.isChecked) {
            return
        }
        if (faceRecognitionHelper.isProcessingRecognition()) {
            return
        }

        binding.facePreview.setImageBitmap(image)
        binding.progressBar.isVisible = true
        faceRecognitionHelper.recognizeFace(
            context = this@MainActivity,
            face = faces.first(),
            image = image.copy(Bitmap.Config.ARGB_8888, true),
            registeredList = viewModel.faceList
        )
    }

    override fun onFaceRecognitionFinished(state: FaceRecognitionState) {
        binding.progressBar.isVisible = false
        val date = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS", Locale.US
        ).format(System.currentTimeMillis())
        when (state) {
            is FaceRecognitionSuccess -> {
                binding.logTextView.text = "Success:\n${state.data.title}\n\nSimilarity: ${String.format("%.1f", state.data.similarity)}\n\n$date"
            }
            is FaceRecognitionNotFound -> {
                binding.logTextView.text = "Not found:\n${state.message}\n\n$date"
            }
            is FaceRecognitionError -> {
                binding.logTextView.text = "Error:\n${state.message}\n\n${state.message}"
            }
            else -> {}
        }
    }

    override fun onFaceRegistrationFinished(state: FaceRecognitionState) {
        when (state) {
            is FaceRecognitionSuccess -> {
                viewModel.registerFace(state.data)
                binding.logTextView.text = "${state.data.title}\n\nRegistered"
            }
            is FaceRecognitionError -> {
                Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    override fun onIdenticalCheckFinished(isIdentical: Boolean, message: String) {}

    private fun observerLiveData() {
        viewModel.faceRegistrationLiveData.observe(this) {
            binding.progressBar.isVisible = false
            when (it) {
                is RequestSuccess -> {
                    binding.logTextView.text = it.data
                }
                is RequestError -> {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun requestPermission(){
        val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (allPermissionsGranted())
                binding.viewFinder.post {
                    setUpCamera()
                }
            else
                Toast.makeText(
                    this,
                    "Izin kamera dan lokasi dibutuhkan",
                    Toast.LENGTH_SHORT
                ).show()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestMultiplePermissions.launch(REQUIRED_PERMISSIONS.toTypedArray())
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun takePicture(register: Boolean) {
        val imageCapture = imageCapture ?: return

        val date = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS", Locale.US
        ).format(System.currentTimeMillis())
        val name = "FaceRecognition_${date}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SPN-Attendance")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        binding.captureInfoTextView.text = "Hang in there..."
        imageCapture.takePicture(outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    binding.captureInfoTextView.text = "Okay, you're good"
                    outputFileResults.savedUri?.let {
                        faceRecognitionHelper.getBitmapFromUri(it, this@MainActivity) { bitmap ->
                            binding.facePreview.setImageBitmap(bitmap)
                            binding.captureInfoTextView.text = ""
                            if (register) {
                                registerFace(bitmap)
                            } else {
                                detectFace(bitmap)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity,"Mohon Tunggu", Toast.LENGTH_LONG).show()
                }

            })
    }

    private fun registerFace(image: Bitmap) {
        binding.progressBar.isVisible = true
        faceRecognitionHelper.registerFace(
            image = image,
            name = binding.nameEditText.text.toString()
        )
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

        preview =
            Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

        imageAnalyzer =
            ImageAnalysis.Builder()
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }
                        bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
                        val rotatedBitmap = faceRecognitionHelper.rotateBitmap(
                            bitmapBuffer.copy(Bitmap.Config.ARGB_8888, true), -90, true, false
                        )
                        image.close()
                        if (!faceRecognitionHelper.isProcessingDetection()) {
                            faceRecognitionHelper.detectFace(rotatedBitmap)
                        }
                    }
                }

        val imageCaptureBuilder = ImageCapture.Builder()
        imageCapture = imageCaptureBuilder
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectFace(bitmap: Bitmap) {
        binding.progressBar.isVisible = true
        faceRecognitionHelper.recognizeFace(
            context = this@MainActivity,
            image = bitmap,
            registeredList = viewModel.faceList
        )
    }

}