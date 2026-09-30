package fr.croustille.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val BASE_URL = "https://crousandgo.crous-strasbourg.fr/fonderie/"

/**
 * Préférences chiffrées, avec repli non chiffré si le trousseau est invalide
 * (ex: écran de verrouillage modifié -> KeyPermanentlyInvalidatedException).
 */
fun prefsSecurisees(ctx: Context, nom: String): SharedPreferences {
    val app = ctx.applicationContext
    return try {
        EncryptedSharedPreferences.create(
            app,
            nom,
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        android.util.Log.w("Prefs", "trousseau invalide pour $nom, repli local", e)
        app.getSharedPreferences("${nom}_clair", Context.MODE_PRIVATE)
    }
}

/** Stockage chiffré des cookies de session WordPress/WooCommerce. */
class CookieStore(ctx: Context) {
    private val sp: SharedPreferences = prefsSecurisees(ctx, "session_cookies")

    fun save(host: String, cookies: List<Cookie>) {
        sp.edit { putStringSet(host, cookies.map { it.toString() }.toSet()) }
    }

    fun load(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return (sp.getStringSet(url.host, emptySet()) ?: emptySet())
            .mapNotNull { Cookie.parse(url, it) }
            .filter { it.expiresAt >= now }
    }

    fun clear() = sp.edit { clear() }

    fun hasSession(): Boolean =
        load(BASE_URL.toHttpUrl()).any { it.name.startsWith("wordpress_logged_in_") }
}

/** Identifiants CrousAndGo chiffrés (reconnexion automatique silencieuse). */
class CrousStore(ctx: Context) {
    private val sp: SharedPreferences = prefsSecurisees(ctx, "crous")

    fun lire(): Pair<String, String>? {
        val id = sp.getString("id", null)
        val mdp = sp.getString("mdp", null)
        return if (id.isNullOrBlank() || mdp.isNullOrBlank()) null else id to mdp
    }

    fun sauver(id: String, mdp: String) = sp.edit {
        putString("id", id)
        putString("mdp", mdp)
    }

    fun oublier() = sp.edit { clear() }
}

/** CookieJar mémoire + persistance chiffrée : le login survit au redémarrage. */
class PersistentCookieJar(private val store: CookieStore) : CookieJar {
    private val memory = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val merged = (memory[url.host].orEmpty() + cookies)
            .groupBy { it.name }.map { (_, v) -> v.last() }
        memory[url.host] = merged
        store.save(url.host, merged)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val cached = memory.getOrPut(url.host) { store.load(url) }
            .filter { it.expiresAt >= now }
        return cached
    }

    fun clearAll() {
        memory.clear()
        store.clear()
    }

    /** Injection externe (ex: cookies lus depuis la WebView). */
    fun injecter(url: okhttp3.HttpUrl, cookies: List<Cookie>) = saveFromResponse(url, cookies)

    /** Tous les cookies valides pour une URL (pour les injecter dans la WebView). */
    fun cookiesFor(url: String): List<Cookie> = loadForRequest(url.toHttpUrl())

    fun hasSession(): Boolean =
        memory.values.flatten().any { it.name.startsWith("wordpress_logged_in_") && it.expiresAt >= System.currentTimeMillis() } ||
            store.hasSession()
}
