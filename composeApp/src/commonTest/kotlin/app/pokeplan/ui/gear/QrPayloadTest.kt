package app.pokeplan.ui.gear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrPayloadTest {
    @Test
    fun aPlainHttpsLinkIsAWebLinkWithNoWarning() {
        val r = QrPayload.read("https://example.com/menu")
        assertEquals(QrPayload.Kind.WEB, r.kind)
        assertEquals(emptyList(), r.warnings)
    }

    @Test
    fun linksThatHideWhereTheyGoAreFlagged() {
        assertTrue(QrPayload.read("http://example.com").warnings.any { "not encrypted" in it })
        assertTrue(QrPayload.read("https://paypal.com@evil.example/login").warnings.any { "@" in it })
        assertTrue(QrPayload.read("https://xn--pple-43d.com/").warnings.any { "look-alike" in it })
        assertTrue(QrPayload.read("https://192.168.1.5/admin").warnings.any { "number" in it })
        assertTrue(QrPayload.read("https://bit.ly/abc").warnings.any { "shortener" in it })
        assertTrue(QrPayload.read("https://example.com/" + "a".repeat(400)).warnings.any { "long" in it })
    }

    @Test
    fun otherPayloadsAreNamedAndRiskyOnesWarn() {
        assertEquals(QrPayload.Kind.WIFI, QrPayload.read("WIFI:T:WPA;S:Cafe;P:secret;;").kind)
        assertTrue(QrPayload.read("WIFI:T:nopass;S:Cafe;;").warnings.any { "no password" in it })
        assertEquals(QrPayload.Kind.CONTACT, QrPayload.read("BEGIN:VCARD\nFN:A").kind)
        assertEquals(QrPayload.Kind.PHONE, QrPayload.read("tel:+15551234").kind)
        assertTrue(QrPayload.read("smsto:12345:hi").warnings.isNotEmpty())
        assertEquals(QrPayload.Kind.TEXT, QrPayload.read("just words").kind)
        assertTrue(QrPayload.read("intent://scan/#Intent;scheme=zxing;end").warnings.isNotEmpty())
    }
}
