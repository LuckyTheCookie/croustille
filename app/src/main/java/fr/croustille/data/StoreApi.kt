package fr.croustille.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val BASE = "https://crousandgo.crous-strasbourg.fr/fonderie/"

interface StoreApi {
    @GET("wp-json/wc/store/v1/products")
    suspend fun products(
        @Query("category") category: String = "23",
        @Query("per_page") perPage: Int = 20,
    ): List<StoreProduct>

    @GET("wp-json/wc/store/v1/cart")
    suspend fun getCart(): Response<ResponseBody>

    companion object {
        fun create(client: OkHttpClient): StoreApi {
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
            return Retrofit.Builder()
                .baseUrl(BASE)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(StoreApi::class.java)
        }
    }
}

/** Trouve l'id de variation correspondant au "Choix menu" sélectionné. */
fun StoreProduct.variationPour(choixMenu: String): Long? =
    variations.firstOrNull { v ->
        v.attributes.any { it.name == "Choix menu" && it.value == choixMenu }
    }?.id ?: variations.firstOrNull()?.id

/**
 * Login 100% requêtes (sans WebView) : POST wp-login.php log/pwd.
 * Le CookieJar persistant conserve wordpress_logged_in_* (chiffré).
 * Seul le paiement Izly reste en WebView (redirection 3DS).
 */
class WpAuth(
    private val client: OkHttpClient,
    private val jar: PersistentCookieJar,
    private val crous: CrousStore? = null,
) {
    suspend fun login(log: String, pwd: String): Boolean = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("log", log)
            .add("pwd", pwd)
            .add("rememberme", "forever")
            .add("redirect_to", "${BASE}wp-admin/")
            .add("testcookie", "1")
            .build()
        val req = Request.Builder().url("${BASE}wp-login.php").post(body).build()
        client.newCall(req).execute().use { resp ->
            val ok = jar.loadForRequest(req.url).any { it.name.startsWith("wordpress_logged_in_") } &&
                resp.code in 200..399
            if (ok) crous?.sauver(log, pwd)
            ok
        }
    }

    fun hasSession(): Boolean = jar.hasSession()

    /** Reconnexion silencieuse avec les identifiants mémorisés (la boutique déconnecte souvent). */
    suspend fun assurerSession(): Boolean {
        if (hasSession()) return true
        val (id, mdp) = crous?.lire() ?: return false
        return try {
            login(id, mdp)
        } catch (e: Exception) {
            false
        }
    }

    fun logout() {
        jar.clearAll()
        crous?.oublier()
    }
}

/** Panier via le endpoint classique Woo (identique au navigateur) :
 *  POST form `?wc-ajax=add_to_cart` avec variation + champs CrousLocation
 *  (zone / jour / heure), car la Store API est bloquée par le plugin
 *  ("Zone de récupération doit être rempli"). La réponse ajax contient un
 *  `error:true` trompeur : on vérifie l'ajout via le compteur du panier.
 */
class CartRepo(
    private val store: StoreApi,
    private val client: OkHttpClient,
    private val assurerSession: suspend () -> Boolean = { true },
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private data class Dispo(val idZone: String = "4", val dateDispo: String = "", val creneaux: String = "0")

    @Serializable
    private data class CartCount(val items_count: Int = 0)

    /** Contenu du panier : lignes (nom, quantité, total) + total, montants en centimes. */
    suspend fun contenu(): Panier = withContext(Dispatchers.IO) {
        val resp = store.getCart()
        if (!resp.isSuccessful) throw IllegalStateException("Panier injoignable.")
        val o = org.json.JSONObject(resp.body()!!.string())
        val lignes = mutableListOf<LignePanier>()
        val items = o.optJSONArray("items") ?: org.json.JSONArray()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            lignes.add(
                LignePanier(
                    name = it.optString("name"),
                    quantity = it.optInt("quantity"),
                    ligneTotal = it.optJSONObject("totals")?.optString("line_total", "0") ?: "0",
                ),
            )
        }
        Panier(lignes, o.optJSONObject("totals")?.optString("total_price", "0") ?: "0")
    }

    /**
     * Checkout 100% natif : lit le formulaire de /commander/ (champs pré-remplis
     * quand connecté), coche les CGV, choisit Izly et poste sur ?wc-ajax=checkout.
     * Retourne l'URL de paiement (page Izly Authorize) en cas de succès.
     */
    suspend fun commander(): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            if (!assurerSession()) {
                return@withContext Result.failure(IllegalStateException("Session expirée, reconnecte-toi."))
            }
            val html = get("${BASE}commander/")
            if (html.contains("woocommerce-form-login") && !html.contains("form.checkout")) {
                return@withContext Result.failure(IllegalStateException("Connecte-toi dans l'onglet Compte."))
            }
            val doc = org.jsoup.Jsoup.parse(html, "${BASE}commander/")
            val form = doc.selectFirst("form.checkout")
                ?: return@withContext Result.failure(
                    IllegalStateException("Checkout introuvable (panier vide ?)."),
                )
            val methodes = form.select("input[name=payment_method]").eachAttr("value")
            val methode = if ("izlyvl" in methodes) "izlyvl" else methodes.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Paiement Izly indisponible."))
            val corps = FormBody.Builder()
            for (el in form.select("input[name], select[name], textarea[name]")) {
                val nom = el.attr("name")
                if (nom.isBlank() || nom == "terms" || nom == "payment_method") continue
                val type = el.attr("type").lowercase()
                if (type == "checkbox" || type == "radio") {
                    if (el.hasAttr("checked")) corps.add(nom, el.attr("value").ifBlank { "1" })
                    continue
                }
                if (type in listOf("submit", "button", "file", "image")) continue
                val valeur = if (el.tagName() == "select") {
                    el.selectFirst("option[selected]")?.attr("value")
                        ?: el.selectFirst("option")?.attr("value").orEmpty()
                } else el.attr("value")
                corps.add(nom, valeur)
            }
            corps.add("terms", "on")
            corps.add("terms-field", "1")
            corps.add("payment_method", methode)
            val req = Request.Builder().url("${BASE}?wc-ajax=checkout").post(corps.build()).build()
            val rep = client.newCall(req).execute().use { it.body!!.string() }
            val o = org.json.JSONObject(rep)
            if (o.optString("result") == "success") {
                val redirect = o.optString("redirect")
                if (redirect.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Checkout sans redirection."))
                }
                Result.success(redirect to methode)
            } else {
                val msg = org.jsoup.Jsoup.parse(o.optString("messages")).text().take(220)
                Result.failure(IllegalStateException(msg.ifBlank { "Checkout refusé." }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).get().build()
        return client.newCall(req).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("Boutique injoignable (${it.code}).")
            it.body!!.string()
        }
    }

    /** Valide un paiement Izly à partir de son URL d'autorisation. */
    suspend fun payerIzly(urlAutorisation: String, izlyId: String, izlyPin: String): Result<String> =
        IzlyPay(client).autoriser(urlAutorisation, izlyId, izlyPin)

    suspend fun ajouterProduit(
        product: StoreProduct,
        choixMenu: String,
        choix1: String,
        choix2: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!assurerSession()) {
                return@withContext Result.failure(IllegalStateException("Session expirée, reconnecte-toi."))
            }
            val variationId = product.variations.firstOrNull { v ->
                v.attributes.any { it.name == "Choix menu" && it.value == choixMenu }
            }?.id ?: return@withContext Result.failure(IllegalStateException("Formule introuvable."))

            val avant = compteurPanier()
            val (zone, date, heure) = dispoDepuisPage(product.permalink)

            val body = FormBody.Builder()
                .add("product_id", product.id.toString())
                .add("variation_id", variationId.toString())
                .add("quantity", "1")
                .add("add-to-cart", product.id.toString())
                .add("attribute_choix-menu", choixMenu)
                .add("attribute_choix-1", choix1)
                .add("attribute_choix-2", choix2)
                .add("crouslocation-zonerecuperation-zone-recuperation", zone)
                .add("crouslocation-zonerecuperation-date-recuperation", date)
                .add("crouslocation-zonerecuperation-heure-recuperation", heure)
                .build()
            val req = Request.Builder()
                .url("${BASE}?wc-ajax=add_to_cart")
                .post(body)
                .build()
            client.newCall(req).execute().close()

            val apres = compteurPanier()
            if (apres > avant) Result.success(Unit)
            else Result.failure(IllegalStateException("La boutique a refusé l'ajout (créneau passé ?). Finalise dans le panier web."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lit `var disposProduit = [...]` de la fiche produit et en tire zone/jour/heure. */
    private fun dispoDepuisPage(permalink: String): Triple<String, String, String> {
        val req = Request.Builder().url(permalink).get().build()
        val html = client.newCall(req).execute().use { it.body!!.string() }
        val brut = Regex("""var disposProduit\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Infos de retrait introuvables sur la fiche produit.")
        val dispo = json.decodeFromString<List<Dispo>>(brut).first()
        // Même format que le JS du site : "28/9/2026" (sans zéros), heure locale Paris.
        val instant = java.time.Instant.parse(dispo.dateDispo)
        val paris = instant.atZone(java.time.ZoneId.of("Europe/Paris"))
        val date = "${paris.dayOfMonth}/${paris.monthValue}/${paris.year}"
        return Triple(dispo.idZone, date, dispo.creneaux)
    }

    private suspend fun compteurPanier(): Int = try {
        val resp = store.getCart()
        if (!resp.isSuccessful) -1
        else json.decodeFromString<CartCount>(resp.body()!!.string()).items_count
    } catch (e: Exception) {
        -1
    }
}
