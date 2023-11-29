package com.dagger.facerecognition.utils

import javax.inject.Inject

class BoostImpl
@Inject
constructor(): Boost {

    override fun getBooster(): String {
        return "high-booster"
    }

}