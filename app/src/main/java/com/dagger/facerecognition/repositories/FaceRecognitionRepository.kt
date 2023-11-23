package com.dagger.facerecognition.repositories

import com.dagger.facerecognition.cache.RecognitionCacheDao
import com.dagger.facerecognition.entities.cache.RecognitionCache
import com.dagger.facerecognition.entities.cache.CacheResult
import com.dagger.facerecognition.entities.cache.CacheSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceRecognitionRepository
@Inject
constructor(
    private val recognitionCacheDao: RecognitionCacheDao
) {

    suspend fun addNewFace(recognition: RecognitionCache): CacheResult<Boolean> {
        recognitionCacheDao.addRecognition(recognition)
        return CacheSuccess(data = true)
    }

    suspend fun getRecognitions(): CacheResult<List<RecognitionCache>> {
        return CacheSuccess(data = recognitionCacheDao.getRecognitions())
    }

    suspend fun deleteRecords(): CacheResult<Boolean> {
        recognitionCacheDao.deleteRecords()
        return CacheSuccess(data = true)
    }

}