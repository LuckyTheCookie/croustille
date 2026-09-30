package fr.croustille.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// Connexion web classique (comme un navigateur) : pas de SMS, pas d'activation.
// GET /Home/Logon (token + cookies) -> POST identifiants -> accueil -> solde.
private const val IZLY = "https://mon-espace.izly.fr"
private const val UA_NAVIGATEUR =
    "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
private const val TAG = "Izly"

/** Identifiants Izly chiffrés (opt-in explicite de l'utilisateur). */
class IzlyStore(ctx: Context) {
    private val sp: SharedPreferences = prefsSecurisees(ctx, "izly")

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

/** Jar mémoire : fusionne par nom, ignore les expirés, thread-safe. */
class JarMemoire : CookieJar {
    private val bocaux = mutableMapOf<String, MutableMap<String, Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val bocal = bocaux.getOrPut(url.host) { mutableMapOf() }
        val maintenant = System.currentTimeMillis()
        for (c in cookies) {
            if (c.expiresAt < maintenant) bocal.remove(c.name)
            else bocal[c.name] = c
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val maintenant = System.currentTimeMillis()
        val bocal = bocaux[url.host] ?: return emptyList()
        bocal.entries.removeIf { it.value.expiresAt < maintenant }
        return bocal.values.filter { it.matches(url) && it.expiresAt >= maintenant }
    }

    @Synchronized
    fun vider() = bocaux.clear()
}

/** Solde Izly via mon-espace.izly.fr. */
class IzlyRepo(private val client: OkHttpClient, private val store: IzlyStore) {

    fun aUneSession(): Boolean = false // pas de session persistante : reconnexion à chaque fois

    fun aDesIdentifiants(): Boolean = store.lireIdentifiants() != null

    fun lireIdentifiants(): Pair<String, String>? = store.lireIdentifiants()

    fun oublier() {
        store.oublier()
        (client.cookieJar as? JarMemoire)?.vider()
    }

    suspend fun solde(id: String, pin: String): Result<String> = withContext(Dispatchers.IO) {
        // 1 retry sur erreur réseau uniquement (pas sur refus d'identifiants).
        var tentative = 0
        var dernierEchec: Exception? = null
        while (tentative < 2) {
            try {
                return@withContext soldeBrut(id.trim(), pin.trim())
            } catch (e: IOException) {
                dernierEchec = e
                Log.w(TAG, "réseau, nouvel essai", e)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
            tentative++
        }
        Result.failure(dernierEchec ?: IOException("Izly injoignable."))
    }

    private fun soldeBrut(id: String, pin: String): Result<String> {
        // 1. Page de login : token anti-CSRF + cookies.
        val page = get("$IZLY/Home/Logon")
        val token = jetonAntiCsrf(page)
            ?: return Result.failure(IllegalStateException("Page Izly injoignable (token absent)."))
        // 2. Connexion.
        val corps = FormBody.Builder()
            .add("__RequestVerificationToken", token)
            .add("ReturnUrl", "/")
            .add("Username", id)
            .add("Password", pin)
            .build()
        val (code, accueil) = post("$IZLY/Home/Logon", corps, "$IZLY/Home/Logon")
        if (code == 400 || code == 403) {
            return Result.failure(IllegalStateException("Izly refuse la connexion (anti-robot ?)."))
        }
        if (formulaireLoginPresent(accueil)) {
            val messageSite = messageErreurSite(accueil)
            return Result.failure(
                IllegalStateException(messageSite ?: "Identifiants Izly refusés."),
            )
        }
        // 3. Solde sur la page d'accueil.
        val montant = extraireSolde(accueil)
            ?: return Result.failure(
                IllegalStateException("Connecté, mais solde introuvable sur la page. Réessaie."),
            )
        store.sauver(id, pin)
        return Result.success(montant)
    }

    suspend fun soldeSauve(): Result<String>? {
        val (id, pin) = store.lireIdentifiants() ?: return null
        return solde(id, pin)
    }

    /** Token anti-CSRF tolérant : quotes simples/doubles, ordre/noms d'attributs, casse. */
    private fun jetonAntiCsrf(page: String): String? {
        val motifs = listOf(
            Regex("""name\s*=\s*["']__RequestVerificationToken["'][^>]*?value\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""value\s*=\s*["']([^"']+)["'][^>]*?name\s*=\s*["']__RequestVerificationToken["']""", RegexOption.IGNORE_CASE),
        )
        return motifs.firstNotNullOfOrNull { it.find(page)?.groupValues?.get(1) }
    }

    /** Le formulaire de login est-il (encore) affiché ? */
    private fun formulaireLoginPresent(html: String): Boolean {
        if (!html.contains("Password", ignoreCase = true)) return false
        return Regex("""<input[^>]*name\s*=\s*["']?(Username|Password)["']?""", RegexOption.IGNORE_CASE)
            .containsMatchIn(html)
    }

    /** Message d'erreur affiché par le site lui-même, s'il y en a un. */
    private fun messageErreurSite(html: String): String? {
        val texte = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
        val cles = listOf("identifiant", "incorrect", "invalide", "erreur", "verrouill", "bloqué")
        val idx = cles.mapNotNull { mot ->
            texte.indexOf(mot, ignoreCase = true).takeIf { it >= 0 }?.let { mot to it }
        }.minByOrNull { it.second } ?: return null
        val debut = (idx.second - 20).coerceAtLeast(0)
        return texte.substring(debut, (idx.second + 160).coerceAtMost(texte.length)).trim().take(160)
    }

    /**
     * Nettoie le HTML puis cherche le montant :
     * 1. près de "Solde"/"Disponible" (avec ou sans décimales),
     * 2. sinon rien (pas de devinette : un faux solde est pire qu'une erreur claire).
     */
    private fun extraireSolde(html: String): String? {
        val texte = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace(" ", " ")
            .replace(" ", " ").replace(" ", " ").replace(" ", " ")
            .replace("&#8364;", "€").replace("&euro;", "€").replace("&EUR;", "EUR")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
        val montant = """(\d[\d\s]*([.,]\d{2})?)\s*(€|EUR)"""
        val pres = Regex("""(?i)(solde|disponible)[^0-9€]{0,120}$montant""").find(texte)
        if (pres != null) return pres.groupValues[2].trim() + " " + pres.groupValues[4]
        return null
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA_NAVIGATEUR)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "fr-FR,fr;q=0.9")
            .get().build()
        return client.newCall(req).execute().use {
            if (!it.isSuccessful) throw IOException("Izly injoignable (${it.code}).")
            it.body?.string() ?: throw IOException("Réponse Izly vide.")
        }
    }

    private fun post(url: String, corps: FormBody, referer: String): Pair<Int, String> {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA_NAVIGATEUR)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "fr-FR,fr;q=0.9")
            .header("Referer", referer)
            .header("Origin", IZLY)
            .post(corps).build()
        return client.newCall(req).execute().use { Pair(it.code, it.body?.string().orEmpty()) }
    }
}
