package com.dagger.facerecognition.entities.cache

sealed class CacheResult<T>

data class CacheSuccess<T>(
    val data: T
): CacheResult<T>()

data class CacheError<T>(
    val message: String
): CacheResult<T>()
