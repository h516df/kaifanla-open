package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AppDataStoreTest {
    private val first = AppData(
        shops = listOf(Shop(1, "第一家", "🍜", emptyList())),
        history = emptyList(),
        settings = Settings(),
        skip = SkipToday(),
    )
    private val second = first.copy(shops = listOf(Shop(2, "第二家", "🍚", emptyList())))

    // Windows needs an explicit replacement for the JVM test; Android's same-directory rename is atomic.
    private val replace: (File, File) -> Boolean = { source, target ->
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }

    @Test fun missingFileIsFirstRunAndNormalSavesKeepLastGoodCopy() = withTempDir { dir ->
        val file = File(dir, "data.json")
        val store = AppDataStore(file, replace)
        assertEquals(DataLoad.Missing, store.load())

        store.save(first)
        assertEquals(first, (store.load() as DataLoad.Loaded).data)
        store.save(second)
        assertEquals(second, (store.load() as DataLoad.Loaded).data)
        assertEquals(first, DataJson.decode(File(dir, "data.json.bak").readText()))
    }

    @Test fun oldSaveWithoutSettingsStillLoads() = withTempDir { dir ->
        val file = File(dir, "data.json")
        file.writeText("""{"shops":[],"history":[]}""")
        assertEquals(Settings(), (AppDataStore(file, replace).load() as DataLoad.Loaded).data.settings)
    }

    @Test fun corruptOrUnreadableExistingDataIsNeverOverwritten() = withTempDir { dir ->
        val file = File(dir, "data.json")
        file.writeText("{bad json")
        val store = AppDataStore(file, replace)
        assertTrue(store.load() is DataLoad.Failed)
        assertFailsSave(store, first)
        assertEquals("{bad json", file.readText())

        file.writeText("{}")
        assertTrue(store.load() is DataLoad.Failed)
        assertFailsSave(store, first)
        assertEquals("{}", file.readText())

        val wrongDishes = """{"shops":[{"id":1,"name":"旧店","dishes":{}}],"history":[]}"""
        file.writeText(wrongDishes)
        assertTrue(store.load() is DataLoad.Failed)
        assertFailsSave(store, first)
        assertEquals(wrongDishes, file.readText())

        file.delete()
        file.mkdir()
        assertTrue(store.load() is DataLoad.Failed)
        assertFailsSave(store, first)
        assertTrue(file.isDirectory)
    }

    @Test fun missingPrimaryWithBackupIsNotTreatedAsFirstRun() = withTempDir { dir ->
        val file = File(dir, "data.json")
        File(dir, "data.json.bak").writeText(DataJson.encode(first))
        val store = AppDataStore(file, replace)
        assertTrue(store.load() is DataLoad.Failed)
        assertFailsSave(store, second)
        assertFalse(file.exists())
    }

    @Test fun tempWriteFailurePreservesOriginal() = withTempDir { dir ->
        val file = File(dir, "data.json")
        val store = AppDataStore(file, replace)
        store.save(first)
        File(dir, "data.json.tmp").mkdir()
        assertFailsSave(store, second)
        assertEquals(first, (store.load() as DataLoad.Loaded).data)
    }

    @Test fun replacementFailurePreservesOriginal() = withTempDir { dir ->
        val file = File(dir, "data.json")
        AppDataStore(file, replace).save(first)
        val failing = AppDataStore(file) { source, target ->
            if (target == file) false else replace(source, target)
        }
        assertFailsSave(failing, second)
        assertEquals(first, (failing.load() as DataLoad.Loaded).data)
        assertEquals(first, DataJson.decode(File(dir, "data.json.bak").readText()))
    }

    @Test fun failedRecordSaveDoesNotClearTheSelectionOrClaimSuccess() = withTempDir { dir ->
        val file = File(dir, "data.json")
        AppDataStore(file, replace).save(first)
        val failing = AppDataStore(file) { source, target ->
            if (target == file) false else replace(source, target)
        }
        var pickedDish: String? = "想吃的菜"
        var claimedSuccess = false
        val saved = afterSuccessfulSave(runCatching { failing.save(second) }.isSuccess) {
            pickedDish = null
            claimedSuccess = true
        }
        assertFalse(saved)
        assertEquals("想吃的菜", pickedDish)
        assertFalse(claimedSuccess)
        assertEquals(first, (failing.load() as DataLoad.Loaded).data)
    }

    @Test fun backupFailurePreservesOriginal() = withTempDir { dir ->
        val file = File(dir, "data.json")
        AppDataStore(file, replace).save(first)
        val failing = AppDataStore(file) { source, target ->
            if (target.name.endsWith(".bak")) false else replace(source, target)
        }
        assertFailsSave(failing, second)
        assertEquals(first, (failing.load() as DataLoad.Loaded).data)
        assertFalse(File(dir, "data.json.bak").exists())
    }

    private fun assertFailsSave(store: AppDataStore, data: AppData) {
        try {
            store.save(data)
            throw AssertionError("save unexpectedly succeeded")
        } catch (_: IOException) {
            // Expected: the caller must not report success.
        }
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("app-data-store-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
