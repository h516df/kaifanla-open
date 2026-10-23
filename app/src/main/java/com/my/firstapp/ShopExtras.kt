package com.my.firstapp

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 新建/编辑店铺时填的信息 */
data class ShopInfo(
    val name: String,
    val emoji: String,
    val address: String = "",
    val link: String = "",
    val photo: String = "",
    val newPhoto: Uri? = null,
    val walkMinutes: Int? = null,
)

fun Shop.info() = ShopInfo(name, emoji, address, link, photo, walkMinutes = walkMinutes)

/** 店铺图标的常用分组；想要更多可以点「更多表情」打开系统完整的表情选择器。 */
val FoodEmojiGroups: List<Pair<String, List<String>>> = listOf(
    "正餐" to listOf(
        "🍚", "🍛", "🍜", "🍝", "🍲", "🥘", "🍱", "🍣", "🍤", "🥟", "🥠", "🥡", "🍙", "🍘", "🍥",
        "🍢", "🍡", "🫕", "🥗", "🥙", "🌯", "🌮", "🫔", "🍕", "🍔", "🌭", "🥪", "🍟", "🍳", "🥚",
    ),
    "肉和海鲜" to listOf("🍗", "🍖", "🥩", "🥓", "🦆", "🐔", "🐟", "🦐", "🦀", "🦞", "🦑", "🐙", "🦪"),
    "面包甜点" to listOf(
        "🍰", "🎂", "🧁", "🥧", "🍮", "🍩", "🍪", "🍫", "🍬", "🍭", "🍯", "🍦", "🍧", "🍨",
        "🥐", "🥖", "🍞", "🥯", "🧇", "🥞", "🫓", "🥨",
    ),
    "喝的" to listOf("🧋", "☕", "🍵", "🫖", "🥤", "🧃", "🥛", "🍼", "🧉", "🍺", "🍻", "🍷", "🍸", "🍹", "🥂", "🍶", "🧊"),
    "水果蔬菜" to listOf(
        "🍓", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍎", "🍏", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🫐",
        "🍅", "🥑", "🌽", "🥕", "🥔", "🍠", "🥦", "🥬", "🥒", "🌶️", "🫑", "🍄", "🥜", "🌰", "🫘",
    ),
    "可爱的" to listOf("💕", "💖", "🌸", "🌷", "🌈", "⭐", "✨", "🎀", "🐰", "🐱", "🐻", "🐼", "🐷", "🦊", "🐧", "🍀"),
)

/** 关键词 → 图标，店名里出现关键词就自动推荐。顺序靠前的优先。 */
private val EmojiKeywords: List<Pair<List<String>, String>> = listOf(
    listOf("蜜雪", "茶百道", "霸王茶姬", "古茗", "一点点", "coco", "奶茶", "喜茶", "奈雪", "茶颜", "沪上阿姨", "书亦") to "🧋",
    listOf("瑞幸", "星巴克", "咖啡", "库迪", "manner", "luckin") to "☕",
    listOf("麦当劳", "肯德基", "汉堡", "塔斯汀", "华莱士", "德克士", "汉堡王", "kfc") to "🍔",
    listOf("必胜客", "披萨", "达美乐", "pizza") to "🍕",
    listOf("火锅", "海底捞", "呷哺", "串串", "冒菜") to "🍲",
    listOf("麻辣烫", "杨国福", "张亮", "麻辣香锅") to "🍲",
    listOf("拉面", "面馆", "牛肉面", "米线", "米粉", "螺蛳粉", "粉面", "面") to "🍜",
    listOf("饺子", "沙县", "馄饨", "包子", "小笼", "扁肉") to "🥟",
    listOf("烧烤", "烤串", "串", "烤肉") to "🍢",
    listOf("寿司", "日料", "刺身") to "🍣",
    listOf("炸鸡", "鸡排", "鸡腿", "正新") to "🍗",
    listOf("蛋糕", "甜品", "烘焙", "面包") to "🍰",
    listOf("冰淇淋", "雪糕", "dq") to "🍦",
    listOf("沙拉", "轻食") to "🥗",
    listOf("便当", "快餐", "盒饭", "简餐") to "🍱",
    listOf("盖饭", "炒饭", "米饭", "饭") to "🍚",
    listOf("水果") to "🍓",
    listOf("酒", "烧鸟", "居酒屋") to "🍻",
)

fun suggestEmoji(name: String): String? {
    val n = name.lowercase()
    // 取「命中的最长关键词」，而不是「第一个命中的分组」。
    // 分组里有一些很通用的单字（比如「面」「饭」），排在前面时会把更具体的词盖掉：
    // 「面包新语」本该是 🍰，却先命中了「面」变成 🍜。
    // 同样长的关键词仍然按 EmojiKeywords 的顺序取第一个。
    return EmojiKeywords
        .flatMap { (words, emoji) -> words.map { it to emoji } }
        .filter { (word, _) -> word in n }
        .maxByOrNull { (word, _) -> word.length }
        ?.second
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/**
 * 用手机上的地图 App（高德、百度、腾讯…）搜这个地址，有多个会让用户选；
 * 一个地图 App 都没装就打开高德网页版。需要 AndroidManifest 里的 <queries> 才能查到地图 App。
 */
fun openMap(context: Context, shop: Shop) {
    val query = shop.address.ifBlank { shop.name }
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
    val hasMapApp = context.packageManager.queryIntentActivities(geo, 0).isNotEmpty()
    val intent = if (hasMapApp) {
        Intent.createChooser(geo, "用哪个地图打开")
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/search?keyword=" + Uri.encode(query)))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(context, "手机上没有地图和浏览器，先装一个高德地图吧")
    }
}

/** 打开团购页 / 分享链接。文字里夹着链接也行（直接粘贴美团、点评的分享文字）。 */
fun openLink(context: Context, raw: String) {
    val url = extractUrl(raw)
    if (url == null) {
        toast(context, "没找到链接")
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        toast(context, "打不开这个链接")
    }
}

fun extractUrl(raw: String): String? = Regex("""https?://[^\s，。、"'）)】]+""").find(raw)?.value

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("地址", text))
    toast(context, "复制好啦")
}

