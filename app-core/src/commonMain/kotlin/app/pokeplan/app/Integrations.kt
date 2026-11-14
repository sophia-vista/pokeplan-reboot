package app.pokeplan.app

import app.pokeplan.heal.HealthKind

/** INTEGRATIONS_AND_PRIVACY.md section 2: every capability is labelled with exactly one of these. */
enum class IntegrationClass(val label: String) {
    OFFICIAL_API("Official provider API"),
    ANDROID_PLATFORM("Android platform API"),
    USER_IMPORT("User-assisted import"),
    UNSUPPORTED("Unsupported or unverified"),
}

/** The status values of section 2. A provider is never shown as connected because an SDK is installed or an account exists on the phone. */
enum class IntegrationStatus(val label: String) {
    NOT_CONFIGURED("Not set up"),
    AVAILABLE("Available"),
    PERMISSION_REQUIRED("Needs your permission"),
    CONNECTED("Connected"),
    PARTIALLY_AUTHORIZED("Partly allowed"),
    TEMPORARILY_UNAVAILABLE("Not available right now"),
    PERMISSION_REVOKED("Permission taken away"),
    DISCONNECTED("Disconnected"),
    UNSUPPORTED("Not supported"),
    REAUTHORIZATION_REQUIRED("Needs signing in again"),
}

/** Whether the phone can talk to Health Connect at all, found out at run time (never assumed). */
enum class ProviderAvailability { AVAILABLE, UPDATE_REQUIRED, UNSUPPORTED }

/** One row of a module's Integrations page (section 14): what it does, what class it is, what it reads, and what it cannot do. */
data class IntegrationInfo(
    val id: String,
    val name: String,
    val cls: IntegrationClass,
    val status: IntegrationStatus,
    val does: String,
    val data: String,
    val limits: String,
)

/**
 * The integrations HEAL and MINT offer, and their honest status. Nothing here connects to anything: it describes what exists. The rule behind every line is
 * INTEGRATIONS_AND_PRIVACY.md section 1, item 6 and section 17, item 8: **an integration is not advertised until it has been verified end to end**, so a
 * provider nobody has verified says so, in the status and in the words.
 */
object Integrations {
    // --- HEAL -------------------------------------------------------------------------------------------------------------

    /** Health Connect's status from what the phone says now, which kinds the player chose, and which of them Android has granted. */
    fun healthConnectStatus(availability: ProviderAvailability, chosen: Set<HealthKind>, granted: Set<HealthKind>): IntegrationStatus = when {
        availability == ProviderAvailability.UNSUPPORTED -> IntegrationStatus.UNSUPPORTED
        availability == ProviderAvailability.UPDATE_REQUIRED -> IntegrationStatus.TEMPORARILY_UNAVAILABLE
        chosen.isEmpty() -> IntegrationStatus.AVAILABLE
        granted.isEmpty() -> IntegrationStatus.PERMISSION_REVOKED
        !granted.containsAll(chosen) -> IntegrationStatus.PARTIALLY_AUTHORIZED
        else -> IntegrationStatus.CONNECTED
    }

    fun heal(availability: ProviderAvailability, chosen: Set<HealthKind>, granted: Set<HealthKind>): List<IntegrationInfo> = listOf(
        IntegrationInfo(
            "health-connect", "Health Connect", IntegrationClass.ANDROID_PLATFORM, healthConnectStatus(availability, chosen, granted),
            "Reads records other apps wrote into Health Connect, only when you tap Read now. Never writes, and nothing runs in the background.",
            HealthKind.entries.joinToString("; ") { "${it.label} (${it.reads})" },
            "Only the 30 days before you first allow access can be read. A watch or app appears here only if it writes into Health Connect. Imported records never earn anything.",
        ),
        IntegrationInfo(
            "huawei", "Huawei Watch Fit 4 Pro", IntegrationClass.UNSUPPORTED, IntegrationStatus.UNSUPPORTED,
            "Not verified, and there is no direct link to the watch. Huawei Health is reported not to write into Health Connect itself. A test on this phone reads Huawei Health's own pages and notification when you allow it, and saves nothing yet.",
            "Whatever Huawei Health or another app writes into Health Connect is read like any other app's, and each record shows the app it came from. The test shows the text of Huawei Health's pages and its step count, in memory only.",
            "Huawei's own Health Kit needs a developer registration and Huawei's approval before any app can use it, and the watch's steps, workouts, sleep and weight have not been tested here. A separate app you install yourself may copy Huawei data into Health Connect; Heal does not depend on one and has not checked any.",
        ),
    )

    // --- MINT -------------------------------------------------------------------------------------------------------------

    private const val NO_ACCESS = "No verified way to read a personal account. Import a statement file instead."

    fun mint(): List<IntegrationInfo> = listOf(
        IntegrationInfo(
            "statement-csv", "Statement file (CSV)", IntegrationClass.USER_IMPORT, IntegrationStatus.AVAILABLE,
            "You choose a file, check a preview, and keep the rows you want. Importing it again adds nothing twice.",
            "Date, description and amount (or a debit and a credit column) of each row, kept as a record of the account you pick.",
            "No credentials and no connection: the file is read once on this phone. Amounts are never rounded and never converted.",
        ),
        IntegrationInfo(
            "notification-parsing", "Reading payment notifications", IntegrationClass.UNSUPPORTED, IntegrationStatus.UNSUPPORTED,
            "Not built. Android gives this only as access to every notification, which is more than Mint should ever ask for.",
            "None.", "It needs its own privacy review first. Mint never reads your texts or notifications.",
        ),
        IntegrationInfo(
            "providers", "GCash, Maya, MariBank, GoTyme, UnionBank", IntegrationClass.UNSUPPORTED, IntegrationStatus.UNSUPPORTED, NO_ACCESS, "None.",
            "Not verified: UnionBank has a developer portal for businesses, and the BSP has an Open Finance framework, but neither is confirmed to give your own transactions to an app like this. Nothing here logs in, scrapes or asks for a password, PIN or code.",
        ),
    )
}
