package app.pokeplan.ui

import app.pokeplan.app.ProviderAvailability
import app.pokeplan.heal.HealthKind
import app.pokeplan.heal.ProviderRead

/**
 * Health Connect, read-only (INTEGRATIONS_AND_PRIVACY.md section 5.1). Only the Android app provides one; everywhere else the model holds null and HEAL says Health
 * Connect is not available. Every call is on the player's tap: nothing is read in the background and no permission is asked before the player chooses what to read.
 * Answers come back on the main thread.
 */
interface HealthSource {
    /** What the phone says about Health Connect right now. */
    fun availability(): ProviderAvailability

    /** The record types Android has allowed us to read now (rechecked every time, never remembered). */
    fun granted(done: (Set<HealthKind>) -> Unit)

    /** Opens Android's own permission screen for [kinds]; [done] gets the ones now allowed. Android may show it only a limited number of times. */
    fun request(kinds: Set<HealthKind>, done: (Set<HealthKind>) -> Unit)

    /** Reads the last [days] days (at most 30) of [kinds]. [done] gets the records, or null and a reason in words. Read-only. */
    fun read(kinds: Set<HealthKind>, days: Int, done: (ProviderRead?, String?) -> Unit)

    /** Takes back every permission this app has in Health Connect. Imported records stay. */
    fun revoke(done: (Boolean) -> Unit)

    /** Opens Health Connect's own settings, where the player sees and manages which apps may read and write. */
    fun openSettings()
}

/** Lets the player choose one text file (a statement CSV); the file is read here and not kept. [done] gets its name and text, or null and a reason in words. */
interface TextFileChooser {
    fun choose(done: (name: String?, text: String?, problem: String?) -> Unit)
}
