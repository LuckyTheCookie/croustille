package fr.croustille.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private const val TAG = "IzlyPay"

/**
 * Valide un paiement Izly WebPayments sans passer par le formulaire web.
 *
 * L'URL `.../WebPayments/{id}/Authorize?token=...` (capturée depuis le tunnel
 * Crous) affiche normalement un formulaire id + code. Comme on ne connaît pas
 * ses champs à l'avance, on analyse le formulaire à l'exécution : champs
 * cachés conservés, cases cochées, champ mot de passe <- code Izly mémorisé,
 * champ texte vide <- identifiant Izly. Puis on suit les redirections et on
 * rend l'URL finale (page de confirmation Crous) à afficher.
 */
class IzlyPay(private val client: OkHttpClient) {

    suspend fun autoriser(authorizeUrl: String, izlyId: String, izlyPin: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "authorize $authorizeUrl")
                val page = get(authorizeUrl)
                val doc = Jsoup.parse(page, authorizeUrl)
                // Le formulaire avec un champ mot de passe, sinon le plus gros.
                val formulaires = doc.select("form")
                if (formulaires.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Page Izly sans formulaire (token expiré ?)."),
                    )
                }
                val form = doc.selectFirst("form#id_post_form")
                    ?: formulaires.firstOrNull { f -> f.selectFirst("input[type=password]") != null }
                    ?: formulaires.maxByOrNull { it.select("input").size }!!
                // L'action vient du bouton "Valider" (data-action), sinon du form, sinon de la page.
                val boutonValider = form.select("button[data-action]").firstOrNull { b ->
                    "authorize" in b.attr("data-action").lowercase()
                } ?: doc.select("button[data-action]").firstOrNull { b ->
                    "authorize" in b.attr("data-action").lowercase()
                }
                val action = form.absUrl("action").ifBlank {
                    boutonValider?.let { btn ->
                        try {
                            authorizeUrl.toHttpUrl().resolve(btn.attr("data-action")).toString()
                        } catch (e: Exception) {
                            ""
                        }
                    }.orEmpty()
                }.ifBlank { authorizeUrl }
                val methode = form.attr("method").ifBlank { "post" }
                Log.i(TAG, "form $methode $action (${form.select("input").size} champs)")
                Log.i(TAG, "inputs=" + form.select("input").joinToString(",") {
                    "${it.attr("name")}:${it.attr("type")}"
                })

                val corps = FormBody.Builder()
                for (input in form.select("input")) {
                    val nom = input.attr("name")
                    if (nom.isBlank()) continue
                    val type = input.attr("type").lowercase()
                    val valeur = when {
                        // Cases (CGV...) : on accepte tout.
                        type == "checkbox" || type == "radio" ->
                            input.attr("value").ifBlank { "on" }
                        type == "password" -> izlyPin
                        (type == "text" || type == "email" || type == "tel") && input.attr("value").isBlank() ->
                            if (nom.contains("mail", true) || nom.contains("user", true) ||
                                nom.contains("login", true) || nom.contains("ident", true) ||
                                nom.contains("phone", true) || nom.contains("tel", true)
                            ) izlyId else input.attr("value")
                        type in listOf("submit", "button", "image", "file") -> continue
                        else -> input.attr("value")
                    }
                    corps.add(nom, valeur)
                }
                // Bouton de validation éventuel.
                form.select("button[type=submit], input[type=submit]").firstOrNull()?.let { b ->
                    val n = if (b.tagName() == "button") b.attr("name") else b.attr("name")
                    if (n.isNotBlank()) corps.add(n, b.attr("value"))
                }

                val req = Request.Builder().url(action)
                    .header("User-Agent", "Croustille/1.0")
                    .header("Referer", authorizeUrl)
                    .let { if (methode.equals("get", true)) it.get() else it.post(corps.build()) }
                    .build()
                val reponse = client.newCall(req).execute()
                reponse.use {
                    val finale = it.request.url.toString()
                    val corpsTexte = try {
                        it.peekBody(300_000).string()
                    } catch (e: Exception) {
                        ""
                    }
                    Log.i(TAG, "final=$finale code=${it.code}")
                    // postForm() peut répondre du JSON (ex {"redirect"/"url"}) au lieu de rediriger.
                    val suiteJson = lienDepuisJson(corpsTexte, finale)
                    if (suiteJson != null) {
                        Log.i(TAG, "suite JSON -> $suiteJson")
                        return@withContext suivreLien(suiteJson)
                    }
                    val docFin = Jsoup.parse(corpsTexte, finale)
                    val erreurPage = docFin.selectFirst("#id_error")?.text()?.trim().orEmpty()
                    if (erreurPage.isNotBlank()) {
                        return@withContext Result.failure(IllegalStateException("Izly : $erreurPage"))
                    }
                    if ("error" in finale.lowercase() || "erreur" in finale.lowercase()) {
                        return@withContext Result.failure(
                            IllegalStateException("Izly a refusé (solde ? identifiants ?)."),
                        )
                    }
                    if ("crousandgo" in finale.lowercase()) {
                        // Retour boutique = paiement accepté, commande créée.
                        // La preuve définitive (panier vidé) est vérifiée par l'appelant.
                        return@withContext Result.success(finale)
                    }
                    if (docFin.selectFirst("input[type=password]") != null ||
                        corpsTexte.contains("Code secret")
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException("Izly redemande les identifiants."),
                        )
                    }
                    // Page intermédiaire inconnue : on ne crie pas victoire.
                    return@withContext Result.failure(
                        IllegalStateException("Réponse Izly inattendue, vérifie dans Mes commandes."),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "échec", e)
                Result.failure(e)
            }
        }

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Croustille/1.0")
            .get().build()
        return client.newCall(req).execute().use {
            if (!it.isSuccessful && it.code != 500) throw IllegalStateException("Izly injoignable (${it.code}).")
            it.body!!.string()
        }
    }

    /** Extrait un lien de suite d'une éventuelle réponse JSON (postForm AJAX). */
    private fun lienDepuisJson(corps: String, base: String): String? {
        val t = corps.trim()
        if (!t.startsWith("{")) return null
        return try {
            val o = org.json.JSONObject(t)
            listOf("redirect", "url", "returnUrl", "redirectUrl", "location")
                .firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() } }
                ?.let { lien ->
                    try {
                        base.toHttpUrl().resolve(lien)?.toString() ?: lien
                    } catch (e: Exception) {
                        lien
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "JSON illisible", e)
            null
        }
    }

    /** Suit un lien de suite (confirmation ou étape suivante) et évalue le résultat. */
    private suspend fun suivreLien(url: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Croustille/1.0")
                    .get().build()
                client.newCall(req).execute().use {
                    val finale = it.request.url.toString()
                    val corps = try {
                        it.peekBody(300_000).string()
                    } catch (e: Exception) {
                        ""
                    }
                    evaluerResultat(finale, corps)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun evaluerResultat(finale: String, corpsTexte: String): Result<String> {
        val docFin = Jsoup.parse(corpsTexte, finale)
        val erreurPage = docFin.selectFirst("#id_error")?.text()?.trim().orEmpty()
        if (erreurPage.isNotBlank()) {
            return Result.failure(IllegalStateException("Izly : $erreurPage"))
        }
        if ("error" in finale.lowercase() || "erreur" in finale.lowercase()) {
            return Result.failure(
                IllegalStateException("Izly a refusé (solde ? identifiants ?)."),
            )
        }
        if ("crousandgo" in finale.lowercase()) {
            // Retour boutique = paiement accepté, commande créée.
            return Result.success(finale)
        }
        if (docFin.selectFirst("input[type=password]") != null ||
            corpsTexte.contains("Code secret")
        ) {
            return Result.failure(
                IllegalStateException("Izly redemande les identifiants."),
            )
        }
        // Page intermédiaire inconnue : on ne crie pas victoire.
        return Result.failure(
            IllegalStateException("Réponse Izly inattendue, vérifie dans Mes commandes."),
        )
    }
}

fun String.estUrlPaiementIzly(): Boolean {
    val h = try {
        this.toHttpUrl().host
    } catch (e: Exception) {
        return false
    }
    return h.contains("izly.fr") && contains("/WebPayments/") && contains("Authorize")
}
