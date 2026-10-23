package com.my.firstapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 从系统分享进来的东西：一张或多张图片，或者一段文字/链接。
 *
 * 抖省省在图片分享面板里可能一次分享 2~3 张截图（店铺页 + 团购页），
 * 也可能只分享一段文字或一个链接，所以两种都要接。
 */
data class SharePayload(
    val imageUris: List<Uri> = emptyList(),
    val text: String = "",
) {
    val isEmpty: Boolean get() = imageUris.isEmpty() && text.isBlank()
}

/** 把 Intent 里的分享内容取出来。图片和文字可以同时有。 */
fun Intent.toSharePayload(): SharePayload {
    val images = mutableListOf<Uri>()
    when (action) {
        Intent.ACTION_SEND -> streamUri()?.let { images += it }
        Intent.ACTION_SEND_MULTIPLE -> images += streamUris()
    }
    // 有些 App 不发 EXTRA_STREAM，只塞在 ClipData 里。
    clipData?.let { clip ->
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i).uri?.let { uri -> if (uri !in images) images += uri }
        }
    }
    val onlyImages = images.filter { looksLikeImage(it) }
    return SharePayload(imageUris = onlyImages, text = sharedText())
}

private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION") (getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
    }

private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION") getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }

private fun Intent.sharedText(): String =
    (getCharSequenceExtra(Intent.EXTRA_TEXT) ?: getCharSequenceExtra(Intent.EXTRA_SUBJECT))?.toString().orEmpty().trim()

/** 分享方声明了图片类型就认；没声明类型的（个别 App）看后缀。 */
private fun Intent.looksLikeImage(uri: Uri): Boolean {
    val declared = type
    if (declared != null) return declared.startsWith("image/")
    val path = uri.lastPathSegment?.lowercase().orEmpty()
    return listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".gif", ".bmp").any { path.endsWith(it) }
}

/**
 * 分享进来的图片先落到 App 私有 cache，再去 OCR。
 *
 * 为什么必须这么做：分享授予的是**一次性的临时读权限**，
 * 分享方进程一结束（或者系统回收），原来的 content:// 就打不开了。
 * 直接拿它去 OCR 会出现「刚分享进来时好使、过一会儿就失败」的玄学 bug。
 * 复制一份到自己的目录，后面怎么用都稳，用完由 [clear] 删掉。
 */
class ShareStaging(context: Context) {
    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.cacheDir, "share-inbox")

    /** 复制到 cache，返回落盘后的文件。单张失败不影响其它张。 */
    fun stage(uris: List<Uri>): List<File> {
        if (uris.isEmpty()) return emptyList()
        dir.mkdirs()
        val stamp = System.currentTimeMillis()
        val failures = mutableListOf<String>()
        val result = uris.mapIndexedNotNull { index, uri ->
            val target = File(dir, "share-$stamp-$index")
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("打不开这个分享链接")
                if (target.length() <= 0L) error("分享过来的图片是空的")
                target
            }.onFailure {
                Log.w("ShareInbox", "复制分享图片失败：${it::class.java.simpleName}")
                failures += it::class.java.simpleName
                target.delete()
            }.getOrNull()
        }
        // 读不到时留一条原因（不含图片内容），方便以后排查分享权限问题
        if (failures.isNotEmpty()) {
            runCatching {
                File(appContext.cacheDir, "share-inbox-error.txt")
                    .writeText("uris=${uris.size} staged=${result.size}\n" + failures.joinToString("\n"))
            }
        }
        return result
    }

    /** OCR 完成或用户取消后清理，别把用户截图长期留在 cache 里。 */
    fun clear() {
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
            File(appContext.cacheDir, "share-inbox-error.txt").delete()
        }
    }
}

/**
 * 分享进来的文字 / 链接 → 一页扫描结果。
 *
 * 抖省省分享文字里通常带店名、套餐名和链接，但不保证格式稳定，
 * 所以只做保守提取：能拿到的就用，拿不到就留空让用户在预览里补，
 * **不伪造**任何来源 ID 或商品链接。
 */
fun scanSharedText(raw: String): PageScan {
    // Keep original line boundaries and evidence. parseSharedText intentionally normalizes
    // whitespace for URL extraction, so its flattened text must not feed the menu parser.
    val withoutUrls = raw.replace(Regex("""https?://[^\s，。、）)】"']+"""), " ")
    val conditionBoundary = Regex("(?=有效期|使用时间|不可用日期|购买须知|使用须知|截止日期)")
    val lines = withoutUrls.lines().flatMap { it.split(conditionBoundary) }
        .map { it.trim() }.filter { it.isNotBlank() }
    val offerWords = Regex("套餐|代金券|优惠券|团购|单人餐|双人餐|多人餐|现金券")
    val shopTail = Regex("(?:餐厅|饭店|面馆|粉店|火锅店|小吃店|门店|分店|咖啡店|奶茶店)(?:[（(][^）)]*[）)])?$")
    val name = lines.firstOrNull { line ->
        line.length in 3..40 && shopTail.containsMatchIn(line) &&
            !offerWords.containsMatchIn(line) && !isPriceCondition(line) &&
            !line.contains('¥') && !line.contains('￥')
    }.orEmpty().removePrefix("店名：").removePrefix("店名:").trim()
    val parsed = ocrLinesToDraftDishes(lines.filter { it != name }.map(::OcrLine))
    val conditions = priceConditionsOf(lines)
    val deals = parsed.filter { offerWords.containsMatchIn(it.name) }.map { item ->
        ScannedDeal(title = item.name, currentPrice = item.price, originalPrice = null,
            rawText = raw, conditionsText = listOf(conditions, item.priceConditions)
                .filter { it.isNotBlank() }.distinct().joinToString("\n"))
    }
    val dishes = parsed.filterNot { offerWords.containsMatchIn(it.name) }
        .map { it.copy(rawText = raw) }
    return PageScan(shopName = name, deals = deals, dishes = dishes.map { it.name },
        pricedDishes = dishes, rawText = raw,
        notes = if (deals.isNotEmpty() || dishes.isNotEmpty())
            listOf("分享文字中的价格和条件仅供预填，请核对实际平台、商品和适用条件。") else emptyList())
}
