package app.pokeplan.ui.gear

object QrPayload {
    enum class Kind(val label: String) {
        WEB("Web link"), WIFI("Wi-Fi network"), CONTACT("Contact card"), EMAIL("Email address"), PHONE("Phone number"), MESSAGE("Message"), TEXT("Plain text")
    }

    data class Reading(val kind: Kind, val warnings: List<String>)

    private val shorteners = setOf("bit.ly", "t.co", "tinyurl.com", "goo.gl", "is.gd", "ow.ly", "buff.ly", "rebrand.ly", "cutt.ly")

    fun read(text: String): Reading {
        val t = text.trim()
        val lower = t.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> Reading(Kind.WEB, webWarnings(t))
            lower.startsWith("wifi:") -> Reading(Kind.WIFI, listOf("Joining a network you do not know lets its owner see your traffic.") + if ("t:nopass" in lower || "t:;" in lower) listOf("This network has no password.") else emptyList())
            lower.startsWith("begin:vcard") || lower.startsWith("mecard:") -> Reading(Kind.CONTACT, emptyList())
            lower.startsWith("mailto:") -> Reading(Kind.EMAIL, emptyList())
            lower.startsWith("tel:") -> Reading(Kind.PHONE, emptyList())
            lower.startsWith("sms:") || lower.startsWith("smsto:") -> Reading(Kind.MESSAGE, listOf("This would prepare a text message, possibly to a paid number."))
            else -> Reading(Kind.TEXT, if (Regex("^[a-z][a-z0-9+.-]*:").containsMatchIn(lower)) listOf("This starts like a link to another app (\"${lower.substringBefore(':')}:\"). Be careful before opening it.") else emptyList())
        }
    }

    private fun webWarnings(url: String): List<String> {
        val warnings = mutableListOf<String>()
        val rest = url.substringAfter("://")
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (url.lowercase().startsWith("http://")) warnings += "This link is not encrypted (http, not https)."
        if ('@' in authority) warnings += "The address contains an @, which can hide where the link really goes."
        if (host.split('.').any { it.startsWith("xn--") }) warnings += "The address uses look-alike letters (punycode), a common trick."
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) warnings += "The address is a number, not a name."
        if (host in shorteners) warnings += "A link shortener hides the real address."
        if (url.length > 300) warnings += "This is a very long link."
        return warnings
    }
}
