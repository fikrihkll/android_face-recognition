package com.dagger.facerecognition.entities.ui

import android.graphics.Rect

data class FacePrediction(
    var bbox : Rect,
    var label : String,
    var maskLabel : String = ""
)