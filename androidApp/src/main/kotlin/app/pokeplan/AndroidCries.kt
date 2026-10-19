package app.pokeplan

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import app.pokeplan.ui.CryPlayer
import java.io.File

/** Plays the real cries (assets/cries, packaged with the Compose resources) through MediaPlayer, one at a time. */
class AndroidCryPlayer(private val context: Context) : CryPlayer {
    private var player: MediaPlayer? = null

    @Synchronized
    override fun play(speciesId: Int) {
        try {
            player?.release()
            val path = "composeResources/pokeplan.composeapp.generated.resources/files/cries/$speciesId.ogg"
            val next = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                try {
                    context.assets.openFd(path).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                } catch (e: java.io.FileNotFoundException) {
                    // Packaged compressed: copy it out once and play the copy.
                    val copy = File(context.cacheDir, "cry-$speciesId.ogg")
                    if (!copy.exists()) context.assets.open(path).use { input -> copy.outputStream().use { input.copyTo(it) } }
                    setDataSource(copy.absolutePath)
                }
                setOnPreparedListener { it.start() }
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
                prepareAsync()
            }
            player = next
        } catch (e: Exception) {
            // A missing or unplayable cry is silent.
        }
    }
}
