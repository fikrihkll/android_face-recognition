package com.dagger.facerecognition

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.dagger.facerecognition.databinding.ActivityPictureComparisonBinding
import com.dagger.facerecognition.utils.BitmapUtils
import com.dagger.facerecognition.viewmodels.FaceAnalyzerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext


@AndroidEntryPoint
class PictureComparisonActivity : AppCompatActivity(),
    View.OnClickListener,
    CoroutineScope {

    private val faceAnalyzerViewModel: FaceAnalyzerViewModel by viewModels()

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
                            faceAnalyzerViewModel.compareFace(image1!!, image2!!)
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

        binding.imagePickerButton.setOnClickListener(this)
        observerLiveData()
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

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun observerLiveData() {
        faceAnalyzerViewModel.faceComparisonLiveData.observe(this) {
            binding.resultTextView.text = it
        }
    }

    private fun pickImage() {
        val imagePickerIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        filePickerLauncher.launch(imagePickerIntent)
    }
}