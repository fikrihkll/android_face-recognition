package com.dagger.facerecognition.utils

abstract class BaseMapper<UI, CACHE> {

    abstract fun mapToCache(entity: UI): CACHE
    abstract fun mapToUI(entity: CACHE): UI

}