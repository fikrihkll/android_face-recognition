package com.dagger.facerecognition.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dagger.facerecognition.entities.cache.CacheError
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.entities.cache.CacheResult
import com.dagger.facerecognition.entities.cache.CacheSuccess
import com.dagger.facerecognition.entities.cache.RecognitionCache
import com.dagger.facerecognition.entities.mapper.RecognitionMapper
import com.dagger.facerecognition.entities.ui.RequestError
import com.dagger.facerecognition.entities.ui.RequestResult
import com.dagger.facerecognition.entities.ui.RequestSuccess
import com.dagger.facerecognition.repositories.FaceRecognitionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val faceRecognitionRepository: FaceRecognitionRepository
): ViewModel() {

    private val _faceRegistrationMutableLiveData: MutableLiveData<RequestResult<String>> = MutableLiveData()
    val faceRegistrationLiveData = _faceRegistrationMutableLiveData

    val faceList: MutableList<Recognition> = mutableListOf()

    fun registerFace(recognition: Recognition) {
        viewModelScope.launch {
            val result = faceRecognitionRepository.addNewFace(
                recognition = RecognitionMapper.mapToCache(recognition)
            )

            when (result) {
                is CacheSuccess -> {
                    faceList.add(recognition)
                    _faceRegistrationMutableLiveData.value = RequestSuccess(
                        data = "Success"
                    )
                }
                is CacheError -> {
                    _faceRegistrationMutableLiveData.value = RequestError(
                        message = result.message
                    )
                }
            }
        }
    }

    fun deleteFace() {
        viewModelScope.launch {
            faceRecognitionRepository.deleteRecords()
            faceList.clear()
            _faceRegistrationMutableLiveData.value = RequestSuccess(
                data = "Success delete"
            )
        }
    }

    fun getFaceList() {
        viewModelScope.launch {
            when (val result = faceRecognitionRepository.getRecognitions()) {
                is CacheSuccess<List<RecognitionCache>> -> {
                    faceList.addAll(
                        result.data.map { RecognitionMapper.mapToUI(it) }
                    )
                }
                else -> {}
            }
        }
    }

}