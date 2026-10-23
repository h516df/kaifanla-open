package com.my.firstapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView

/** 新建 / 编辑店铺：店名、图标、地址、团购链接。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopDialog(
    title: String,
    initial: ShopInfo,
    onDismiss: () -> Unit,
    onSave: (ShopInfo) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var address by remember { mutableStateOf(initial.address) }
    var walk by remember { mutableStateOf(initial.walkMinutes?.toString().orEmpty()) }
    var link by remember { mutableStateOf(initial.link) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    var keepPhoto by remember { mutableStateOf(initial.photo.isNotBlank()) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedPhoto = uri
            keepPhoto = false
        }
    }
    // 用户自己挑过图标后，就不再根据店名自动换
    var emojiTouched by remember { mutableStateOf(initial.name.isNotBlank()) }
    var group by remember { mutableIntStateOf(0) }
    var morePicker by remember { mutableStateOf(false) }

    fun pick(e: String) {
        emoji = e
        emojiTouched = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = CuteFont, fontSize = 24.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(emoji, size = 60.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.replace("\n", "")
                            if (!emojiTouched) suggestEmoji(name)?.let { s -> emoji = s }
                        },
                        label = { Text("店名") },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                if (keepPhoto || selectedPhoto != null) {
                    ShopArtwork(
                        emoji = emoji,
                        photo = if (keepPhoto) initial.photo else "",
                        pendingPhoto = selectedPhoto,
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        fallbackSize = 60.dp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("📷 从相册选封面", color = AppColors.Deep)
                    }
                    if (keepPhoto || selectedPhoto != null) {
                        TextButton(onClick = { keepPhoto = false; selectedPhoto = null }) {
                            Text("移除照片", color = AppColors.SubText)
                        }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.replace("\n", " ") },
                    label = { Text("📍 地址（可不填）") },
                    placeholder = { Text("比如：万达广场 3 楼") },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("🔗 团购 / 地图链接（可不填）") },
                    placeholder = { Text("粘贴美团、点评、高德的分享") },
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = walk, onValueChange = { walk = it.trim() },
                    label = { Text("步行分钟（可不填）") },
                    supportingText = { Text("自己核对路线后的估计，换出发地后可能不准") },
                    isError = walk.isNotBlank() && walk.toIntOrNull()?.let { it in 0..240 } != true,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                Text("选个小图标", fontSize = 14.sp, color = AppColors.SubText)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FoodEmojiGroups.forEachIndexed { i, (label, _) ->
                        Pill(label, active = i == group) { group = i }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FoodEmojiGroups[group].second.forEach { e ->
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (e == emoji) AppColors.Primary else Color.Transparent, CircleShape)
                                .clickable { pick(e) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(e, fontSize = 22.sp)
                        }
                    }
                }
                SoftButton("🔍 更多表情", onClick = { morePicker = true }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (walk.isBlank() || walk.toIntOrNull()?.let { it in 0..240 } == true),
                onClick = { onSave(ShopInfo(name.trim(), emoji, address.trim(), link.trim(), if (keepPhoto) initial.photo else "", selectedPhoto, walk.toIntOrNull())) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = AppColors.Surface,
    )

    if (morePicker) {
        // Google 官方的表情选择器（androidx.emoji2:emoji2-emojipicker），带分类和最近使用
        AlertDialog(
            onDismissRequest = { morePicker = false },
            title = { Text("挑一个表情", fontFamily = CuteFont, fontSize = 22.sp) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            emojiGridColumns = 8
                            setOnEmojiPickedListener { item ->
                                pick(item.emoji)
                                morePicker = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(380.dp),
                )
            },
            confirmButton = { TextButton(onClick = { morePicker = false }) { Text("关闭") } },
            containerColor = AppColors.Surface,
        )
    }
}

