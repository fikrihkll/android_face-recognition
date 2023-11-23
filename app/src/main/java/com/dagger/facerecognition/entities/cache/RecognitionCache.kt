package com.dagger.facerecognition.entities.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognitions")
data class RecognitionCache(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("vector")
    val vector: String
)