package com.dagger.facerecognition.modules

import com.dagger.facerecognition.utils.Boost
import com.dagger.facerecognition.utils.BoostImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
interface BoostModule {

    @Binds
    fun provideBoostImpl(boostImpl: BoostImpl): Boost

}