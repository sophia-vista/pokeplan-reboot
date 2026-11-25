package app.pokeplan.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneDriveTest {
    @Test
    fun theSignInUrlCarriesTheReadOnlyScopeAndPkceChallenge() {
        val url = OneDrive.authorizeUrl("client-1", "challenge", "state-1")
        assertTrue(url.startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?"))
        assertTrue("client_id=client-1" in url)
        assertTrue("redirect_uri=pokeplan-onedrive%3A%2F%2Fauth" in url, "the registered redirect URI")
        assertTrue("scope=Files.Read%20offline_access" in url)
        assertTrue("code_challenge=challenge" in url && "code_challenge_method=S256" in url)
        assertTrue("state=state-1" in url && "response_type=code" in url)
        val body = OneDrive.tokenBody("client-1", "the-code", "verifier")
        assertTrue("grant_type=authorization_code" in body && "code=the-code" in body && "code_verifier=verifier" in body)
        assertTrue("refresh_token" in OneDrive.refreshBody("client-1", "refresh-1"))
    }

    @Test
    fun theRedirectIsOnlyTakenForOurOwnSchemeAndItsCodeAndState() {
        assertEquals("abc" to "xyz", OneDrive.redirect("pokeplan-onedrive://auth?code=abc&state=xyz"))
        assertNull(OneDrive.redirect("pokeplan-onedrive://auth?error=access_denied"))
        assertNull(OneDrive.redirect("https://example.com/?code=abc"))
        assertNull(OneDrive.redirect("pokeplan-onedrive://auth"))
    }

    @Test
    fun aGraphListingIsFoldersFirstThenByNameWithItsDetails() {
        val json = """
            {"value":[
              {"id":"p1","name":"Zebra.jpg","size":1234,"file":{"mimeType":"image/jpeg"}},
              {"id":"f2","name":"Trips","folder":{"childCount":4}},
              {"id":"p2","name":"Apple.png","size":99,"file":{"mimeType":"image/png"}},
              {"id":"f1","name":"Family","folder":{"childCount":0}}
            ]}
        """.trimIndent()
        val items = OneDrive.parseChildren(json)!!
        assertEquals(listOf("Family", "Trips", "Apple.png", "Zebra.jpg"), items.map { it.name })
        assertEquals(4, items.first { it.name == "Trips" }.childCount)
        assertEquals("image/png", items.first { it.name == "Apple.png" }.mime)
        assertTrue(items.first { it.name == "Family" }.folder)
        assertEquals("https://graph.microsoft.com/v1.0/me/drive/root/children?%24select=id,name,size,folder,file,photo&%24top=200", OneDrive.childrenUrl(null))
        assertTrue("items/folder-1/children" in OneDrive.childrenUrl("folder-1"))
        assertTrue("thumbnails/0/medium/content" in OneDrive.thumbnailUrl("p1"))
        assertTrue(OneDrive.downloadUrl("p1").endsWith("/items/p1/content"))
    }

    @Test
    fun aTokenAndAProviderErrorAreRead() {
        val token = OneDrive.parseToken("""{"access_token":"a","refresh_token":"r","expires_in":3600}""")!!
        assertEquals("a", token.access)
        assertEquals("r", token.refresh)
        assertEquals(3_600_000L, token.expiresInMillis)
        assertNull(OneDrive.parseToken("""{"error":"invalid_grant"}"""))
        assertEquals("The sign-in has expired.", OneDrive.problem("""{"error":{"code":"invalid_grant","message":"The sign-in has expired."}}"""))
        assertNull(OneDrive.problem("not json"))
    }
}
