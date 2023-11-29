package com.dagger.facerecognition

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.dagger.facerecognition.databinding.ActivityPictureComparisonBinding
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.utils.face_recognition.FaceComparisonHelper
import com.dagger.facerecognition.utils.face_recognition.FaceComparisonResultListener
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext


@AndroidEntryPoint
class PictureComparisonActivity : AppCompatActivity(),
    View.OnClickListener,
    FaceComparisonResultListener,
    CoroutineScope {

    @Inject
    lateinit var faceComparisonHelper: FaceComparisonHelper

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
                        pickImage()
                    }
                } else if (image2 == null) {
                    BitmapUtils.getBitmapFromUri(it, this@PictureComparisonActivity) { bitmap ->
                        image2 = bitmap
                        binding.face2ImageView.setImageBitmap(bitmap)
                        if (image1 != null && image2 != null)
                            binding.comparisonProgress.isVisible = true
                            faceComparisonHelper.compareWithAllModels(
                                image1!!,
                                image2!!,
                                metricToBeUsed = binding.algoSpinner.selectedItem.toString()
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
        faceComparisonHelper.init(
            coroutineScope = this,
            modelInfo = FaceRecognitionModel.getModelInfo(FaceRecognitionModel.Type.FACE_NET),
            listener = this
        )
        setupSpinner()

        binding.imagePickerButton.setOnClickListener(this)
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

    override fun onComparingFinished(result: String) {
        binding.comparisonProgress.isVisible = false
        binding.resultTextView.text = result
    }

    override fun onImageCropped(image1: Bitmap, image2: Bitmap) {
        binding.face1ImageView.setImageBitmap(image1)
        binding.face2ImageView.setImageBitmap(image2)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("l2", "cosine"))

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item)
        binding.algoSpinner.adapter = adapter
    }

    private fun pickImage() {
        val imagePickerIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        filePickerLauncher.launch(imagePickerIntent)
    }
}