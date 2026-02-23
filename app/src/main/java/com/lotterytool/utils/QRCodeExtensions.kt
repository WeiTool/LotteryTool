package com.lotterytool.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

/**
 * 使用 KTX 风格实现的二维码生成工具
 * 基于 androidx.core.graphics 扩展函数
 */
suspend fun String.toQrCode(size: Int = 512): Bitmap? = withContext(Dispatchers.Default) {
    try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1) // 设置边距，通常 1-2 即可
        }

        // 1. 生成 Zxing 位矩阵
        val bitMatrix = QRCodeWriter().encode(
            this@toQrCode,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )

        // 2. 使用 KTX 的 createBitmap 创建实例
        // 使用 RGB_565 减少内存占用（二维码不需要透明度）
        createBitmap(size, size, Bitmap.Config.RGB_565).applyCanvas {
            val paint = Paint()
            // 遍历矩阵，绘制矩形而非打点
            for (x in 0 until size) {
                for (y in 0 until size) {
                    paint.color = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    // 绘制 1x1 的矩形填充整个像素格子，颜色会非常扎实
                    drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}