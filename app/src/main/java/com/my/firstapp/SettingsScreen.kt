package com.my.firstapp

import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    var showUpdate by remember { mutableStateOf(false) }
    if (showUpdate) {
        MealUpdateScreen(vm.updater) { showUpdate = false }
        return
    }
    var editingName by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val bytes = vm.exportJson().toByteArray()
                context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            }.isSuccess
            toast(if (ok) "备份好啦 💾" else "备份失败了，再试一次吧")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestore = runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.getOrNull()
            if (pendingRestore == null) toast("读不了这个文件")
        }
    }

    ScreenColumn {
        PageTitle("我的 💝", "一些专属小设置")

        CuteCard(onClick = { editingName = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge("💝", size = 64.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    val nick = vm.settings.nickname
                    Text(
                        if (nick.isBlank()) "还没有专属称呼" else nick,
                        fontSize = 24.sp,
                        fontFamily = CuteFont,
                        color = AppColors.Text,
                    )
                    Text("点这里设置，首页会叫这个名字", fontSize = 13.sp, color = AppColors.SubText)
                }
                Text("›", fontSize = 26.sp, color = AppColors.SubText)
            }
        }

        SectionLabel("🎨 主题皮肤")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p ->
                        ThemeChip(p, selected = p.id == vm.settings.theme, modifier = Modifier.weight(1f)) {
                            if (!vm.updateSettings { it.copy(theme = p.id) }) toast("没保存成功，请再试一次")
                        }
                    }
                }
            }
        }

        SectionLabel("⬆️ 应用更新")
        CuteCard(onClick = { showUpdate = true }) {
            Text("检查新版", color = AppColors.Text, fontFamily = CuteFont, fontSize = 18.sp)
            Text(vm.updater.hint() ?: if(MealUpdateChannel.Pending.ready) "下载由你决定，资料保留" else "发行源待配置 · 暂不可更新",
                color = AppColors.SubText, fontSize = 13.sp)
        }

        SectionLabel("🔔 小设置")
        CuteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎵", fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("选择音效", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text("咔哒咔哒～叮！", fontSize = 13.sp, color = AppColors.SubText)
                }
                Switch(checked = vm.settings.sound, onCheckedChange = { on ->
                    if (!vm.updateSettings { it.copy(sound = on) }) toast("没保存成功，请再试一次")
                })
            }
        }

        SectionLabel("💾 数据")
        CuteCard {
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "🛡️ 每天第一次打开会自动备份到「下载/今天吃什么」，最多保留 7 份（不含封面照片）。"
                } else {
                    "🛡️ 这台手机系统较旧，请继续用下面的手动备份。"
                },
                fontSize = 13.sp,
                color = AppColors.SubText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            vm.autoBackupWarning?.let { warning ->
                Text(warning, fontSize = 13.sp, color = AppColors.Deep, modifier = Modifier.padding(bottom = 8.dp))
            }
            HorizontalDivider(color = AppColors.Line)
            SettingRow("📤", "备份到文件", "会备份店和记录；封面照片不会包含在内") {
                val date = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
                exportLauncher.launch("今天吃什么备份_$date.json")
            }
            HorizontalDivider(color = AppColors.Line)
            SettingRow("📥", "从备份恢复", "会替换店和记录；封面照片不会恢复") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }
            HorizontalDivider(color = AppColors.Line)
            SettingRow("💌", "分享菜单给朋友", "发到微信，对方粘贴就能用") {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.shareMenuText())
                }
                context.startActivity(Intent.createChooser(intent, "分享菜单"))
            }
            HorizontalDivider(color = AppColors.Line)
            SettingRow("📋", "粘贴导入菜单", "把朋友分享的店和菜加进来") { pasting = true }
        }

        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        Text(
            "开饭啦 · v$version\n用心做给你的小转盘 💗",
            fontSize = 12.sp,
            color = AppColors.SubText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        )
    }

    if (editingName) {
        var name by remember { mutableStateOf(vm.settings.nickname) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("专属称呼 💝", fontFamily = CuteFont, fontSize = 24.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("比如：宝宝、小美、猪猪～", fontSize = 13.sp, color = AppColors.SubText)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.replace("\n", "").take(10) },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.updateSettings { it.copy(nickname = name.trim()) }) editingName = false
                    else toast("没保存成功，请再试一次")
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("取消") } },
            containerColor = AppColors.Surface,
        )
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text("粘贴导入菜单 📋", fontFamily = CuteFont, fontSize = 24.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("把朋友分享的那一整段文字粘贴进来。已有的店不会被覆盖，只会补上没有的菜。", fontSize = 13.sp, color = AppColors.SubText)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SoftButton("从剪贴板粘贴", onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    })
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    val n = vm.mergeMenu(text)
                    toast(
                        when {
                            n < 0 && vm.storageError != null -> "导入没保存成功，原存档还在"
                            n < 0 -> "没认出菜单，检查一下是不是复制全了"
                            n == 0 -> "这些店和菜都已经有啦"
                            else -> "导入好啦，更新了 $n 家店 🎉"
                        },
                    )
                    if (n >= 0) pasting = false
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text("取消") } },
            containerColor = AppColors.Surface,
        )
    }

    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("从备份恢复？") },
            text = { Text("现在的店铺和小本本会被备份里的内容替换掉。封面照片不会恢复，请先留好原图。") },
            confirmButton = {
                TextButton(onClick = {
                    val restored = vm.restore(text)
                    toast(when {
                        restored -> "恢复好啦 🎉"
                        vm.storageError != null -> "恢复没保存成功，原存档还在"
                        else -> "这个文件不是备份文件哦"
                    })
                    if (restored) pendingRestore = null
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("算了") } },
            containerColor = AppColors.Surface,
        )
    }
}

@Composable
private fun SettingRow(emoji: String, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
            Text(hint, fontSize = 13.sp, color = AppColors.SubText)
        }
        Text("›", fontSize = 22.sp, color = AppColors.SubText)
    }
}

@Composable
private fun ThemeChip(palette: Palette, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(palette.cardRadius)
    Column(
        modifier
            .shadow(if (selected) 10.dp else 4.dp, shape, spotColor = palette.primary)
            .clip(shape)
            .background(palette.surface)
            .border(2.5.dp, if (selected) palette.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(palette.bgTop, palette.light))),
            contentAlignment = Alignment.Center,
        ) {
            Text(palette.emoji, fontSize = 20.sp)
        }
        Text(
            palette.name,
            fontSize = 15.sp,
            fontFamily = when(palette.style) {
                ThemeStyle.Cream -> androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.cute))
                ThemeStyle.Midnight -> androidx.compose.ui.text.font.FontFamily.Serif
                ThemeStyle.Mint -> androidx.compose.ui.text.font.FontFamily.SansSerif
            },
            color = palette.text,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

