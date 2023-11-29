package com.dagger.facerecognition.utils

import javax.inject.Inject

class BoostHelper
@Inject
constructor(
    private val boost: Boost
){

    fun print() {
        println(boost.getBooster())
    }

}