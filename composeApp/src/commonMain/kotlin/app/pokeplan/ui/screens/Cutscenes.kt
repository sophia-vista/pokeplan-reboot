package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.app.GameStore
import app.pokeplan.ui.theme.Spacing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.world.bonusText
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.Stage
import app.pokeplan.ui.SceneStage
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.Acquisition
import app.pokeplan.ui.acquisitionOf
import app.pokeplan.app.BuddyScene
import androidx.compose.foundation.layout.offset
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.world.StoryPlayer
import app.pokeplan.app.world.worldMoment
import app.pokeplan.app.world.worldMomentSeen

sealed interface Cutscene {
    data class Hatch(val pokemon: OwnedPokemon, val fromTask: Boolean, val how: app.pokeplan.app.Origin.How? = null) : Cutscene

    data class Evolve(val before: OwnedPokemon, val after: OwnedPokemon, val option: app.pokeplan.core.pokemon.EvolutionOption? = null) : Cutscene

    data class World(val moment: app.pokeplan.world.WorldMoment) : Cutscene

    /** An optional Mythical follow-up, which belongs to no region: the Story archive plays it from here. */
    data class Mythical(val chapter: Int) : Cutscene
}

private val FlashWhite = Color.White
private val FlashGold = Color(0xFFFFE27A)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CutsceneHost(model: AppModel) {
    val waiting = model.data.rewardLog.filter { !it.seen && it.source != GameStore.WORLD_SOURCE }
        .flatMap { it.rewards.filterIsInstance<Reward.Hatched>() }
        .firstOrNull { it.pokemon.id !in model.playedHatches }
    val idle = model.cutscene == null
    LaunchedEffect(waiting?.pokemon?.id, idle) {
        if (idle && waiting != null) {
            model.playedHatches += waiting.pokemon.id
            model.cutscene = Cutscene.Hatch(waiting.pokemon, waiting.fromTask, model.data.origins[waiting.pokemon.id]?.how)
        }
    }
    LaunchedEffect(model.data.world, idle) {
        if (model.cutscene == null) model.store.worldMoment()?.let { m -> model.store.worldMomentSeen(m); model.cutscene = Cutscene.World(m) }
    }
    val scene = model.cutscene ?: return
    val close = { model.cutscene = null }
    BackHandler(enabled = true, onBack = close)
    when (scene) {
        is Cutscene.Hatch -> if (acquisitionOf(scene.how) == Acquisition.EGG) {
            val visitor = model.data.origins[scene.pokemon.id]?.visitor?.takeIf { scene.how == app.pokeplan.app.Origin.How.BEFRIENDED }
            var left by remember(scene.pokemon.id) { mutableStateOf(visitor == null) }
            if (left) HatchScene(model, scene, close) else VisitorLeavesEggScene(model, visitor!!) { left = true }
        } else ArrivalScene(model, scene, acquisitionOf(scene.how), close)
        is Cutscene.Evolve -> EvolutionScene(model, scene, close)
        is Cutscene.World -> WorldMomentScene(model, scene.moment, close)
        is Cutscene.Mythical -> {
            val episode = app.pokeplan.world.story.StoryCatalog.mythical(scene.chapter)
            if (episode != null) StoryPlayer(model, episode, close, fromStart = episode.key in model.data.world.episodesDone)
            else LaunchedEffect(scene.chapter) { close() }
        }
    }
}

@Composable
private fun SceneFrame(onTap: () -> Unit, flash: Float, flashColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.safeDrawingPadding().widthIn(max = 480.dp).fillMaxWidth().padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        if (flash > 0f) Box(Modifier.fillMaxSize().background(flashColor.copy(alpha = flash)))
    }
}

@Composable
private fun SceneText(text: String, detail: String?, done: Boolean, skip: () -> Unit, close: () -> Unit, stop: (() -> Unit)? = null) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(text, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End)) {
                if (done) PixelButton("OK", close, color = Pixel.Green, compact = true)
                else {
                    stop?.let { PixelButton("Stop", it, color = Pixel.InkSoft, compact = true) }
                    PixelButton("Skip", skip, color = Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

@Composable
private fun HatchScene(model: AppModel, scene: Cutscene.Hatch, close: () -> Unit) {
    val p = scene.pokemon
    val still = model.data.settings.reduceMotion
    var hatched by remember(p.id) { mutableStateOf(false) }
    val tilt = remember(p.id) { Animatable(0f) }
    val flash = remember(p.id) { Animatable(0f) }
    var pose by remember(p.id) { mutableStateOf(!still) }
    LaunchedEffect(p.id, hatched) {
        if (hatched) return@LaunchedEffect
        if (!still) {
            delay(700)
            for (burst in 0 until 3) {
                model.haptic()
                model.effect(Sfx.JUMP)
                val swing = 7f + burst * 5f
                repeat(2 + burst) {
                    tilt.animateTo(swing, tween(80))
                    tilt.animateTo(-swing, tween(80))
                }
                tilt.animateTo(0f, tween(80))
                delay(700L - burst * 200)
            }
            flash.animateTo(1f, tween(300))
        }
        hatched = true
    }
    LaunchedEffect(p.id, hatched) {
        if (!hatched) return@LaunchedEffect
        tilt.snapTo(0f)
        model.effect(Sfx.RECRUIT)
        model.cry(p)
        model.haptic()
        if (still) {
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        flash.snapTo(1f)
        flash.animateTo(0f, tween(700))
        if (p.isShiny) {
            flash.animateTo(0.6f, tween(200))
            flash.animateTo(0f, tween(500))
        }
    }

    val name = p.nickname
    val egg = when {
        scene.fromTask -> "Task Egg"
        scene.how == app.pokeplan.app.Origin.How.BEFRIENDED -> "Egg a visitor left"
        else -> "Egg"
    }
    SceneFrame({ hatched = true }, flash.value, if (p.isShiny) FlashGold else FlashWhite) {
        Stage("bw08", 260.dp) {
            if (!hatched) {
                ItemIcon(
                    "mystery-egg",
                    Modifier.align(Alignment.Center).graphicsLayer {
                        rotationZ = tilt.value
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                    },
                    size = 144.dp, description = "An egg",
                )
            } else {
                AnimatedSprite(
                    p.form.id, p.isShiny, name, Modifier.align(Alignment.TopCenter).padding(top = Spacing.XL), size = 176.dp, animate = !still,
                    action = if (pose) SpriteAction.POSE else SpriteAction.IDLE, onActionDone = { pose = false },
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.M), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    p.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            }
        }
        SceneText(
            text = when {
                !hatched -> "Oh?"
                p.isShiny -> "A shiny $name hatched from the $egg!"
                else -> "$name hatched from the $egg!"
            },
            detail = if (hatched) p.species.genus.takeIf { it.isNotEmpty() }?.let { "$name, the $it." } else null,
            done = hatched, skip = { hatched = true }, close = close,
        )
    }
}

@Composable
private fun VisitorLeavesEggScene(model: AppModel, visitor: app.pokeplan.app.VisitorOrigin, next: () -> Unit) {
    val still = model.data.settings.reduceMotion
    val key = visitor.visitorId
    var gone by remember(key) { mutableStateOf(false) }
    val walk = remember(key) { Animatable(0f) }
    val eggIn = remember(key) { Animatable(0f) }
    val name = app.pokeplan.core.dex.Dex.species(visitor.speciesId).name
    LaunchedEffect(key, gone) {
        if (gone) { walk.snapTo(1f); eggIn.snapTo(1f); return@LaunchedEffect }
        if (still) { gone = true; return@LaunchedEffect }
        model.cry(visitor.speciesId)
        delay(1400)
        eggIn.animateTo(1f, tween(500))
        model.effect(Sfx.SELECT)
        walk.animateTo(1f, tween(1700))
        gone = true
    }
    SceneFrame({ gone = true }, 0f, FlashWhite) {
        SceneStage(BuddyScene.CARE, 260.dp) {
            Box(Modifier.align(Alignment.Center).graphicsLayer { alpha = eggIn.value }) {
                ItemIcon("mystery-egg", size = Sizes.SpriteL, description = "An egg in the grass")
            }
            if (walk.value < 1f) {
                AnimatedSprite(
                    app.pokeplan.core.dex.Dex.defaultForm(visitor.speciesId).id, false, name,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.XL).offset(x = (-walk.value * 300).dp),
                    size = 160.dp, animate = !still,
                    action = if (walk.value > 0f) SpriteAction.WALK_SIDE else SpriteAction.IDLE, mirror = walk.value > 0f,
                )
            }
        }
        SceneText(
            text = if (gone) "$name left an egg behind in the meadow." else "$name has grown fond of you...",
            detail = if (gone) "Something stirs inside it." else null,
            done = gone, skip = { gone = true }, close = next,
        )
    }
}

@Composable
private fun ArrivalScene(model: AppModel, scene: Cutscene.Hatch, kind: Acquisition, close: () -> Unit) {
    val p = scene.pokemon
    val still = model.data.settings.reduceMotion
    var arrived by remember(p.id) { mutableStateOf(false) }
    val travel = remember(p.id) { Animatable(0f) }
    val flash = remember(p.id) { Animatable(0f) }
    val glint = remember(p.id) { Animatable(0f) }
    LaunchedEffect(p.id, arrived) {
        if (arrived) return@LaunchedEffect
        if (!still) {
            when (kind) {
                Acquisition.ARRIVAL -> { delay(500); travel.animateTo(1f, tween(1800)) }
                Acquisition.FIELD -> { delay(400); travel.animateTo(1f, tween(900)) }
                Acquisition.LEDGER -> {
                    delay(300)
                    repeat(3) { model.effect(Sfx.SELECT); glint.animateTo(1f, tween(260)); glint.animateTo(0.25f, tween(260)) }
                    glint.animateTo(1f, tween(300))
                    flash.animateTo(0.7f, tween(200))
                    travel.snapTo(0f)
                    flash.animateTo(0f, tween(250))
                    travel.animateTo(1f, tween(900))
                }
                Acquisition.HABITAT -> { delay(400); travel.animateTo(1f, tween(1000)) }
                Acquisition.POSTCARD -> { delay(400); travel.animateTo(1f, tween(1100)) }
                Acquisition.SAFARI -> { delay(500); travel.animateTo(1f, tween(1200)) }
                Acquisition.EGG -> {}
            }
        }
        arrived = true
    }
    LaunchedEffect(p.id, arrived) {
        if (!arrived) return@LaunchedEffect
        travel.snapTo(1f)
        glint.snapTo(0f)
        model.effect(Sfx.RECRUIT)
        model.cry(p)
        model.haptic()
        if (still) return@LaunchedEffect
        if (p.isShiny) {
            flash.snapTo(0.6f)
            flash.animateTo(0f, tween(700))
        }
    }

    val name = p.nickname
    val shiny = p.isShiny
    val scenery = when (kind) { Acquisition.ARRIVAL -> BuddyScene.CARE; Acquisition.FIELD -> BuddyScene.HEAL; Acquisition.HABITAT -> BuddyScene.BASE; Acquisition.POSTCARD, Acquisition.SAFARI -> BuddyScene.LINK; else -> BuddyScene.MINT }
    SceneFrame({ arrived = true }, flash.value, if (shiny) FlashGold else FlashWhite) {
        SceneStage(scenery, 260.dp) {
            val t = travel.value
            val enter = when (kind) {
                Acquisition.ARRIVAL -> Modifier.offset(x = (-(1f - t) * 260).dp)
                Acquisition.FIELD -> Modifier.offset(x = ((1f - t) * 260).dp)
                Acquisition.HABITAT -> Modifier.offset(y = (-(1f - t) * 200).dp)
                Acquisition.POSTCARD -> Modifier.offset(x = ((1f - t) * 260).dp, y = (-(1f - t) * 60).dp)
                Acquisition.SAFARI -> Modifier.offset(x = (-(1f - t) * 260).dp)
                else -> Modifier.offset(y = ((1f - t) * 200).dp)
            }
            if (kind == Acquisition.LEDGER && !arrived) {
                ItemIcon("star-piece", Modifier.align(Alignment.Center).graphicsLayer { alpha = 0.3f + 0.7f * glint.value; scaleX = 1f + 0.25f * glint.value; scaleY = 1f + 0.25f * glint.value }, size = Sizes.SpriteM, description = null)
            }
            if (arrived || kind != Acquisition.LEDGER || t > 0f) {
                val walking = !arrived && t < 1f && kind != Acquisition.LEDGER
                AnimatedSprite(
                    p.form.id, shiny, name, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.XL).then(enter), size = 176.dp, animate = !still,
                    action = if (walking) SpriteAction.WALK_SIDE else SpriteAction.IDLE, mirror = walking && kind == Acquisition.FIELD,
                )
            }
            if (arrived) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.S), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    p.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            }
        }
        val prefix = if (shiny) "shiny " else ""
        SceneText(
            text = when {
                !arrived -> when (kind) { Acquisition.ARRIVAL -> "Oh? Someone is coming over the meadow..."; Acquisition.FIELD -> "Oh? Something is running along the route..."; Acquisition.HABITAT -> "Oh? Something has settled into the habitat..."; Acquisition.POSTCARD -> "Oh? A postcard has arrived..."; Acquisition.SAFARI -> "Oh? Something rustles in the tall grass..."; else -> "Oh? Ledger Research has turned something up..." }
                else -> when (kind) {
                    Acquisition.ARRIVAL -> "A ${prefix}new friend wandered over: $name!"
                    Acquisition.FIELD -> "Field Research found a $prefix$name!"
                    Acquisition.HABITAT -> "Habitat Discovery found a $prefix$name!"
                    Acquisition.POSTCARD -> "Postcard Trail delivered a $prefix$name!"
                    Acquisition.SAFARI -> "A Friend Safari had a $prefix$name!"
                    else -> "Ledger Research catalogued a $prefix$name!"
                }
            },
            detail = if (arrived) p.species.genus.takeIf { it.isNotEmpty() }?.let { "$name, the $it." } else null,
            done = arrived, skip = { arrived = true }, close = close,
        )
    }
}

@Composable
private fun EvolutionScene(model: AppModel, scene: Cutscene.Evolve, close: () -> Unit) {
    val before = scene.before
    val after = scene.after
    val still = model.data.settings.reduceMotion
    var done by remember(after) { mutableStateOf(false) }
    var stopped by remember(after) { mutableStateOf(false) }
    var made by remember(after) { mutableStateOf(scene.option == null) }
    var showNew by remember(after) { mutableStateOf(false) }
    val white = remember(after) { Animatable(0f) }
    val flash = remember(after) { Animatable(0f) }
    var spin by remember(after) { mutableStateOf(!still) }
    LaunchedEffect(after, done, stopped) {
        if (done || stopped) return@LaunchedEffect
        if (!still) {
            model.effect(Sfx.EVOLUTION)
            delay(1_000)
            white.animateTo(1f, tween(600))
            var gap = 450L
            while (gap > 50) {
                delay(gap)
                showNew = !showNew
                gap = gap * 4 / 5
            }
            showNew = true
            flash.animateTo(1f, tween(250))
        }
        done = true
    }
    LaunchedEffect(after, done, stopped) {
        if (!done || stopped) return@LaunchedEffect
        if (!made) { scene.option?.let { model.store.evolve(before.id, it) }; made = true }
        showNew = true
        white.snapTo(0f)
        model.cry(after)
        model.haptic()
        if (still) {
            flash.snapTo(0f)
        } else {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(700))
        }
        delay(1_200)
        model.effect(Sfx.UNLOCK)
    }

    if (stopped) {
        SceneFrame(close, 0f, FlashWhite) {
            Stage("bw08", 260.dp) { Sprite(before.form.id, before.isShiny, before.nickname, Modifier.align(Alignment.Center), size = 160.dp) }
            SceneText(text = "Huh? ${before.nickname} stopped evolving!", detail = null, done = true, skip = close, close = close)
        }
        return
    }
    SceneFrame({ done = true }, flash.value, FlashWhite) {
        Stage("bw08", 260.dp) {
            if (done) {
                AnimatedSprite(
                    after.form.id, after.isShiny, after.nickname, Modifier.align(Alignment.TopCenter).padding(top = Spacing.XL), size = 176.dp, animate = !still,
                    action = if (spin) SpriteAction.ROTATE else SpriteAction.IDLE, onActionDone = { spin = false },
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.M), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    after.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            } else {
                val shown = if (showNew) after else before
                Box(Modifier.align(Alignment.Center)) {
                    Sprite(shown.form.id, shown.isShiny, before.nickname, Modifier.alpha(1f - white.value), size = 160.dp)
                    Sprite(shown.form.id, shown.isShiny, before.nickname, Modifier.alpha(white.value), size = 160.dp, silhouette = Color.White)
                }
            }
        }
        SceneText(
            text = if (done) "Congratulations! Your ${before.nickname} evolved into ${after.species.name}!" else "What? ${before.nickname} is evolving!",
            detail = null, done = done, skip = { done = true }, close = close,
            stop = if (scene.option != null && !made) ({ stopped = true }) else null,
        )
    }
}

@Composable
private fun WorldMomentScene(model: AppModel, moment: app.pokeplan.world.WorldMoment, close: () -> Unit) {
    if (moment == app.pokeplan.world.WorldMoment.Welcome) {
        val intro = app.pokeplan.world.story.StoryCatalog.intro()
        StoryPlayer(model, intro, close, fromStart = intro.key in model.data.world.episodesDone); return
    }
    if (moment is app.pokeplan.world.WorldMoment.ChapterOpened) {
        val episode = app.pokeplan.world.story.StoryCatalog.episodeFor(moment.layer, moment.chapter.number)
        if (episode != null) { StoryPlayer(model, episode, close, fromStart = episode.key in model.data.world.episodesDone); return }
    }
    if (moment is app.pokeplan.world.WorldMoment.SpecialEpisode) {
        val episode = app.pokeplan.world.story.StoryCatalog.episodeFor(moment.layer, moment.chapter)
        if (episode != null) StoryPlayer(model, episode, close, fromStart = episode.key in model.data.world.episodesDone) else LaunchedEffect(moment) { close() }
        return
    }
    val still = model.data.settings.reduceMotion
    var done by remember(moment) { mutableStateOf(still) }
    val flash = remember(moment) { Animatable(0f) }
    LaunchedEffect(moment) {
        model.haptic()
        if (still) { model.effect(Sfx.LEVEL_UP); return@LaunchedEffect }
        flash.snapTo(1f)
        model.effect(Sfx.LEVEL_UP)
        flash.animateTo(0f, tween(900))
        delay(900)
        done = true
    }
    SceneFrame({ done = true }, flash.value, FlashGold) {
        when (moment) {
            app.pokeplan.world.WorldMoment.Welcome, is app.pokeplan.world.WorldMoment.SpecialEpisode -> {}
            is app.pokeplan.world.WorldMoment.RankUp -> {
                val rank = moment.rank
                Stage("bw08", 220.dp) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("Exploration rank", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                        Text(rank.label, style = MaterialTheme.typography.displaySmall, color = FlashGold)
                        ItemIcon(rank.badge, size = Sizes.SpriteL, description = "${rank.label} rank badge")
                    }
                }
                val opens = app.pokeplan.world.WorldMoments.rankOpened(rank)
                SceneText(
                    text = "Your team is now ${rank.label} rank!",
                    detail = if (opens.isEmpty()) null else "This opens ${opens.joinToString("; ")}.",
                    done = done, skip = { done = true }, close = close,
                )
            }
            is app.pokeplan.world.WorldMoment.LegendJoined -> {
                val q = moment.quest
                val form = app.pokeplan.core.dex.Dex.defaultForm(q.speciesId)
                LaunchedEffect(q.id) { model.cry(q.speciesId) }
                Stage("bw08", 260.dp) {
                    AnimatedSprite(form.id, false, q.name, Modifier.align(Alignment.Center), size = 176.dp, animate = !still)
                }
                SceneText(
                    text = if (q.afterGuardian) "${q.name} joined you! ${q.layer.label}'s Guardian has answered." else "${q.name} joined you as a Guardian of ${q.layer.label}!",
                    detail = "It gives: ${q.bonusText()}.", done = done, skip = { done = true }, close = close,
                )
            }
            is app.pokeplan.world.WorldMoment.ChapterOpened -> {
                val c = moment.chapter
                Stage("bw08", 220.dp) {
                    Column(Modifier.align(Alignment.Center).padding(horizontal = Spacing.M), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("${moment.layer.label}, chapter ${c.number}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                        Text(c.title, style = MaterialTheme.typography.headlineMedium, color = FlashGold, textAlign = TextAlign.Center)
                        ItemIcon("town-map", size = Spacing.Target, description = null)
                    }
                }
                SceneText(text = c.summary, detail = if (c.unlocks.isBlank()) null else "New: ${c.unlocks}.", done = done, skip = { done = true }, close = close)
            }
        }
    }
}
