package app.pokeplan

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import app.pokeplan.ui.CryPlayer
import app.pokeplan.ui.Sfx
import java.io.File

/**
 * Plays the real cries and sound effects (assets/cries and assets/sfx, packaged with the Compose resources): one cry at a
 * time through MediaPlayer, the short effects through a SoundPool (loaded on first use, several at once, so a run of taps
 * or the letter scroller never cuts itself off), and the one long jingle through MediaPlayer.
 */
class AndroidCryPlayer(private val context: Context) : CryPlayer {
    private var player: MediaPlayer? = null
    private var effectPlayer: MediaPlayer? = null
    private val ids = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()
    private val waiting = mutableMapOf<Int, Float>()
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
        .also { pool ->
            pool.setOnLoadCompleteListener { _, id, status ->
                synchronized(this) {
                    if (status == 0) ready += id
                    waiting.remove(id)?.let { volume -> if (status == 0) pool.play(id, volume, volume, 1, 0, 1f) }
                }
            }
        }

    @Synchronized
    override fun play(speciesId: Int, volume: Float) {
        player?.release()
        player = start("cries/$speciesId.ogg", "cry-$speciesId.ogg", volume)
    }

    @Synchronized
    override fun playVoiced(speciesId: Int, volume: Float, pitch: Float, speed: Float) {
        player?.release()
        player = start("cries/$speciesId.ogg", "cry-$speciesId.ogg", volume, pitch, speed)
    }

    @Synchronized
    override fun effect(sound: Sfx, volume: Float) {
        if (sound.streams) {
            effectPlayer?.release()
            effectPlayer = start("sfx/${sound.file}.ogg", "sfx-${sound.file}.ogg", volume)
            return
        }
        val id = ids.getOrPut(sound.file) { load(sound.file) }
        if (id == 0) return
        if (id in ready) pool.play(id, volume, volume, 1, 0, 1f) else waiting[id] = volume
    }

    /** Starts loading a short effect; 0 when it cannot be read (it then stays silent). */
    private fun load(file: String): Int = try {
        val path = "composeResources/pokeplan.composeapp.generated.resources/files/sfx/$file.ogg"
        try {
            context.assets.openFd(path).use { pool.load(it, 1) }
        } catch (e: java.io.FileNotFoundException) {
            // Packaged compressed: copy it out once and load the copy.
            val copy = File(context.cacheDir, "sfx-$file.ogg")
            if (!copy.exists()) context.assets.open(path).use { input -> copy.outputStream().use { input.copyTo(it) } }
            pool.load(copy.absolutePath, 1)
        }
    } catch (e: Exception) {
        0
    }

    private fun start(file: String, cacheName: String, volume: Float, pitch: Float = 1f, speed: Float = 1f): MediaPlayer? {
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
                // A voiced cry (CryVoice): pitch and speed apart. Setting them on a prepared player may start it, which start() then repeats harmlessly.
                setOnPreparedListener { p ->
                    if (pitch != 1f || speed != 1f) runCatching { p.playbackParams = android.media.PlaybackParams().setPitch(pitch).setSpeed(speed) }
                    p.start()
                }
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
