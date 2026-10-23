package com.my.firstapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val sound by lazy { SoundFx(applicationContext) }

    /** 系统分享进来的内容。先在 Activity 里接住，再交给 AppViewModel 去 OCR。 */
    private var incoming by mutableStateOf<SharePayload?>(null)
    private var shareConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shareConsumed = savedInstanceState?.getBoolean("shareConsumed") ?: false
        incoming = if (shareConsumed) null else intent?.sharePayloadOrNull()
        setContent {
            val dark = AppColors.palette.style == ThemeStyle.Midnight
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT) { dark },
                )
            }
            CompositionLocalProvider(LocalSound provides sound) {
                CuteTheme {
                    App(
                        shared = incoming,
                        onSharedConsumed = { incoming = null; shareConsumed = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.sharePayloadOrNull()?.let {
            shareConsumed = false
            incoming = it
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("shareConsumed", shareConsumed)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
    }
}

/** 只有真正的分享 Intent 才处理，别把普通启动当成分享。 */
private fun Intent.sharePayloadOrNull(): SharePayload? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val payload = toSharePayload()
    return payload.takeIf { !it.isEmpty }
}

private val Tabs = listOf(R.drawable.ic_tab_meal to "吃什么", R.drawable.ic_tab_shops to "店铺", R.drawable.ic_tab_history to "小本本", R.drawable.ic_tab_profile to "我的")

@Composable
internal fun App(
    shared: SharePayload? = null,
    onSharedConsumed: () -> Unit = {},
    vm: AppViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val decisionHolder = rememberSaveableStateHolder()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.updater.onForeground()
            if (event == Lifecycle.Event.ON_PAUSE) vm.updater.onBackground()
        }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.updater.onForeground()
        onDispose { owner.lifecycle.removeObserver(observer); vm.updater.onBackground() }
    }
    val sound = LocalSound.current
    SideEffect { sound?.enabled = vm.settings.sound }

    // 从系统分享进来：直接进 OCR → 统一导入预览，用户不用先点「识别截图」。
    LaunchedEffect(shared) {
        if (shared != null && !shared.isEmpty) {
            vm.startShareImport(shared)
            onSharedConsumed()
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.background)) {
        FloatingBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if(vm.importDraft==null) NavigationBar(containerColor = AppColors.Surface.copy(alpha = 0.96f)) {
                    Tabs.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(painterResource(icon),contentDescription=null,modifier=Modifier.size(24.dp)) },
                            label = { Text(label, fontSize = 13.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AppColors.Light,
                                selectedIconColor = AppColors.Deep,
                                unselectedIconColor = AppColors.SubText,
                                selectedTextColor = AppColors.Deep,
                                unselectedTextColor = AppColors.SubText,
                            ),
                        )
                    }
                }
            },
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
                vm.storageError?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().background(AppColors.Light).padding(12.dp),
                        color = AppColors.Deep,
                    )
                }
                Box(Modifier.weight(1f)) {
                    val draft=vm.importDraft
                    if(draft!=null) {
                        val session=vm.importSessionId
                        key(session) {
                            ImportPreviewScreen(draft,
                                onDraftChanged={vm.updateImportDraft(it,session)},
                                onDismiss={vm.cancelImport(session)},
                                onConfirm={if(vm.confirmImport(it,session))tab=1})
                        }
                    } else when (tab) {
                        0 -> decisionHolder.SaveableStateProvider("mealDecision") { SpinScreen(vm, onGoShops = { tab = 1 }) }
                        1 -> ShopsScreen(vm)
                        2 -> HistoryScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
        }
    }

    vm.importBusy?.let { message -> ImportBusyDialog(message) }
    vm.importError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissImportError() },
            title = { Text("请再核对一下", fontFamily = CuteFont, fontSize = 22.sp) },
            text = { Text(message, fontSize = 14.sp, color = AppColors.SubText) },
            confirmButton = { TextButton(onClick = { vm.dismissImportError() }) { Text("好") } },
            containerColor = AppColors.Surface,
        )
    }

}

@Composable
private fun ImportBusyDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在认字… 🔍", fontFamily = CuteFont, fontSize = 22.sp) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = AppColors.Primary, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                Text(message, fontSize = 14.sp, color = AppColors.SubText)
            }
        },
        confirmButton = {},
        containerColor = AppColors.Surface,
    )
}

