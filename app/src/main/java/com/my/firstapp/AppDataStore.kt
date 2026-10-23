package com.my.firstapp

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed interface DataLoad {
    data object Missing : DataLoad
    data class Loaded(val data: AppData) : DataLoad
    data class Failed(val cause: Exception) : DataLoad
}

/** Keeps the last valid file until a complete replacement is ready. */
class AppDataStore(
    private val file: File,
    private val replace: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
) {
    fun load(): DataLoad {
        if (!file.exists()) {
            val backup = File(file.parentFile, "${file.name}.bak")
            return if (backup.exists()) {
                DataLoad.Failed(IOException("Primary data is missing but a previous copy exists"))
            } else DataLoad.Missing
        }
        return try {
            DataLoad.Loaded(DataJson.decode(file.readText(Charsets.UTF_8)))
        } catch (e: Exception) {
            DataLoad.Failed(e)
        }
    }

    @Throws(IOException::class)
    fun save(data: AppData) {
        if (!file.exists() && File(file.parentFile, "${file.name}.bak").exists()) {
            throw IOException("Primary data is missing but a previous copy exists")
        }
        val previous = if (file.exists()) {
            val bytes = file.readBytes()
            try {
                DataJson.decode(bytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                throw IOException("Existing data is unreadable; refusing to replace it", e)
            }
            bytes
        } else null

        val bytes = DataJson.encode(data).toByteArray(Charsets.UTF_8)
        file.parentFile?.mkdirs()
        if (previous != null) {
            val backupTemp = File(file.parentFile, "${file.name}.bak.tmp")
            writeSynced(backupTemp, previous)
            if (!replace(backupTemp, File(file.parentFile, "${file.name}.bak"))) {
                throw IOException("Could not preserve previous data")
            }
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        writeSynced(temp, bytes)
        if (!replace(temp, file)) throw IOException("Could not replace data file")
    }

    private fun writeSynced(target: File, bytes: ByteArray) {
        FileOutputStream(target).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
    }
}

/** A save-dependent UI action must run only after the write really succeeded. */
internal inline fun afterSuccessfulSave(saved: Boolean, action: () -> Unit): Boolean {
    if (saved) action()
    return saved
}
