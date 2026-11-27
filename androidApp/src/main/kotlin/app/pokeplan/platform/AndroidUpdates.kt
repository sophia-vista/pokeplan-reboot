package app.pokeplan.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import app.pokeplan.MainActivity
import app.pokeplan.reminders.Channels
import app.pokeplan.reminders.notificationIcon
import app.pokeplan.reminders.notifySafely
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.Place
import app.pokeplan.ui.updates.MAX_PATCH_BYTES
import app.pokeplan.ui.updates.UpdateAccess
import app.pokeplan.ui.updates.UpdateClient
import app.pokeplan.ui.updates.UpdatePatch
import app.pokeplan.ui.updates.UpdateRelease
import app.pokeplan.ui.updates.installRoomProblem
import app.pokeplan.ui.updates.parseUpdatePatch
import app.pokeplan.ui.updates.updateReleasePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * The Android side of the in-app update (see `app.pokeplan.ui.updates`): checks GitHub, downloads the APK into the app's
 * own files, checks its size and checksum, and hands it to the system installer.
 *
 * When the release carries a patch for the installed build, only the patch (about 4 to 20 MB) is downloaded and the
 * new, signed APK is rebuilt from the copy installed on the phone (no signing key is ever in the app); the rebuilt file
 * is checked against the release manifest's size and SHA-256 before it is used. Anything that goes wrong — a different
 * installed build, a damaged patch, an unreadable APK — falls back to the full download.
 *
 * A downloaded APK lives in `filesDir/updates` and reaches the installer through the existing FileProvider, so nothing
 * needs an external-storage permission. Installing over itself needs `REQUEST_INSTALL_PACKAGES` and the phone's
 * "install unknown apps" permission, which is per-source and granted once (the About screen opens that page when it is
 * missing and offers Install again when the player comes back).
 */
private const val UPDATE_NOTIFICATION_ID = 7_431
private const val UPDATE_INSTALL_REQUEST = 7_432

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
        val patch = release.patchFor(AppVersion.NAME, AppVersion.BUILD.toLong())
        if (patch != null) {
            // A small download first; anything that goes wrong with it gets the whole APK instead.
            if (applyPatchDownload(release, patch, apk, onProgress)) return@withContext true
            problem = null
        }
        downloadFull(release, apk, onProgress)
    }

    private fun applyPatchDownload(release: UpdateRelease, patch: UpdatePatch, apk: File, onProgress: (Int) -> Unit): Boolean {
        val installed = installedApk() ?: return fail("The installed app cannot be read.")
        if (installed.length() <= 0) return fail("The installed app cannot be read.")
        val patchFile = File(dir, "update.patch.gz")
        patchFile.delete()
        if (dir.usableSpace < patch.sizeBytes + release.sizeBytes + 8L * 1024 * 1024) return fail("Not enough room for the update.")
        return try {
            downloadFile(release.patchUrl(patch), patchFile, patch.sizeBytes, patch.sha256) { percent -> onProgress(percent * 60 / 100) }
            val bytes = gunzipCapped(patchFile, MAX_PATCH_BYTES) ?: throw IOException("The patch is too large.")
            val plan = parseUpdatePatch(bytes, installed.length()) ?: throw IOException("The patch cannot be applied.")
            if (release.sizeBytes > 0 && plan.targetSize != release.sizeBytes) throw IOException("The patch builds the wrong size.")
            val part = File(dir, apk.name + ".part")
            part.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            val ok = FileOutputStream(part).use { out ->
                java.io.RandomAccessFile(installed, "r").use { source ->
                    plan.apply(
                        readInstalled = { offset, length ->
                            try {
                                ByteArray(length).also { source.seek(offset); source.readFully(it) }
                            } catch (e: Exception) {
                                null
                            }
                        },
                        write = { data, offset, length ->
                            out.write(data, offset, length)
                            digest.update(data, offset, length)
                        },
                        onProgress = { percent -> onProgress(60 + percent * 40 / 100) },
                    )
                }
            }
            if (!ok) throw IOException("The patch stopped early.")
            if (plan.targetSize != part.length()) throw IOException("The patch built the wrong size.")
            if (release.sha256.isNotEmpty()) {
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha.equals(release.sha256, ignoreCase = true)) throw IOException("The rebuilt update did not match its checksum.")
            }
            if (!part.renameTo(apk)) {
                part.copyTo(apk, overwrite = true)
                part.delete()
            }
            patchFile.delete()
            onProgress(100)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            patchFile.delete()
            throw e
        } catch (e: Exception) {
            patchFile.delete()
            problem = e.message?.takeIf { it.isNotBlank() } ?: "The small update could not be built."
            false
        }
    }

    private fun downloadFull(release: UpdateRelease, apk: File, onProgress: (Int) -> Unit): Boolean {
        if (dir.usableSpace < release.sizeBytes + 8L * 1024 * 1024) {
            problem = "Not enough room for the update."
            return false
        }
        val part = File(dir, apk.name + ".part")
        part.delete()
        return try {
            downloadFile(release.apkUrl, part, release.sizeBytes, release.sha256, onProgress)
            if (!part.renameTo(apk)) {
                part.copyTo(apk, overwrite = true)
                part.delete()
            }
            onProgress(100)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            problem = e.message?.takeIf { it.isNotBlank() } ?: "The download stopped."
            false
        }
    }

    /** Streams [url] into [dest], checking [size] and [sha256] when the manifest gave them. */
    private fun downloadFile(url: String, dest: File, size: Long, sha256: String, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PokeLife-Updater")
            setRequestProperty("Accept", "application/octet-stream")
            connect()
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("GitHub answered ${connection.responseCode}.")
            val total = if (size > 0) size else connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var reported = -1
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(dest).use { out ->
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
            if (size > 0 && dest.length() != size) throw IOException("The download came in short.")
            if (sha256.isNotEmpty()) {
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha.equals(sha256, ignoreCase = true)) throw IOException("The download did not match its checksum.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun gunzipCapped(file: File, cap: Int): ByteArray? {
        GZIPInputStream(BufferedInputStream(file.inputStream())).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > cap) return null
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }

    /** The APK Android installed, which a patch rebuilds from. */
    private fun installedApk(): File? = try {
        File(context.applicationInfo.sourceDir).takeIf { it.isFile }
    } catch (e: Exception) {
        null
    }

    private fun fail(text: String): Boolean {
        problem = text
        return false
    }

    override fun downloaded(release: UpdateRelease): Boolean {
        val apk = apkFile(release) ?: return false
        return apk.isFile && (release.sizeBytes <= 0 || apk.length() == release.sizeBytes)
    }

    override suspend fun install(release: UpdateRelease): Boolean {
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
        installRoomProblem(apk.length(), context.filesDir.usableSpace)?.let {
            problem = it
            return false
        }
        // A PackageInstaller session, not an ACTION_VIEW hand-off: the system reports back why an install stopped
        // (blocked, conflicting signature, cancelled), where the old hand-off showed "Preparing app" and vanished.
        val installer = context.packageManager.packageInstaller
        val result = UpdateInstallResult.begin()
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setSize(apk.length()) }
            sessionId = installer.createSession(params)
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("pokeplan.apk", 0, apk.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val status = PendingIntent.getBroadcast(
                        context, UPDATE_INSTALL_REQUEST,
                        Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(status.intentSender)
                }
            }
            val outcome = result.await()
            problem = outcome
            outcome == null
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { if (sessionId >= 0) installer.abandonSession(sessionId) }
            throw e
        } catch (e: Exception) {
            runCatching { if (sessionId >= 0) installer.abandonSession(sessionId) }
            problem = "The installer could not start: ${e.message ?: e::class.java.simpleName}."
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

    override fun notifyAvailable(release: UpdateRelease) {
        val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        val stamp = "${release.version}#${release.build}"
        if (prefs.getString("notified", null) == stamp) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.APP_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, UPDATE_NOTIFICATION_ID, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.ME.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, Channels.APP_UPDATES)
            .setSmallIcon(notificationIcon(context, "poke-ball"))
            .setContentTitle("PokéLife update ready")
            .setContentText("Version ${release.version} is available. Open Me, Settings, About to install it.")
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (manager.areNotificationsEnabled()) {
            notifySafely(context, UPDATE_NOTIFICATION_ID, notification)
            prefs.edit().putString("notified", stamp).apply()
        }
    }

    override fun openReleasePage(release: UpdateRelease) {
        val page = Intent(Intent.ACTION_VIEW, Uri.parse(updateReleasePage(release.tag)))
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
