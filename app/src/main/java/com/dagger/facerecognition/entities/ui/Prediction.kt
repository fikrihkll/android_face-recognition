package com.dagger.facerecognition.entities.ui

import android.graphics.Rect

data class FacePrediction(
    var boundingBox: Rect,
    var label: String,
    var score: Double
)