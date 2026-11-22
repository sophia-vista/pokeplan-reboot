package app.pokeplan.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.pokeplan.ui.updates.UPDATE_RELEASE_PAGE
import app.pokeplan.ui.updates.UpdateAccess
import app.pokeplan.ui.updates.UpdateClient
import app.pokeplan.ui.updates.UpdateRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The Android side of the in-app update (see `app.pokeplan.ui.updates`): checks GitHub, downloads the APK into the app's
 * own files, checks its size and checksum, and hands it to the system installer.
 *
 * A downloaded APK lives in `filesDir/updates` and reaches the installer through the existing FileProvider, so nothing
 * needs an external-storage permission. Installing over itself needs `REQUEST_INSTALL_PACKAGES` and the phone's
 * "install unknown apps" permission, which is per-source and granted once (the About screen opens that page when it is
 * missing and offers Install again when the player comes back).
 */
class AndroidUpdates(
    private val context: Context,
    private val client: UpdateClient = UpdateClient(),
) : UpdateAccess {
    override var problem: String? = null
        private set

    private val dir: File get() = File(context.filesDir, "updates")

    override suspend fun check(): UpdateRelease? = withContext(Dispatchers.IO) {
        problem = null
        val release = client.latest()
        if (release == null) problem = client.problem
        release
    }

    override suspend fun download(release: UpdateRelease, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        problem = null
        if (downloaded(release)) return@withContext true
        val apk = apkFile(release)
        if (apk == null) {
            problem = "The update is not valid."
            return@withContext false
        }
        dir.mkdirs()
        // A different build's half-finished or finished file has no use any more.
        dir.listFiles()?.forEach { if (it != apk) it.delete() }
        if (dir.usableSpace < release.sizeBytes + 8L * 1024 * 1024) {
            problem = "Not enough room for the update."
            return@withContext false
        }
        val part = File(dir, apk.name + ".part")
        part.delete()
        try {
            val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PokeLife-Updater")
                setRequestProperty("Accept", "application/octet-stream")
                connect()
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("GitHub answered ${connection.responseCode}.")
                val total = if (release.sizeBytes > 0) release.sizeBytes else connection.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                var reported = -1
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(part).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            read += count
                            if (total > 0) {
                                val percent = ((read * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != reported) {
                                    reported = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                if (release.sizeBytes > 0 && part.length() != release.sizeBytes) throw IOException("The download came in short.")
                if (release.sha256.isNotEmpty()) {
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!sha.equals(release.sha256, ignoreCase = true)) throw IOException("The download did not match its checksum.")
                }
                if (!part.renameTo(apk)) {
                    part.copyTo(apk, overwrite = true)
                    part.delete()
                }
                onProgress(100)
                true
            } finally {
                connection.disconnect()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            problem = when (e) {
                is IOException -> e.message?.takeIf { it.isNotBlank() } ?: "The download stopped."
                else -> "The download stopped."
            }
            false
        }
    }

    override fun downloaded(release: UpdateRelease): Boolean {
        val apk = apkFile(release) ?: return false
        return apk.isFile && (release.sizeBytes <= 0 || apk.length() == release.sizeBytes)
    }

    override fun install(release: UpdateRelease): Boolean {
        problem = null
        val apk = apkFile(release)
        if (apk == null || !apk.isFile) {
            problem = "The update is not downloaded."
            return false
        }
        if (!canInstall()) {
            problem = "Allow app installs first."
            openInstallSettings()
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            problem = "The installer could not open."
            false
        }
    }

    override fun canInstall(): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (e: Exception) {
        false
    }

    override fun openInstallSettings() {
        val page = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(page) }.isSuccess ||
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) problem = "Settings could not open."
    }

    override fun openReleasePage() {
        val page = Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_RELEASE_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(page) }.isFailure) problem = "The browser could not open."
    }

    private fun apkFile(release: UpdateRelease): File? {
        // The name comes from a checked release, but it is built here from validated parts so a manifest can never escape
        // the app's own folder.
        if (release.version.any { !(it.isDigit() || it == '.') }) return null
        if (release.build < 0) return null
        return File(dir, "pokeplan-${release.version}-${release.build}.apk")
    }
}
