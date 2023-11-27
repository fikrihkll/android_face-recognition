package com.dagger.facerecognition.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.face.Face

object BitmapUtils {

    fun getBitmapFromUri(uri: Uri, context: Context, result: (Bitmap) -> Unit) {
        val parcelFileDescriptor: ParcelFileDescriptor? =
            context.contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor = parcelFileDescriptor?.fileDescriptor
        var image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        fileDescriptor?.let {
            val exif = ExifInterface(fileDescriptor)

            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    Log.i("FKR-CHECK", "ORIENTATION 90")
                    image = rotateBitmap(image, 90, true, false)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    Log.i("FKR-CHECK", "ORIENTATION 270")
                    image = rotateBitmap(image, -90, true, false)
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    Log.i("FKR-CHECK", "ORIENTATION 180")
                    image = rotateBitmap(image, 180, true, false)
                }
            }
            result.invoke(image)
        } ?: run {
            result.invoke(image)
        }
        parcelFileDescriptor?.close()

    }

    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }


    fun cropImageFaceBitmap(image: Bitmap, face: Face, resizeHeight: Int = 112, resizeWidth: Int = 112): Bitmap? {
        val boundingBox = RectF(face.boundingBox)
        val croppedFace = getCropBitmapByBoundingBox(image, boundingBox) ?: run {
            return null
        }
        val scaled = getResizedBitmap(croppedFace, resizeWidth, resizeHeight) ?: run {
            return null
        }
        return scaled
    }

    fun cropImageFaceBitmapWithoutResize(image: Bitmap, face: Face): Bitmap? {
        val boundingBox = RectF(face.boundingBox)
        val croppedFace = getCropBitmapByBoundingBox(image.copy(Bitmap.Config.ARGB_8888, true), boundingBox) ?: run {
            return null
        }
        return croppedFace
    }

    fun cropImageFaceBitmapWithoutResize(image: Bitmap, rectangle: Rect): Bitmap {
        val boundingBox = RectF(rectangle)
        return getCropBitmapByBoundingBox(image.copy(Bitmap.Config.ARGB_8888, true), boundingBox)
    }

    fun getCropBitmapByBoundingBox(source: Bitmap, cropRectF: RectF): Bitmap {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        canvas.drawRect(
            RectF(0f, 0f, cropRectF.width(), cropRectF.height()),
            paint
        )
        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)

        // draw image
        canvas.drawBitmap(source, matrix, paint)
        if (!source.isRecycled) {
            source.recycle()
        }
        return resultBitmap
    }

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap? {
        val width = bm.width
        val height = bm.height

        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height

        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

}