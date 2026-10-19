package app.pokeplan.ui

import pokeplan.composeapp.generated.resources.Res

// Browsers may refuse to play before the first tap; a refused play is simply silent.
@JsFun("(url, volume) => { try { const a = new Audio(url); a.volume = volume; a.play().catch(() => {}); } catch (e) {} }")
private external fun playAudio(url: String, volume: Double)

/** Cries in the browser, through an audio element. */
class WebCryPlayer : CryPlayer {
    override fun play(speciesId: Int, volume: Float) = playAudio(Res.getUri("files/cries/$speciesId.ogg"), volume.toDouble())
}
