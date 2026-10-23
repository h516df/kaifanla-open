package com.my.firstapp

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 抖省省 / 抖音系 App 的可能包名。
 *
 * 本机没有装抖省省，**没法实测真实包名**（见 PR 里的探针说明），
 * 所以这里做成候选列表：能拉起就拉起，拉不起来就退化成「复制店名」，
 * 让用户自己去 App 里搜。宁可多一步，也不伪造 deep link 或商品链接。
 */
private val DoushengshengPackages = listOf(
    "com.ss.android.ugc.aweme.local",
    "com.ss.android.ugc.doushengsheng",
    "com.douyin.dss",
    "com.ss.android.ugc.aweme",
    "com.ss.android.ugc.aweme.lite",
)

/**
 * 「去抖省省」。
 *
 * 三种情况：
 * 1. 有真实分享链接 → 直接打开链接（这是用户自己分享过来的，不是我们编的）；
 * 2. 装了抖省省/抖音 → 拉起 App，并把店名复制好，方便直接搜；
 * 3. 都没装 → 只复制店名，明确告诉用户去 App 里搜。
 */
fun openDoushengsheng(context: Context, shopName: String, sourceUrl: String = "") {
    if (sourceUrl.isNotBlank()) {
        openLink(context, sourceUrl)
        return
    }
    val manager = context.packageManager
    val launched = DoushengshengPackages.any { pkg ->
        val intent = manager.getLaunchIntentForPackage(pkg) ?: return@any false
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
    if (shopName.isNotBlank()) copyText(context, shopName)
    val message = when {
        launched && shopName.isNotBlank() -> "已经打开抖省省，店名也复制好了，搜一下就行～"
        launched -> "已经打开抖省省～"
        shopName.isNotBlank() -> "没找到抖省省，店名已经复制，去 App 里搜一下～"
        else -> "没找到抖省省，去 App 里搜一下店铺名吧～"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

