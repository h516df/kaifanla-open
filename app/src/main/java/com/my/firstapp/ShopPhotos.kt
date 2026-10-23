package com.my.firstapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** 照片只保存在应用私有目录。JSON 里只记安全的文件名。 */
class ShopPhotos(private val context: Context) {
    private val directory get() = File(context.filesDir, "photos")

    fun file(name: String): File? =
        if (Regex("[0-9a-fA-F-]{36}\\.jpg").matches(name)) File(directory, name) else null

    fun delete(name: String) {
        file(name)?.delete()
    }

    fun import(uri: Uri): String {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建照片目录")
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(directory, name)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsInput = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取照片")
            boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("不是可用的图片")
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2160) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("无法解码照片")
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val rotated = if (degrees == 0f) decoded else Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true,
            )
            val scale = 1080f / max(rotated.width, rotated.height)
            val resized = if (scale < 1f) Bitmap.createScaledBitmap(
                rotated, (rotated.width * scale).toInt().coerceAtLeast(1),
                (rotated.height * scale).toInt().coerceAtLeast(1), true,
            ) else rotated
            target.outputStream().use {
                if (!resized.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("照片压缩失败")
            }
            if (resized !== rotated) resized.recycle()
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
            return name
        } catch (error: Exception) {
            Log.w("ShopPhotos", "Could not import shop cover", error)
            target.delete()
            throw error
        }
    }
}

/** 有照片时显示裁切图；旧店铺或文件丢失时仍显示原来的 emoji。 */
@Composable
fun ShopArtwork(emoji: String, photo: String, modifier: Modifier = Modifier, fallbackSize: Dp = 52.dp, pendingPhoto: Uri? = null) {
    val context = LocalContext.current
    val file = remember(photo) { ShopPhotos(context).file(photo)?.takeIf { it.isFile } }
    val source = pendingPhoto ?: file
    var failed by remember(source) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (source == null || failed) {
            EmojiBadge(emoji, fallbackSize)
        } else {
            AsyncImage(
                model = source,
                contentDescription = "店铺照片",
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        }
    }
}
