package com.dagger.facerecognition.entities.mapper

import com.dagger.facerecognition.entities.cache.RecognitionCache
import com.dagger.facerecognition.entities.ui.Recognition
import com.dagger.facerecognition.utils.BaseMapper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RecognitionMapper: BaseMapper<Recognition, RecognitionCache>() {
    override fun mapToCache(entity: Recognition): RecognitionCache {
        val gson = Gson()
        val vectorString = gson.toJson(entity.vector)
        return RecognitionCache(
            id = entity.id,
            title = entity.title,
            vector = vectorString
        )
    }

    override fun mapToUI(entity: RecognitionCache): Recognition {
        val gson = Gson()
        val vector = gson.fromJson<Array<FloatArray>>(entity.vector, object : TypeToken<Array<FloatArray>>() {}.type)
        return Recognition(
            id = entity.id,
            title = entity.title,
            vector = vector
        )
    }
}