package com.my.firstapp

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.view.ContextThemeWrapper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[34], application=Application::class, qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
class MealUiSnapshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun decide() = render("decide")
    @Test fun direct() = render("direct")
    @Test fun mint() = render("mint")
    @Test fun midnight() = render("midnight")
    @Test fun midnightSettings() = render("midnight-settings")
    @Test fun pendingUpdateIsExplicitAndHasNoActionableDownload() {
        val vm = fixture("midnight")
        compose.setContent { CuteTheme { Box(Modifier.fillMaxSize().background(AppColors.background)) { MealUpdateScreen(vm.updater,{}) } } }
        compose.onNodeWithText("发行源待配置").assertExists()
        compose.onNodeWithText("立即更新").assertDoesNotExist()
        compose.onNodeWithText("检查更新").assertDoesNotExist()
        compose.runOnIdle {
            check(vm.updater.state == dev.h516.update.UpdateState.Unsupported)
            vm.updater.onForeground(); vm.updater.check(); vm.updater.download(); vm.updater.install()
            check(vm.updater.state == dev.h516.update.UpdateState.Unsupported)
        }
        val output = File("build/outputs/meal-ui/pending-update.png"); output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
        vm.updater.close()
    }
    @Test fun longLabels() = render("long")
    @Test fun history() = render("history")
    @Test fun empty() = render("empty")
    @Test @Config(qualifiers="zh-rCN-w320dp-h780dp-xhdpi")
    fun narrow() = render("narrow", 1.3f)
    @Test fun timezoneChangeInvalidatesHistoryGrouping() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val app = RuntimeEnvironment.getApplication()
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val history = listOf("2026-09-30 23:30", "2026-10-01 00:30").map {
                Record(date.parse(it)!!.time,"🍜","合成店铺","合成餐食",20.0,15.0)
            }
            val today = SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(Date())
            app.filesDir.mkdirs()
            File(app.filesDir,"data.json").writeText(DataJson.encode(AppData(emptyList(),history,Settings(lastAutoBackupDay=today),SkipToday())))
            val vm = AppViewModel(app)
            val revision = mutableIntStateOf(0)
            compose.setContent {
                val context = remember(revision.intValue) { ContextThemeWrapper(app, android.R.style.Theme_Material_Light_NoActionBar) }
                CompositionLocalProvider(LocalContext provides context) { CuteTheme { HistoryScreen(vm) } }
            }
            compose.onAllNodesWithText("2026年9月30日",substring=true).assertCountEquals(1)
            compose.runOnIdle {
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
                revision.intValue++
            }
            compose.onAllNodesWithText("2026年9月30日",substring=true).assertCountEquals(0)
            compose.onAllNodesWithText("2026年10月1日",substring=true).assertCountEquals(1)
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun zeroAnimationScaleStillSelectsAndSavesOnce() {
        val app = RuntimeEnvironment.getApplication()
        val oldScale = AndroidSettings.Global.getFloat(app.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            AndroidSettings.Global.putFloat(app.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val vm = fixture("decide")
            compose.setContent { CuteTheme { SpinScreen(vm,{}) } }
            compose.onNodeWithText("摇出这一餐").performClick()
            compose.onNodeWithText("就吃这个，记下来").assertExists().performScrollTo().performClick()
            compose.runOnIdle { check(vm.history.size == 2) }
            compose.onNodeWithText("就吃这个，记下来").assertDoesNotExist()
        } finally { AndroidSettings.Global.putFloat(app.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, oldScale) }
    }
    @Test fun leavingDuringSpinCancelsWithoutSavingAndAllowsAnotherSpin() {
        val vm = fixture("decide")
        val visible = mutableStateOf(true)
        compose.setContent { CuteTheme { if(visible.value) SpinScreen(vm,{}) } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("摇出这一餐").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("偏好").assertIsNotEnabled()
        compose.runOnIdle { visible.value = false }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("正在挑选…").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithText("摇出这一餐").assertIsEnabled()
        compose.runOnIdle { check(vm.history.size == 1) }
        compose.onNodeWithText("这次的选择").assertDoesNotExist()
    }
    @Test fun preferencesOpenFromIdle() {
        val vm=fixture("decide")
        compose.setContent { CuteTheme { SpinScreen(vm,{}) } }
        compose.onNodeWithText("偏好").performClick()
        compose.onNodeWithText("这一餐的偏好").assertExists()
        val output=File("build/outputs/meal-ui/preferences.png");output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
        compose.onNodeWithText("好啦").performClick()
    }
    @Test fun leverDragProducesOneTicketAndPreferencesInvalidateIt() {
        val vm=fixture("decide")
        compose.setContent { CuteTheme { SpinScreen(vm,{}) } }
        compose.onNodeWithContentDescription("拉杆，选择这一餐").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f,100f),300)
        }
        compose.waitForIdle()
        compose.onNodeWithText("这次的选择").assertExists()
        compose.onNodeWithText("就吃这个，记下来").performScrollTo().assertIsDisplayed()
        val output=File("build/outputs/meal-ui/ticket.png");output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
        compose.onNodeWithText("偏好").performScrollTo()
        compose.onNodeWithText("偏好").performClick()
        compose.onNodeWithText("就吃这个，记下来").assertDoesNotExist()
        compose.runOnIdle { check(vm.history.size==1) }
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h780dp-xhdpi")
    fun narrowLargeFontTicketCanBeConfirmed() {
        val vm=fixture("narrow",1.3f)
        compose.setContent { CuteTheme { SpinScreen(vm,{}) } }
        compose.onNodeWithContentDescription("拉杆，选择这一餐").performClick()
        compose.onNodeWithText("就吃这个，记下来").performScrollTo().assertIsDisplayed()
        val output=File("build/outputs/meal-ui/narrow-ticket.png");output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
        compose.onNodeWithText("就吃这个，记下来").performClick()
        compose.runOnIdle { check(vm.history.size==2) }
    }
    @Test fun importPreviewKeepsEditingStableAndConfirmsExplicitly() {
        fixture("midnight")
        val state=mutableStateOf(ImportDraft(shopName="合成测试店",platform="meituan",
            deals=listOf(DraftDeal("双人套餐",39.0,89.0,conditionsText="仅限新会员；有效期未知",platform="meituan"))))
        var confirmed:ImportDraft?=null
        compose.setContent { CuteTheme { Box(Modifier.fillMaxSize().background(AppColors.background)) { ImportPreviewScreen(state.value,{state.value=it},{},{confirmed=it}) } } }
        compose.onNodeWithText("核对这次导入").assertExists()
        compose.onRoot().captureRoboImage(File("build/outputs/meal-ui/import-preview.png").absolutePath)
        compose.onNodeWithText("页面报价，元").performScrollTo().performTextReplacement("-2")
        compose.onNodeWithText("确认导入 1 项").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("页面报价，元").performScrollTo().performTextReplacement("36.50")
        compose.runOnIdle { check(state.value.deals.single().currentPrice==36.5);check(!state.value.deals.single().priceConfirmed) }
        compose.onNodeWithText("确认导入 1 项").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { check(confirmed!=null);check(!confirmed!!.retainRawText);check(!confirmed!!.deals.single().priceConfirmed) }
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h780dp-xhdpi")
    fun narrowImportPreviewKeepsUnconfirmedPriceOutOfBudget() {
        fixture("mint",1.3f)
        val state=mutableStateOf(ImportDraft(shopName="合成店",dishes=listOf(DraftDish("招牌面",12.0,
            priceConditions="仅限会员，价格适用条件待核对",priceConfirmed=false))))
        compose.setContent { CuteTheme { Box(Modifier.fillMaxSize().background(AppColors.background)) { ImportPreviewScreen(state.value,{state.value=it},{},{}) } } }
        compose.onNodeWithText("这道菜的已核对价格，元").performScrollTo().performTextReplacement("15")
        compose.onNodeWithText("金额和条件已核对；未确认时仅作价格待确认").performScrollTo().assertExists()
        compose.onRoot().captureRoboImage(File("build/outputs/meal-ui/import-narrow.png").absolutePath)
        compose.runOnIdle {
            val saved=applyDraft(emptyList(),state.value,{1L},100L).single().dishes.single()
            check(saved.paid==null)
        }
    }

    private fun fixture(name: String, fontScale: Float = 1f): AppViewModel {
        RuntimeEnvironment.setFontScale(fontScale)
        val app = RuntimeEnvironment.getApplication()
        val shops = listOf(
            Shop(1, if(name=="long") "巷口一家名字特别特别长的手工牛肉面馆" else "巷口小面", "🍜", listOf(Dish(11, "招牌牛肉面", 28.0, 22.0),Dish(12, "番茄鸡蛋面", 18.0, 15.0))),
            Shop(2, "一起吃饭", "🍚", listOf(Dish(21, "照烧鸡腿饭", 32.0, 25.0))),
            Shop(3, "暖暖小锅", "🍲", listOf(Dish(31, "菌菇小火锅", 45.0, 38.0))),
            Shop(4, "周末披萨", "🍕", listOf(Dish(41, "芝士披萨", 48.0, 39.0))),
        )
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val data = AppData(if(name=="empty") emptyList() else shops,
            listOf(Record(System.currentTimeMillis(),"🍜","巷口小面","招牌牛肉面",28.0,22.0)),
            Settings(theme=when(name) { "mint" -> "mint"; "midnight", "midnight-settings" -> "lavender"; else -> "strawberry" },avoidDays=0,lastAutoBackupDay=today),SkipToday())
        app.filesDir.mkdirs()
        File(app.filesDir,"data.json").writeText(DataJson.encode(data))
        return AppViewModel(app)
    }
    private fun render(name: String, fontScale: Float = 1f) {
        val vm = fixture(name, fontScale)
        compose.setContent { CuteTheme { Box(Modifier.fillMaxSize().background(AppColors.background)) {
            FloatingBackground(Modifier.fillMaxSize())
            when(name) { "history" -> HistoryScreen(vm); "midnight-settings" -> SettingsScreen(vm); else -> SpinScreen(vm,{}) }
        } } }
        compose.waitForIdle()
        if(name=="history") {
            compose.onNodeWithText("清空所有记录").assertDoesNotExist()
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("清空所有记录").performClick()
            compose.onNodeWithText("清空所有记录？").assertExists()
            compose.onNodeWithText("算了").performClick()
            compose.onNodeWithText("清空所有记录？").assertDoesNotExist()
            check(vm.history.size == 1) { "Cancelling must preserve the saved meal" }
        }
        if(name=="direct") {
            compose.waitForIdle()
        }
        val output = File("build/outputs/meal-ui/$name.png"); output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
    }
}
