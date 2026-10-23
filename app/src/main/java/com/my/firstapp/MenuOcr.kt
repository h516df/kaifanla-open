package com.my.firstapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** 相册截图可能很大，先缩到长边 2000 再识别：省内存，也更快。 */
private const val MaxOcrSide = 2000

/**
 * 识别相册里那张截图的文字，整理成「批量粘贴菜单」认的格式。
 *
 * 用的是 Google ML Kit 的**bundled 中文**识别（`com.google.mlkit:text-recognition-chinese`）：
 * 模型和 native 库都打进 APK，不联网、不需要设备装 Google Play 服务，国内能直接用。
 * 识别不到文字时返回空串，由调用方提示用户换一张。
 */
suspend fun recognizeMenuText(context: Context, uri: Uri): String =
    ocrLinesToMenuText(recognizeMenuLines(context, uri))

/**
 * 只做 OCR，返回带坐标的文字块。
 *
 * 抖省省那种页面需要「按位置分区」（店名 / 团购 / 推荐菜），
 * 所以这里把带坐标的原始结果暴露出来，交给 `scanForImport` 做结构化整理。
 * 老的 `recognizeMenuText` 仍然走同一套识别，行为不变。
 */
suspend fun recognizeMenuLines(context: Context, uri: Uri): List<OcrLine> {
    val bitmap = loadScaledBitmap(context, uri)
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    return try {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = text.textBlocks.flatMap { block -> block.lines }
        Log.d(
            "MenuOcr",
            "bitmap=${bitmap.width}x${bitmap.height} blocks=${text.textBlocks.size} lines=${lines.size} chars=${text.text.length}",
        )
        val boxes = lines.map { it.boundingBox }
        val hasBoxes = boxes.all { it != null }
        lines.mapIndexed { index, line ->
            val box = if (hasBoxes) boxes[index] else null
            if (box != null) OcrLine(line.text, box.left, box.top, box.right, box.bottom) else OcrLine(line.text)
        }
    } finally {
        recognizer.close()
        bitmap.recycle()
    }
}

private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // 注意：只量尺寸时 decodeStream 返回 null，所以不能写成
    // `openInputStream(uri)?.use { decodeStream(...) } ?: error(...)`，
    // 那样 use 的返回值就是 null，永远当「读不到图片」。null 检查要单独做。
    val boundsInput = context.contentResolver.openInputStream(uri) ?: error("读不到这张图片")
    boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("这不是一张能识别的图片")
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MaxOcrSide) sample *= 2
    val imageInput = context.contentResolver.openInputStream(uri) ?: error("读不到这张图片")
    val decoded = imageInput.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("图片解码失败")
    return applyExifOrientation(context, uri, decoded)
}

/**
 * 手机拍的菜单照片，真正方向写在 EXIF 里，而 `BitmapFactory.decodeStream` **不会**自动应用。
 * 不摆正的话，竖着拍的菜单会以横着的像素进 OCR，菜名和价格就拼不回一行了。
 */
private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val orientation = readExifOrientation(context, uri)
    val transform = imageTransformForExif(orientation)
    Log.d(
        "MenuOcr",
        "exif=$orientation rotate=${transform.degrees} mirror=${transform.mirror} before=${bitmap.width}x${bitmap.height}",
    )
    if (transform.isIdentity) return bitmap
    val matrix = Matrix()
    // 顺序不能反：先 setRotate 再 postScale（AndroidX 官方示例的写法），
    // 这样 transpose(5) / transverse(7) 才是沿对角线翻，而不是「先翻再转」。
    matrix.setRotate(transform.degrees.toFloat())
    if (transform.mirror) matrix.postScale(-1f, 1f)
    val upright = runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrElse { return bitmap }
    if (upright !== bitmap) bitmap.recycle()
    Log.d("MenuOcr", "upright=${upright.width}x${upright.height}")
    return upright
}

private fun readExifOrientation(context: Context, uri: Uri): Int = runCatching {
    context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
}

