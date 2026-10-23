package com.my.firstapp

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.h516.update.Updater

@Composable
fun MealUpdateScreen(updater: Updater, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val notes = remember(updater) { updater.currentNotes() }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick=onBack) { Text("返回我的", color = AppColors.Deep) }
        if (!MealUpdateChannel.Pending.ready) {
            ScreenColumn {
                PageTitle("应用更新", "当前版本 ${updater.installedVersionName()}（${updater.installedVersionCode()}）")
                CuteCard {
                    Text("应用内更新通道尚未启用。菜单和用餐记录仍保留在本机，可以继续正常使用。", color = AppColors.Text)
                    Text("安装包请从版本发布页下载，需要有权限的 GitHub 账号登录。保留旧版直接覆盖安装。", color = AppColors.SubText)
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/h516df/kaifanla/releases")))
                        }
                    }) { Text("查看版本发布页", color = AppColors.Deep) }
                }
                if (notes != null) CuteCard {
                    Text("本版说明 ${notes.version}", fontFamily = CuteFont, color = AppColors.Text)
                    notes.sections.forEach { (heading, lines) ->
                        Text(heading, fontWeight = FontWeight.SemiBold, color = AppColors.Deep)
                        lines.forEach { Text(it, color = AppColors.Text) }
                    }
                } else Text("本版说明暂时无法读取，请到版本发布页查看。", color = AppColors.SubText)
            }
        } else dev.h516.update.UpdateScreen(updater, PaddingValues(0.dp))
    }
}
