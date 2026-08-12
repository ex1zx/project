package com.project.handtracker

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object YuvToRgbConverter {
    fun toBitmap(image: ImageProxy): Bitmap? {
        val mediaImage = image.image ?: return null
        if (mediaImage.format != ImageFormat.YUV_420_888) return null
        val width = mediaImage.width
        val height = mediaImage.height
        val nv21 = ByteArray(width * height * 3 / 2)
        val yPlane = mediaImage.planes[0]
        val uPlane = mediaImage.planes[1]
        val vPlane = mediaImage.planes[2]
        var offset = 0
        val yBuffer = yPlane.buffer
        val yRow = ByteArray(yPlane.rowStride)
        for (row in 0 until height) {
            yBuffer.position(row * yPlane.rowStride)
            yBuffer.get(yRow, 0, minOf(yRow.size, yBuffer.remaining()))
            yRow.copyInto(nv21, offset, 0, width)
            offset += width
        }
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        for (row in 0 until chromaHeight) {
            for (column in 0 until chromaWidth) {
                val uIndex = row * uPlane.rowStride + column * uPixelStride
                val vIndex = row * vPlane.rowStride + column * vPixelStride
                if (uIndex < uBuffer.limit() && vIndex < vBuffer.limit()) {
                    nv21[offset++] = vBuffer.get(vIndex)
                    nv21[offset++] = uBuffer.get(uIndex)
                }
            }
        }
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val output = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 82, output)
        val bytes = output.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}