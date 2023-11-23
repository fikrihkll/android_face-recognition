package com.dagger.facerecognition.entities.ui

sealed class RequestResult<T>

data class RequestSuccess<T>(
    val data: T
): RequestResult<T>()

data class RequestError<T>(
    val message: String
): RequestResult<T>()