package app.pokeplan.platform

import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import app.pokeplan.ui.ImageViewer
import java.io.File

/**
 * Full brightness for the big QR and a share sheet for its picture (MINT.md 4.2). Brightness is the activity window's own override (no permission, no system setting
 * touched) and the screen is kept on while it is raised; both are undone when [bright] is called with false. The picture is written to the cache's `exports/` folder
 * (the one the FileProvider already serves), the older files there are removed, and Android's share sheet is opened: the player picks where it goes.
 */
class AndroidImageViewer(private val activity: ComponentActivity) : ImageViewer {
    override fun bright(on: Boolean) {
        activity.runOnUiThread {
            val window = activity.window
            val attrs = window.attributes
            attrs.screenBrightness = if (on) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun share(png: ByteArray, name: String): String = try {
        val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, File(name).name.ifBlank { "receive-qr.png" }).apply { writeBytes(png) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(when (name.substringAfterLast('.').lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Share this QR code"))
        "Choose where to send it."
    } catch (e: Exception) {
        "Could not share the picture."
    }
}
