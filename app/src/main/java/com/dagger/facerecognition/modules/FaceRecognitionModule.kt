package com.dagger.facerecognition.modules

import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelperImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
interface FaceRecognitionModule {

    @ActivityScoped
    @Binds
    fun provideFaceRecognitionHandler(impl: FaceRecognitionHelperImpl): FaceRecognitionHelper

}