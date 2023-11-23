package com.dagger.facerecognition

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.dagger.facerecognition.databinding.ActivityPictureComparisonBinding
import com.dagger.facerecognition.entities.ui.ModelInfo
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_detection.FaceDetectionMLKit
import com.dagger.facerecognition.utils.face_recognition.FaceNetRecognition
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionCallback
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionNotFound
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionState
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionSuccess
import com.dagger.facerecognition.viewmodels.MainViewModel
import com.google.mlkit.vision.face.Face
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext


@AndroidEntryPoint
class PictureComparisonActivity : AppCompatActivity(),
    View.OnClickListener,
    FaceRecognitionCallback,
    CoroutineScope {

    lateinit var faceRecognitionHelper: FaceNetRecognition
    private var faceNetModel = ModelInfo(
        "FaceNet" ,
        "facenet.tflite" ,
        0.4f ,
        10f ,
        128 ,
        160
    )

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityPictureComparisonBinding
    private var image1: Bitmap? = null
    private var image2: Bitmap? = null
    private lateinit var job: Job

    private val filePickerLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            activityResult.data?.data?.let {
                if (image1 == null) {
                    BitmapUtils.getBitmapFromUri(it, this@PictureComparisonActivity) { bitmap ->
                        image1 = bitmap
                        binding.face1ImageView.setImageBitmap(bitmap)
//                        faceRecognitionHelper.detectFace(
//                            bitmap
//                        )
                        pickImage()
                    }
                } else if (image2 == null) {
                    BitmapUtils.getBitmapFromUri(it, this@PictureComparisonActivity) { bitmap ->
                        image2 = bitmap
                        binding.face2ImageView.setImageBitmap(bitmap)
                        Log.i("FKR-CHECK", "PASSED")
                        faceRecognitionHelper.isIdentical(
                            context = this@PictureComparisonActivity,
                            image1 = image1!!.copy(Bitmap.Config.ARGB_8888, true),
                            image2 = image2!!.copy(Bitmap.Config.ARGB_8888, true)
                        )
                    }
                } else {
                    BitmapUtils.getBitmapFromUri(it, this@PictureComparisonActivity) { bitmap ->
                        image1 = bitmap
                        binding.face1ImageView.setImageBitmap(bitmap)
                        image2 = null
                        pickImage()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPictureComparisonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        job = Job()

        faceRecognitionHelper = FaceNetRecognition(this@PictureComparisonActivity, modelInfo = faceNetModel)
        faceRecognitionHelper.coroutineScope = this
        faceRecognitionHelper.faceDetectionHelper = FaceDetectionMLKit()
        faceRecognitionHelper.faceDetectionHelper.initialize()
        faceRecognitionHelper.setListener(this)

        binding.imagePickerButton.setOnClickListener(this)
        viewModel.getFaceList()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.imagePickerButton -> {
                pickImage()
            }
        }
    }

    override fun onFaceDetectionFinished(faces: List<Face>, image: Bitmap) {
        if (faces.isEmpty()) {
            return
        }

        faceRecognitionHelper.recognizeFace(
            context = this@PictureComparisonActivity,
            face = faces.first(),
            image = image.copy(Bitmap.Config.ARGB_8888, true),
            registeredList = viewModel.faceList
        )
    }

    override fun onFaceRecognitionFinished(state: FaceRecognitionState) {
        when (state) {
            is FaceRecognitionSuccess -> {
                binding.resultTextView.text = "${state.data.title}\n${state.data.similarity}"
            }
            is FaceRecognitionNotFound -> {
                binding.resultTextView.text = state.message
            }
            else -> {

            }
        }
    }

    override fun onIdenticalCheckFinished(isIdentical: Boolean, message: String) {
        binding.resultTextView.text = "${if (isIdentical) "Identical\n\n${message}" else "Not identical"}\n\n${message}"
    }

    override fun onFaceRegistrationFinished(state: FaceRecognitionState) {}

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun pickImage() {
        val imagePickerIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        filePickerLauncher.launch(imagePickerIntent)
    }
}