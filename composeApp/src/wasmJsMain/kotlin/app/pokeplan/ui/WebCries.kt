package app.pokeplan.ui

import pokeplan.composeapp.generated.resources.Res

// Browsers may refuse to play before the first tap; a refused play is simply silent.
@JsFun("(url) => { try { new Audio(url).play().catch(() => {}); } catch (e) {} }")
private external fun playAudio(url: String)

/** Cries in the browser, through an audio element. */
class WebCryPlayer : CryPlayer {
    override fun play(speciesId: Int) = playAudio(Res.getUri("files/cries/$speciesId.ogg"))
}
