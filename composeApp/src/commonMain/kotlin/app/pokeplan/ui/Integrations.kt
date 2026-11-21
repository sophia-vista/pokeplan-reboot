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

/**
 * Lets the player pick one image (an account's receive-money QR, MINT.md 4.2) through the phone's own photo picker. The picture is shrunk here to at most 1080 px and
 * [app.pokeplan.mint.MintRules.QR_MAX_CHARS] of base64, and handed back as PNG bytes; it is not kept anywhere else. [done] gets the PNG, or null and a reason in words
 * (null and null when the player cancelled).
 */
interface ImageChooser {
    fun choose(done: (png: ByteArray?, problem: String?) -> Unit)
}

/**
 * Showing and sharing a picture that is already kept (an account's receive-money QR, MINT.md 4.2): [bright] raises the screen to full brightness and keeps it on while
 * the big QR is open (off again when it closes; it asks for no permission and changes no setting), and [share] hands the PNG to the phone's own share sheet as a file
 * named [name], so the player chooses where it goes. Nothing is sent anywhere by the app itself. Only the Android app provides one.
 */
interface ImageViewer {
    fun bright(on: Boolean)

    /** Returns a short note on what happened, or an error in words. */
    fun share(png: ByteArray, name: String): String
}

/** An app installed on the phone that can be opened from its launcher: the name the player sees and its package. */
data class InstalledApp(val label: String, val packageName: String)

/**
 * Starting an account's own wallet or bank app (MINT.md 4.3C). [installed] lists the apps with a launcher entry, by name, for the player to pick one in an account's
 * Edit form (read on the phone only, never sent anywhere); [label] is the name of one package, or null when it is not installed (then the Open button is hidden);
 * [open] starts it and nothing else. Only the Android app provides one.
 */
interface AppLauncher {
    fun installed(): List<InstalledApp>

    fun label(packageName: String): String?

    fun open(packageName: String): Boolean
}

/** What the phone lets the Huawei Health reader do right now, rechecked on every look (INTEGRATIONS_AND_PRIVACY.md 5.2.2). */
data class HuaweiReaderState(
    val installed: Boolean,
    val screenReading: Boolean,
    val notificationReading: Boolean,
    val walking: Boolean,
    /** Whether screen reading can be turned on for one read without Android's settings (WRITE_SECURE_SETTINGS granted over adb). */
    val canSwitchItself: Boolean = false,
)

/**
 * Reading Huawei Health on the phone, probe stage (INTEGRATIONS_AND_PRIVACY.md 5.2.2): an accessibility service for `com.huawei.health` only and a notification
 * listener that drops every other app's notifications. What it sees is held in memory and never saved. Only the Android app provides one.
 */
interface HuaweiReader {
    fun state(): HuaweiReaderState

    /** Everything captured since the last [clear]. */
    fun seen(): app.pokeplan.app.heal.HuaweiSeen

    fun clear()

    /**
     * Opens Huawei Health, walks it (INTEGRATIONS_AND_PRIVACY.md 5.2.2, rule 3) and comes back; screen reading is turned on first when [HuaweiReaderState.canSwitchItself]
     * and is off again afterwards. [known] is what HEAL already holds, so a read skips what has nothing new. [done] gets null, or what stopped it in words, on the main thread.
     */
    fun walk(known: app.pokeplan.app.heal.HuaweiWalk.Known, done: (String?) -> Unit)

    /** Turns screen reading off now (it also turns itself off after every read). */
    fun turnOff()

    /** Android's Accessibility settings, where the player turns screen reading on. */
    fun openScreenReadingSettings()

    /** Android's Notification access settings. */
    fun openNotificationSettings()

    /** This app's App info page, where Android 13 and later hide "Allow restricted settings" for a sideloaded app. */
    fun openAppInfo()
}
