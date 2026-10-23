package com.my.firstapp

/**
 * 一张图片需要做的方向修正：顺时针旋转多少度 + 是否左右镜像。
 *
 * 手机拍的菜单照片，像素经常是「横着存」的，真正的方向写在 EXIF 里。
 * `BitmapFactory.decodeStream` **不会**自动应用 EXIF，所以要自己读出来再摆正，
 * 否则竖屏拍的照片会以横着的像素进 OCR。
 */
data class ImageTransform(val degrees: Int, val mirror: Boolean) {
    val isIdentity: Boolean get() = degrees == 0 && !mirror
}

/**
 * EXIF orientation → 需要的旋转/镜像。
 *
 * 用「先旋转、再左右镜像」这一种组合就能表示全部 8 种方向（也是 AndroidX 官方示例的写法）：
 * - 2 水平镜像、3 旋转 180、4 垂直镜像（= 水平镜像 + 180）
 * - 5 transpose（沿主对角线翻）、6 顺时针 90、7 transverse（沿副对角线翻）、8 顺时针 270
 */
fun imageTransformForExif(orientation: Int): ImageTransform = when (orientation) {
    2 -> ImageTransform(0, mirror = true)
    3 -> ImageTransform(180, mirror = false)
    4 -> ImageTransform(180, mirror = true)
    5 -> ImageTransform(90, mirror = true)
    6 -> ImageTransform(90, mirror = false)
    7 -> ImageTransform(270, mirror = true)
    8 -> ImageTransform(270, mirror = false)
    else -> ImageTransform(0, mirror = false)
}
