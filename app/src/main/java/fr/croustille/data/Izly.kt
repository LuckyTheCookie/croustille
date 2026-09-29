package fr.croustille.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// Connexion web classique (comme un navigateur) : pas de SMS, pas d'activation.
// GET /Home/Logon (token + cookies) -> POST identifiants -> accueil -> solde.
private const val IZLY = "https://mon-espace.izly.fr"
private const val UA = "Croustille/1.0"

/** Identifiants Izly chiffrés (opt-in explicite de l'utilisateur). */
class IzlyStore(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        "izly",
        MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun lireIdentifiants(): Pair<String, String>? {
        val id = sp.getString("id", null)
        val pin = sp.getString("secret", null)
        return if (id.isNullOrBlank() || pin.isNullOrBlank()) null else id to pin
    }

    fun sauver(id: String, pin: String) = sp.edit {
        putString("id", id)
        putString("secret", pin)
    }

    fun oublier() = sp.edit { clear() }
}

/** Jar mémoire minimal (les cookies de session sont indispensables au login). */
class JarMemoire : CookieJar {
    private val bocaux = mutableMapOf<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) bocaux[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = bocaux[url.host].orEmpty()
}

/** Solde Izly via mon-espace.izly.fr. */
class IzlyRepo(private val client: OkHttpClient, private val store: IzlyStore) {

    fun aUneSession(): Boolean = false // pas de session persistante : reconnexion à chaque fois

    fun aDesIdentifiants(): Boolean = store.lireIdentifiants() != null

    fun oublier() = store.oublier()

    suspend fun solde(id: String, pin: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val page = get("$IZLY/Home/Logon")
            val token = Regex("""name="__RequestVerificationToken"[^>]*value="([^"]+)"""")
                .find(page)?.groupValues?.get(1)
                ?: Regex("""value="([^"]+)"[^>]*name="__RequestVerificationToken"""")
                    .find(page)?.groupValues?.get(1)
                ?: return@withContext Result.failure(IllegalStateException("Page Izly injoignable."))
            val corps = FormBody.Builder()
                .add("__RequestVerificationToken", token)
                .add("ReturnUrl", "/")
                .add("Username", id)
                .add("Password", pin)
                .build()
            val accueil = post("$IZLY/Home/Logon", corps)
            if (accueil.contains("name=\"Password\"") && accueil.contains("name=\"Username\"")) {
                return@withContext Result.failure(IllegalStateException("Identifiants Izly refusés."))
            }
            val montant = extraireSolde(accueil)
                ?: return@withContext Result.failure(
                    IllegalStateException("Connecté, mais solde introuvable sur la page."),
                )
            store.sauver(id, pin)
            Result.success(montant)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun soldeSauve(): Result<String>? {
        val (id, pin) = store.lireIdentifiants() ?: return null
        return solde(id, pin)
    }

    /** Nettoie le HTML puis cherche le montant près de "Solde", sinon 1er montant en €. */
    private fun extraireSolde(html: String): String? {
        var texte = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("\u00a0", " ")
            .replace("&#8364;", "€").replace("&euro;", "€").replace("&EUR;", "EUR")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
        val montant = """(\d[\d\s]*[.,]\d{2})\s*(€|EUR)"""
        // 1. Montant proche du mot "Solde" / "Disponible".
        val pres = Regex("""(?i)(solde|disponible)[^0-9€]{0,120}$montant""").find(texte)
        if (pres != null) return pres.groupValues[2].trim() + " " + pres.groupValues[3]
        // 2. Premier montant en euros de la page.
        val premier = Regex(montant).find(texte)
        if (premier != null) return premier.groupValues[1].trim() + " " + premier.groupValues[2]
        return null
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).get().build()
        return client.newCall(req).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("Izly injoignable (${it.code}).")
            it.body!!.string()
        }
    }

    private fun post(url: String, corps: FormBody): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).post(corps).build()
        return client.newCall(req).execute().use { it.body!!.string() }
    }
}
