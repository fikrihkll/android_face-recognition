package com.dagger.facerecognition.modules

import android.content.Context
import com.dagger.facerecognition.usecases.CompareFaceUseCase
import com.dagger.facerecognition.usecases.GetFaceVectorUseCase
import com.dagger.facerecognition.usecases.RecognizeFaceUseCase
import com.dagger.facerecognition.utils.face_detection.FaceDetectionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionHelper
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionUtility
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionModel
import com.dagger.facerecognition.utils.face_recognition.FaceRecognitionModel.Type
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FaceRecognitionUseCaseModule {

    @Provides
    @Singleton
    fun provideFaceRecognitionFaceNetUseCase(
        faceRecognitionUtility: FaceRecognitionUtility,
        faceDetectionHelper: FaceDetectionHelper
    ): RecognizeFaceUseCase {
        faceRecognitionUtility.setModel(
            FaceRecognitionModel.getModelInfo(Type.FACE_NET)
        )
        return RecognizeFaceUseCase(
            faceRecognitionHelper = faceRecognitionUtility,
            faceDetectionHelper = faceDetectionHelper
        )
    }

    @Provides
    @Singleton
    fun provideGetFaceVectorFaceNetUseCase(
        faceRecognitionUtility: FaceRecognitionUtility,
        faceDetectionHelper: FaceDetectionHelper
    ): GetFaceVectorUseCase {
        return GetFaceVectorUseCase(
            faceRecognitionHelper = faceRecognitionUtility,
            faceDetectionHelper = faceDetectionHelper
        )
    }

    @Provides
    @Singleton
    fun provideFaceHelperUseCase(
        faceRecognitionUtility: FaceRecognitionUtility,
        faceDetectionHelper: FaceDetectionHelper
    ): FaceRecognitionHelper {
        return FaceRecognitionHelper(
            faceRecognitionHelper = faceRecognitionUtility,
            faceDetectionHelper = faceDetectionHelper
        )
    }

    @FaceNet
    @Provides
    @Singleton
    fun provideCompareFaceFaceNetUseCase(
        faceRecognitionUtility: FaceRecognitionUtility,
        faceDetectionHelper: FaceDetectionHelper
    ): CompareFaceUseCase {
        faceRecognitionUtility.setModel(
            FaceRecognitionModel.getModelInfo(
                Type.FACE_NET))
        return CompareFaceUseCase(
            faceRecognitionHelper = faceRecognitionUtility,
            faceDetectionHelper = faceDetectionHelper
        )
    }

//    @FaceNet512
//    @Provides
//    @Singleton
//    fun provideCompareFaceFaceNet512UseCase(
//        faceRecognitionUtility: FaceRecognitionHelper,
//        faceDetectionHelper: FaceDetectionHelper
//    ): CompareFaceUseCase {
//        faceRecognitionUtility.setModel(
//            FaceRecognitionModel.getModelInfo(
//                FaceRecognitionModel.Type.FACE_NET_512))
//        return CompareFaceUseCase(
//            faceRecognitionHelper = faceRecognitionUtility,
//            faceDetectionHelper = faceDetectionHelper
//        )
//    }
//
//    @FaceNet512Quantized
//    @Provides
//    @Singleton
//    fun provideCompareFaceFaceNet512QuantizedUseCase(
//        faceRecognitionUtility: FaceRecognitionHelper,
//        faceDetectionHelper: FaceDetectionHelper
//    ): CompareFaceUseCase {
//        faceRecognitionUtility.setModel(
//            FaceRecognitionModel.getModelInfo(
//                FaceRecognitionModel.Type.FACE_NET_512_QUANTIZED))
//        return CompareFaceUseCase(
//            faceRecognitionHelper = faceRecognitionUtility,
//            faceDetectionHelper = faceDetectionHelper
//        )
//    }
//
//    @FaceNetQuantized
//    @Provides
//    @Singleton
//    fun provideCompareFaceFaceNetIntQuantizedUseCase(
//        faceRecognitionUtility: FaceRecognitionHelper,
//        faceDetectionHelper: FaceDetectionHelper
//    ): CompareFaceUseCase {
//        faceRecognitionUtility.setModel(
//            FaceRecognitionModel.getModelInfo(
//                FaceRecognitionModel.Type.FACE_NET_QUANTIZED))
//        return CompareFaceUseCase(
//            faceRecognitionHelper = faceRecognitionUtility,
//            faceDetectionHelper = faceDetectionHelper
//        )
//    }
//
//    @MobileFaceNet
//    @Provides
//    @Singleton
//    fun provideCompareFaceMobileFaceNetUseCase(
//        faceRecognitionUtility: FaceRecognitionHelper,
//        faceDetectionHelper: FaceDetectionHelper
//    ): CompareFaceUseCase {
//        faceRecognitionUtility.setModel(
//            FaceRecognitionModel.getModelInfo(
//                FaceRecognitionModel.Type.MOBILE_FACE_NET))
//        return CompareFaceUseCase(
//            faceRecognitionHelper = faceRecognitionUtility,
//            faceDetectionHelper = faceDetectionHelper
//        )
//    }

    @Provides
    fun provideContext(
        @ApplicationContext context: Context,
    ): Context {
        return context
    }

}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FaceNet512Quantized

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FaceNet512

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FaceNetQuantized

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FaceNet

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MobileFaceNet