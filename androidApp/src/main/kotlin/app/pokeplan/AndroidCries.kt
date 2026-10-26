package app.pokeplan

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import app.pokeplan.ui.CryPlayer
import app.pokeplan.ui.Sfx
import java.io.File

/**
 * Plays the real cries and sound effects (assets/cries and assets/sfx, packaged with the Compose resources) through
 * MediaPlayer: one cry at a time, and one effect at a time beside it.
 */
class AndroidCryPlayer(private val context: Context) : CryPlayer {
    private var player: MediaPlayer? = null
    private var effectPlayer: MediaPlayer? = null

    @Synchronized
    override fun play(speciesId: Int, volume: Float) {
        player?.release()
        player = start("cries/$speciesId.ogg", "cry-$speciesId.ogg", volume)
    }

    @Synchronized
    override fun effect(sound: Sfx, volume: Float) {
        effectPlayer?.release()
        effectPlayer = start("sfx/${sound.file}.ogg", "sfx-${sound.file}.ogg", volume)
    }

    private fun start(file: String, cacheName: String, volume: Float): MediaPlayer? {
        return try {
            val path = "composeResources/pokeplan.composeapp.generated.resources/files/$file"
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                try {
                    context.assets.openFd(path).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                } catch (e: java.io.FileNotFoundException) {
                    // Packaged compressed: copy it out once and play the copy.
                    val copy = File(context.cacheDir, cacheName)
                    if (!copy.exists()) context.assets.open(path).use { input -> copy.outputStream().use { input.copyTo(it) } }
                    setDataSource(copy.absolutePath)
                }
                setVolume(volume, volume)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            // A missing or unplayable sound is silent.
            null
        }
    }
}
