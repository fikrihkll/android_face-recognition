package com.dagger.facerecognition.modules

import android.content.Context
import androidx.room.Room
import com.dagger.facerecognition.cache.CacheDatabaseManager
import com.dagger.facerecognition.cache.RecognitionCacheDao
import com.dagger.facerecognition.repositories.FaceRecognitionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Singleton
    @Provides
    fun provideCacheDatabaseManager(@ApplicationContext context: Context): CacheDatabaseManager {
        return Room.databaseBuilder(context, CacheDatabaseManager::class.java, "face_recognition_db").build()
    }

    @Singleton
    @Provides
    fun provideRecognitionDao(cacheDatabaseManager: CacheDatabaseManager): RecognitionCacheDao {
        return cacheDatabaseManager.recognitionDao()
    }

}