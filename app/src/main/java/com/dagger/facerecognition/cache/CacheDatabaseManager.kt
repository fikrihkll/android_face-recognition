package com.dagger.facerecognition.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dagger.facerecognition.entities.cache.RecognitionCache


@Database(entities = [
    RecognitionCache::class
], version = 32, exportSchema = true)
abstract class CacheDatabaseManager: RoomDatabase() {
    abstract fun recognitionDao(): RecognitionCacheDao
}