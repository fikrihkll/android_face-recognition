package com.dagger.facerecognition.modules

import com.dagger.facerecognition.utils.Boost
import com.dagger.facerecognition.utils.BoostHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
class BoostHelperModule {

    @Provides
    fun provideBoostHelper(boost: Boost): BoostHelper {
        return BoostHelper(boost)
    }

}