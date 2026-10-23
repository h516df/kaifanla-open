package com.my.firstapp

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[34],application=Application::class,qualifiers="zh-rCN-w360dp-h720dp-xhdpi")
class MealFlowSnapshotTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(empty:Boolean=false,budget:Double?=null):AppViewModel {
        val app=RuntimeEnvironment.getApplication()
        val today=SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(Date())
        val shops=if(empty)emptyList() else listOf(
            Shop(1,"巷口小面","🍜",listOf(Dish(11,"招牌牛肉面",28.0,22.0),Dish(12,"番茄鸡蛋面",18.0,15.0)),walkMinutes=10),
            Shop(2,"一起吃饭","🍚",listOf(Dish(21,"照烧鸡腿饭",32.0,25.0)),walkMinutes=15))
        File(app.filesDir,"data.json").writeText(DataJson.encode(AppData(shops,emptyList(),Settings(avoidDays=0,budget=budget,lastAutoBackupDay=today),SkipToday())))
        return AppViewModel(app)
    }
    private fun show(vm:AppViewModel) {
        compose.setContent { CuteTheme { App(vm=vm) } }
    }

    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File("build/outputs/meal-flow/$name.png");file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
    @Test fun idleAndResultHaveVisiblePrimaryAction() {
        val vm=fixture();show(vm);shot("idle-360")
        compose.onNodeWithText("拉一下").assertIsDisplayed().performClick()
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
        compose.onNodeWithContentDescription("完整餐食卷轴").assertDoesNotExist()
        compose.runOnIdle { check(vm.history.isEmpty()) }
        shot("result-360")
        compose.onNodeWithText("就吃这个").performClick()
        compose.runOnIdle { check(vm.history.isEmpty()) }
        shot("confirmed-360")
        compose.onNodeWithText("吃过了，记一餐").assertIsDisplayed().performClick()
        compose.onNodeWithText("返回挑选").assertIsDisplayed()
        compose.runOnIdle { check(vm.history.size==1) }
        shot("saved-360")
        compose.onNodeWithText("就吃这个").assertDoesNotExist()
    }
    @Test fun cancelConditionsPreservesResultAndApplyExplainsInvalidation() {
        val vm=fixture();show(vm)
        compose.onNodeWithText("拉一下").performClick()
        compose.onNodeWithText("调整条件").performClick()
        compose.onNodeWithText("¥15").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
        compose.runOnIdle { check(vm.settings.budget==null);check(vm.history.isEmpty()) }
        compose.onNodeWithText("调整条件").performClick()
        compose.onNodeWithText("¥15").performClick()
        shot("conditions-360")
        compose.onNodeWithText("应用条件").assertIsDisplayed().performClick()
        compose.onNodeWithText("就吃这个").assertDoesNotExist()
        compose.onNodeWithText("条件已更新，按新条件重新挑一餐").assertExists()
        compose.onNodeWithText("看看这一餐").performClick()
        compose.onNodeWithText("番茄鸡蛋面",substring=true).assertExists()
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
    }
    @Test fun noChangeApplyKeepsTicket() {
        val vm=fixture();show(vm)
        compose.onNodeWithText("拉一下").performClick()
        compose.onNodeWithText("调整条件").performClick()
        compose.onNodeWithText("应用条件").performClick()
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
    }
    @Test fun noCandidatesCannotSpinOrSave() {
        val vm=fixture(budget=1.0);show(vm);shot("blocked-360")
        compose.onNodeWithText("调整条件").assertIsDisplayed()
        compose.onNodeWithText("超预算 · 3 样").assertExists()
        compose.onNodeWithContentDescription("拉杆，选择这一餐").assertDoesNotExist()
        compose.runOnIdle { check(vm.history.isEmpty()) }
    }
    @Test fun leverProducesOneMealAndSkipDoesNotRecordIt() {
        val vm=fixture();show(vm)
        compose.onNodeWithContentDescription("拉杆，选择这一餐").performTouchInput {
            swipe(center,center+androidx.compose.ui.geometry.Offset(0f,100f),300)
        }
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
        compose.onNodeWithText("今天不想吃").performClick()
        compose.runOnIdle { check(vm.history.isEmpty());check(vm.skip.dishIds.size==1) }
        compose.onNodeWithText("今天先跳过这一样，明天会恢复").assertExists()
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun smallScreenLargeFontNeverScrollsToConfirm() {
        RuntimeEnvironment.setFontScale(1.3f)
        val vm=fixture();show(vm);shot("idle-320-large")
        compose.onNodeWithText("拉一下").assertIsDisplayed().performClick()
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
        shot("result-320-large")
        compose.onNodeWithText("调整条件").assertIsDisplayed().performClick()
        compose.onNodeWithText("应用条件").assertIsDisplayed()
        shot("conditions-320-large")
    }
    @Test fun leavingDuringAnimationDoesNotRecord() {
        val vm=fixture();val visible=mutableStateOf(true)
        compose.setContent { CuteTheme { if(visible.value)SpinScreen(vm,{}) } }
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("拉一下").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("条件").assertIsNotEnabled()
        compose.runOnIdle { visible.value=false }
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        compose.runOnIdle { check(vm.history.isEmpty());visible.value=true }
        compose.onNodeWithText("拉一下").assertIsEnabled()
    }
    @Test fun resultAndConfirmedChoiceRestoreWithoutWritingHistory() {
        val vm=fixture()
        val restoration=StateRestorationTester(compose)
        restoration.setContent { CuteTheme { App(vm=vm) } }
        compose.onNodeWithText("拉一下").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("就吃这个").assertIsDisplayed().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("吃过了，记一餐").assertIsDisplayed()
        compose.runOnIdle { check(vm.history.isEmpty()) }
        compose.onNodeWithText("吃过了，记一餐").performClick()
        compose.runOnIdle { check(vm.history.size==1) }
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun doubleFontHasVisibleConfirm() {
        RuntimeEnvironment.setFontScale(2f)
        val vm=fixture();show(vm)
        compose.onNodeWithText("拉一下").assertIsDisplayed().performClick()
        compose.onNodeWithText("就吃这个").assertIsDisplayed()
        shot("result-320-200percent")
    }
    @Test fun redrawAvoidsRepeatingUntilPoolIsExhausted() {
        val vm=fixture();show(vm)
        compose.onNodeWithText("拉一下").performClick()
        val labels=mutableSetOf<String>()
        repeat(3) { index ->
            val title=compose.onAllNodes(hasText("🍜",substring=true) or hasText("🍚",substring=true))
                .fetchSemanticsNodes().first { node -> node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text){emptyList()}.any { it.text.contains("面") || it.text.contains("饭") } }
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
            check(labels.add(title)){"Repeated meal before the eligible pool was exhausted"}
            if(index<2)compose.onNodeWithText("换一个").performClick()
        }
        compose.onNodeWithText("换一个").performClick()
        compose.onNodeWithText("符合条件的都看过了，开始新一轮").assertExists()
        compose.runOnIdle { check(vm.history.isEmpty()) }
    }
    @Test fun confirmedChoiceSurvivesVisitingAnotherTab() {
        val vm=fixture();show(vm)
        compose.onNodeWithText("拉一下").performClick()
        compose.onNodeWithText("就吃这个").performClick()
        compose.onNodeWithText("小本本").performClick()
        compose.onNodeWithText("吃什么").performClick()
        compose.onNodeWithText("吃过了，记一餐").assertIsDisplayed()
        compose.runOnIdle { check(vm.history.isEmpty()) }
    }
    @Test fun disabledSystemAnimationsStillRevealWithoutSaving() {
        val app=RuntimeEnvironment.getApplication()
        val key=android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        val old=android.provider.Settings.Global.getFloat(app.contentResolver,key,1f)
        try {
            android.provider.Settings.Global.putFloat(app.contentResolver,key,0f)
            val vm=fixture();show(vm)
            compose.onNodeWithText("拉一下").performClick()
            compose.onNodeWithText("就吃这个").assertIsDisplayed()
            compose.runOnIdle { check(vm.history.isEmpty()) }
        } finally { android.provider.Settings.Global.putFloat(app.contentResolver,key,old) }
    }
    @Test fun emptyMenuHasImportEntry() { val vm=fixture(empty=true);show(vm);compose.onNodeWithText("添加菜单 / 导入截图").assertIsDisplayed();shot("empty-360") }
}
