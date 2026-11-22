package app.pokeplan.ui

import pokeplan.composeapp.generated.resources.Res

@JsFun("(url, volume, rate) => { try { const a = new Audio(url); a.volume = volume; a.preservesPitch = false; a.playbackRate = rate; a.play().catch(() => {}); } catch (e) {} }")
private external fun playAudioRate(url: String, volume: Double, rate: Double)

@JsFun("(url, volume) => { try { const a = new Audio(url); a.volume = volume; a.play().catch(() => {}); } catch (e) {} }")
private external fun playAudio(url: String, volume: Double)

class WebCryPlayer : CryPlayer {
    override fun play(speciesId: Int, volume: Float) = playAudio(Res.getUri("files/cries/$speciesId.ogg"), volume.toDouble())

    override fun playVoiced(speciesId: Int, volume: Float, pitch: Float, speed: Float) =
        playAudioRate(Res.getUri("files/cries/$speciesId.ogg"), volume.toDouble(), (pitch * speed).toDouble())

    override fun effect(sound: Sfx, volume: Float) = playAudio(Res.getUri("files/sfx/${sound.file}.ogg"), volume.toDouble())
}
