package app.pokeplan.core.js

import app.pokeplan.core.reward.RewardJson
import app.pokeplan.core.reward.RewardRules

/**
 * What the server imports. Every function takes and returns JSON strings:
 *
 * ```js
 * import { RewardApi } from "./pokeplan-pokemon-core.mjs";
 * const out = RewardApi.getInstance().effort(stateJson, eventJson, seed);
 * ```
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object RewardApi {
    /** The rules version the server records on every ledger row (RewardRules.VERSION). */
    fun rulesVersion(): Int = RewardRules.VERSION

    fun emptyState(): String = RewardJson.emptyState()

    fun effort(stateJson: String, eventJson: String, seed: Int): String = RewardJson.effort(stateJson, eventJson, seed)

    fun achievement(stateJson: String, eventJson: String, seed: Int): String = RewardJson.achievement(stateJson, eventJson, seed)

    fun care(stateJson: String, eventJson: String, seed: Int): String = RewardJson.care(stateJson, eventJson, seed)
}
