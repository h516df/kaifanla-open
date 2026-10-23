package dev.h516.update

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class RuntimeReleaseNotesTest {
    @Test fun bundledNotesParseThroughTheProductionReaderAndMatchTheInstalledVersion() {
        val app = RuntimeEnvironment.getApplication()
        val updater = Updater(app)
        try {
            val notes = requireNotNull(updater.currentNotes()) { "Bundled release notes must be readable offline" }
            assertEquals(updater.installedVersionName(), notes.version)
            assertEquals(updater.installedVersionCode(), notes.code)
            assertEquals(ReleaseNotes.HEADINGS, notes.sections.map { it.first })
            assertTrue(notes.sections.all { (_, lines) -> lines.isNotEmpty() && lines.all(String::isNotBlank) })
            assertTrue(notes.sections.last().second.any { it.contains("更新通道") && it.contains("未启用") })
        } finally {
            updater.close()
        }
    }
}
