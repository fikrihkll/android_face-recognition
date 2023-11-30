package com.dagger.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.dagger.facerecognition.databinding.ActivityMainBinding
import com.dagger.facerecognition.entities.ui.FacePrediction
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.entities.ui.RequestError
import com.dagger.facerecognition.entities.ui.RequestSuccess
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.camera.CameraFrameListener
import com.dagger.facerecognition.utils.camera.FrameAnalyser
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionModel
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionResultListener
import com.dagger.facerecognition.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class FaceNetActivity : AppCompatActivity(),
    View.OnClickListener,
    View.OnLongClickListener,
    CameraFrameListener,
    FaceRecognitionResultListener,
    CoroutineScope,
    CompoundButton.OnCheckedChangeListener {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var faceRecognitionHelper: FaceRecognitionHelper
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var job: Job
    private lateinit var frameAnalyser: FrameAnalyser

    private var faceRecognitionModel = FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET)
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
    )

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        job = Job()
        cameraExecutor = Executors.newSingleThreadExecutor()
        launch {
            faceRecognitionHelper.init(
                modelInfo = faceRecognitionModel,
                listener = this@FaceNetActivity
            )
        }

        binding.comparisonButton.setOnClickListener(this)
        binding.registerButton.setOnClickListener(this)
        binding.registerButton.setOnLongClickListener(this)
        binding.registerSwitch.setOnCheckedChangeListener(this)

        mainViewModel.getFaceList()

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
                mainViewModel.deleteFace()
                return true
            }
        }
        return false
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.registerButton -> {
                takePicture()
            }
            R.id.comparisonButton -> {
                startActivity(Intent(this@FaceNetActivity, PictureComparisonActivity::class.java))
            }
        }
    }

    override fun onCheckedChanged(view: CompoundButton?, isChecked: Boolean) {
        when (view?.id) {
            R.id.registerSwitch -> {
                binding.registerButton.isVisible = isChecked
            }
        }
    }

    override fun onFrameReceived(image: Bitmap) {
        if (binding.registerSwitch.isChecked) {
            frameAnalyser.setProcessingDone()
            return
        }

        launch {
            faceRecognitionHelper.recognizeFace(
                frameBitmap = image,
                registeredFace = mainViewModel.faceList.map { Pair(it.title, it.vector[0]) },
                cosineThreshold = faceRecognitionModel.cosineThreshold,
                l2Threshold = faceRecognitionModel.l2Threshold,
                firstFaceOnly = true
            )
        }
    }

    override fun onFaceRecognized(result: List<FacePrediction>) {
        frameAnalyser.setProcessingDone()

        if (result.isNotEmpty()) {
            updateUI(
                """Success:
                        
                    ${
                    result.joinToString { prediction ->
                        "[Verified] ${prediction.label}: ${prediction.score}"
                    }
                }
                    """.trimMargin()
            )
        } else {
            updateUI(result = "No face detected")
        }
    }

    override fun onFaceVectorExtracted(result: List<Recognition>) {
        frameAnalyser.setProcessingDone()

        if (result.isNotEmpty())
            mainViewModel.registerFace(result.first().copy(title = binding.nameEditText.text.toString()))
        else
            updateUI(result = "No face detected")
    }

    private fun observerLiveData() {
        mainViewModel.faceRegistrationLiveData.observe(this) {
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

    private fun updateUI(result: String) {
        binding.logTextView.text = result
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

        frameAnalyser = FrameAnalyser(this)
        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size( 480, 640 ))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser)

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
                imageFrameAnalysis
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e("FKR-CHECK", "Use case binding failed", exc)
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

    private fun takePicture() {
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
                        BitmapUtils.getBitmapFromUri(it, this@FaceNetActivity) { bitmap ->
                            binding.facePreview.setImageBitmap(bitmap)
                            binding.captureInfoTextView.text = ""
                            register(bitmap)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@FaceNetActivity,"Mohon Tunggu", Toast.LENGTH_LONG).show()
                }

            })
    }

    private fun register(bitmap: Bitmap) {
        Log.i(FaceRecognitionHelper.TAG,  "Prior to process of embedding ${Thread.currentThread().name}")
        launch {
            faceRecognitionHelper.getFaceVector(bitmap)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

}