package com.dagger.facerecognition.utils.face_recognition

import com.dagger.facerecognition.entities.ui.Recognition

sealed class FaceRecognitionState

data class FaceRecognitionSuccess(
    val data: Recognition,
): FaceRecognitionState()

data class FaceRecognitionNotFound(
    val message: String
): FaceRecognitionState()

data class FaceRecognitionError(
    val message: String
): FaceRecognitionState()
