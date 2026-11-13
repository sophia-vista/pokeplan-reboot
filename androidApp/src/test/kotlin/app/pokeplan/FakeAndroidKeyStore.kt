package app.pokeplan

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A stand-in for the Android Keystore under Robolectric, which has none (REP-033): `KeyStore.getInstance("AndroidKeyStore")` and
 * `KeyGenerator.getInstance("AES", "AndroidKeyStore")` work as the app calls them, with AES keys kept in memory. A key "survives" a simulated process death
 * (the map is kept) and is lost with [wipe], like a phone that was reset or a key the Keystore dropped; [available] false makes the Keystore fail as it does
 * on a phone whose Keystore cannot be reached, so the fail-closed paths can be tested.
 */
object FakeAndroidKeyStore {
    private val keys = mutableMapOf<String, SecretKey>()
    @Volatile var available = true

    fun install() {
        if (Security.getProvider(NAME) == null) Security.addProvider(Provider())
        available = true
    }

    fun wipe() = synchronized(keys) { keys.clear() }

    private const val NAME = "AndroidKeyStore"

    @Suppress("DEPRECATION") private class Provider : java.security.Provider(NAME, 1.0, "A test Keystore") {
        init {
            put("KeyStore.$NAME", Store::class.java.name)
            put("KeyGenerator.AES", Generator::class.java.name)
        }
    }

    class Store : KeyStoreSpi() {
        private fun check() { if (!available) throw java.security.KeyStoreException("the Keystore is not available") }
        override fun engineLoad(stream: InputStream?, password: CharArray?) = check()
        override fun engineGetKey(alias: String, password: CharArray?): Key? { check(); return synchronized(keys) { keys[alias] } }
        override fun engineContainsAlias(alias: String): Boolean { check(); return synchronized(keys) { alias in keys } }
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(synchronized(keys) { keys.keys.toList() })
        override fun engineSize(): Int = synchronized(keys) { keys.size }
        override fun engineDeleteEntry(alias: String) { synchronized(keys) { keys.remove(alias) } }
        override fun engineIsKeyEntry(alias: String): Boolean = engineContainsAlias(alias)
        override fun engineIsCertificateEntry(alias: String) = false
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCreationDate(alias: String): Date? = null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) = throw UnsupportedOperationException()
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) = throw UnsupportedOperationException()
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = throw UnsupportedOperationException()
        override fun engineGetCertificateAlias(cert: Certificate): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = throw UnsupportedOperationException()
    }

    class Generator : KeyGeneratorSpi() {
        private var spec: KeyGenParameterSpec? = null
        override fun engineInit(random: SecureRandom?) = throw UnsupportedOperationException("the app always passes a KeyGenParameterSpec")
        override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) { spec = params as KeyGenParameterSpec }
        override fun engineInit(keysize: Int, random: SecureRandom?) = throw UnsupportedOperationException()
        override fun engineGenerateKey(): SecretKey {
            if (!available) throw java.security.ProviderException("the Keystore is not available")
            val s = checkNotNull(spec)
            val bytes = ByteArray(s.keySize / 8).also { SecureRandom().nextBytes(it) }
            return SecretKeySpec(bytes, "AES").also { synchronized(keys) { keys[s.keystoreAlias] = it } }
        }
    }
}
