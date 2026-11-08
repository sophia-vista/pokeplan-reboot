package app.pokeplan.app

import app.pokeplan.heal.HealthKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationsTest {
    private val av = ProviderAvailability.AVAILABLE
    private val two = setOf(HealthKind.EXERCISE, HealthKind.SLEEP)

    @Test fun healthConnectIsConnectedOnlyWhenAndroidHasGrantedWhatWasChosen() {
        assertEquals(IntegrationStatus.UNSUPPORTED, Integrations.healthConnectStatus(ProviderAvailability.UNSUPPORTED, two, two))
        assertEquals(IntegrationStatus.TEMPORARILY_UNAVAILABLE, Integrations.healthConnectStatus(ProviderAvailability.UPDATE_REQUIRED, two, two))
        assertEquals(IntegrationStatus.AVAILABLE, Integrations.healthConnectStatus(av, emptySet(), two), "choosing nothing is not a connection, whatever Android still holds")
        assertEquals(IntegrationStatus.PERMISSION_REVOKED, Integrations.healthConnectStatus(av, two, emptySet()))
        assertEquals(IntegrationStatus.PARTIALLY_AUTHORIZED, Integrations.healthConnectStatus(av, two, setOf(HealthKind.SLEEP)))
        assertEquals(IntegrationStatus.CONNECTED, Integrations.healthConnectStatus(av, two, two + HealthKind.WEIGHT))
    }

    @Test fun noUnverifiedProviderIsEverShownAsAvailableOrConnected() {
        val unverified = Integrations.mint().filter { it.id != "statement-csv" } + Integrations.heal(av, two, two).filter { it.id == "huawei" }
        assertTrue(unverified.isNotEmpty())
        assertTrue(unverified.all { it.cls == IntegrationClass.UNSUPPORTED && it.status == IntegrationStatus.UNSUPPORTED })
        val names = Integrations.mint().first { it.id == "providers" }.name
        assertTrue(listOf("GCash", "Maya", "MariBank", "GoTyme", "UnionBank").all { it in names })
        assertEquals(IntegrationClass.USER_IMPORT, Integrations.mint().first { it.id == "statement-csv" }.cls)
    }
}
