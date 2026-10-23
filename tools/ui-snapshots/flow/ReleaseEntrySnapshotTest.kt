package com.my.firstapp

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [32], application = Application::class, qualifiers = "zh-rCN-w360dp-h720dp-xhdpi")
class ReleaseEntrySnapshotTest {
    @get:Rule val compose = createComposeRule()

    private fun openFromSettings(): AppViewModel {
        val app = RuntimeEnvironment.getApplication()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        File(app.filesDir, "data.json").writeText(DataJson.encode(AppData(
            emptyList(), emptyList(), Settings(lastAutoBackupDay = today), SkipToday(),
        )))
        val vm = AppViewModel(app)
        compose.setContent { CuteTheme { App(vm = vm) } }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("检查新版").performScrollTo().performClick()
        return vm
    }

    @Test fun realSettingsEntryShowsCurrentNotesOfflineAndCanReturn() {
        val vm = openFromSettings()
        val notes = requireNotNull(vm.updater.currentNotes())
        compose.onNodeWithText("当前版本 ${notes.version}（${notes.code}）").assertIsDisplayed()
        val overview = File("build/outputs/meal-flow/update-overview-360.png")
        overview.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(overview.absolutePath)
        compose.onNodeWithText("本版说明 ${notes.version}").performScrollTo().assertIsDisplayed()
        notes.sections.forEach { (heading, lines) ->
            compose.onNodeWithText(heading).performScrollTo().assertIsDisplayed()
            lines.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        }
        compose.onNodeWithText("立即更新").assertDoesNotExist()
        compose.onNodeWithText("打开系统安装").assertDoesNotExist()
        assertFalse(MealUpdateChannel.Pending.ready)
        assertNull(MealUpdateChannel.Pending.apiUrl)
        val image = File("build/outputs/meal-flow/update-notes-360.png")
        image.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(image.absolutePath)
        val app = RuntimeEnvironment.getApplication()
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
        compose.onNodeWithText("查看版本发布页").performScrollTo().performClick()
        val destination = requireNotNull(Shadows.shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, destination.action)
        assertEquals("https://github.com/h516df/kaifanla/releases", destination.dataString)
        compose.onNodeWithText("返回我的").performClick()
        compose.onNodeWithText("检查新版").performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun smallScreenDoubleFontKeepsNotesScrollableAndBackVisible() {
        RuntimeEnvironment.setFontScale(2f)
        val vm = openFromSettings()
        val notes = requireNotNull(vm.updater.currentNotes())
        compose.onNodeWithText(notes.sections.last().second.last()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("返回我的").assertIsDisplayed()
        val image = File("build/outputs/meal-flow/update-notes-320-200percent.png")
        image.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(image.absolutePath)
        compose.onNodeWithText("返回我的").performClick()
        compose.onNodeWithText("检查新版").performScrollTo().assertIsDisplayed()
    }
}
