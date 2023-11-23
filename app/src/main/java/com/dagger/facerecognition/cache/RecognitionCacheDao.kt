package com.dagger.facerecognition.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.dagger.facerecognition.entities.cache.RecognitionCache

@Dao
interface RecognitionCacheDao {

    @Insert
    suspend fun addRecognition(recognitionCacheDao: RecognitionCache): Long

    @Query("SELECT * FROM recognitions")
    suspend fun getRecognitions(): List<RecognitionCache>

    @Query("DELETE FROM recognitions")
    suspend fun deleteRecords()
}