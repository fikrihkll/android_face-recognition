package com.dagger.facerecognition.modules

import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.dagger.facerecognition.utils.face_detection.FaceDetectionMLKit
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtility
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtilityImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface FaceRecognitionUtilityModule {

    @Binds
    @Singleton
    fun provideFaceRecognitionHandler(impl: FaceRecognitionUtilityImpl): FaceRecognitionUtility

    @Binds
    @Singleton
    fun provideFaceDetectionHelper(impl: FaceDetectionMLKit): FaceDetectionHelper

}