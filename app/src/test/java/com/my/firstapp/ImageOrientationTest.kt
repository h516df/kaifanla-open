package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EXIF 方向映射的测试。
 * 8 种方向都要能摆正；手机拍照最常见的是 6（竖着拍、像素横着存）。
 */
class ImageOrientationTest {

    @Test
    fun normalAndUnknownNeedNoChange() {
        assertTrue(imageTransformForExif(1).isIdentity)
        assertTrue(imageTransformForExif(0).isIdentity)
        assertTrue(imageTransformForExif(99).isIdentity)
    }

    @Test
    fun rotations() {
        assertEquals(ImageTransform(180, false), imageTransformForExif(3))
        assertEquals(ImageTransform(90, false), imageTransformForExif(6))
        assertEquals(ImageTransform(270, false), imageTransformForExif(8))
    }

    @Test
    fun mirrors() {
        // 2 = 水平镜像；4 = 垂直镜像，等于「水平镜像 + 旋转 180」
        assertEquals(ImageTransform(0, true), imageTransformForExif(2))
        assertEquals(ImageTransform(180, true), imageTransformForExif(4))
    }

    @Test
    fun transposeAndTransverse() {
        // 5 = 沿主对角线翻；7 = 沿副对角线翻
        assertEquals(ImageTransform(90, true), imageTransformForExif(5))
        assertEquals(ImageTransform(270, true), imageTransformForExif(7))
    }

    @Test
    fun everyExifOrientationIsCovered() {
        // 1..8 一个都不能漏；除了 1 之外都应该有动作
        (2..8).forEach { orientation ->
            assertFalse("orientation $orientation 应该需要修正", imageTransformForExif(orientation).isIdentity)
        }
        // 旋转角度只能是 0/90/180/270
        (1..8).forEach { orientation ->
            val degrees = imageTransformForExif(orientation).degrees
            assertTrue("orientation $orientation 的旋转角 $degrees 不合法", degrees in listOf(0, 90, 180, 270))
        }
    }
}
