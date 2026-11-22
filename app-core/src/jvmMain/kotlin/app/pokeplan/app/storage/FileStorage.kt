package app.pokeplan.app.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class FileStorage(private val file: File) : Storage {
    private val folder: File get() = file.absoluteFile.parentFile

    override val shareKey: Any get() = file.absolutePath
    private val backup: File get() = File(folder, file.name + ".bak")

    override fun load(): String? = if (file.exists()) file.readText() else null

    override fun loadBackup(): String? = if (backup.exists()) backup.readText() else null

    override fun save(text: String) {
        folder.mkdirs()
        val temp = File(folder, file.name + ".tmp")
        try {
            writeAndSync(temp, text)
            if (file.exists()) replace(File(folder, backup.name + ".tmp").also { Files.copy(file.toPath(), it.toPath(), REPLACE_EXISTING) }, backup)
            replace(temp, file)
        } finally {
            temp.delete()
        }
    }

    override fun keepAside(text: String) {
        folder.mkdirs()
        File(folder, "${file.name}.unreadable-${System.currentTimeMillis()}").writeText(text)
    }

    private fun writeAndSync(target: File, text: String) {
        FileOutputStream(target).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    private fun replace(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), REPLACE_EXISTING)
        }
    }
}
