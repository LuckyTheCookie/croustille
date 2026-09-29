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
class WpAuth(private val client: OkHttpClient, private val jar: PersistentCookieJar) {
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
            jar.loadForRequest(req.url).any { it.name.startsWith("wordpress_logged_in_") } &&
                resp.code in 200..399
        }
    }

    fun hasSession(): Boolean = jar.hasSession()

    fun logout() = jar.clearAll()
}

/** Panier via le endpoint classique Woo (identique au navigateur) :
 *  POST form `?wc-ajax=add_to_cart` avec variation + champs CrousLocation
 *  (zone / jour / heure), car la Store API est bloquée par le plugin
 *  ("Zone de récupération doit être rempli"). La réponse ajax contient un
 *  `error:true` trompeur : on vérifie l'ajout via le compteur du panier.
 */
class CartRepo(private val store: StoreApi, private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private data class Dispo(val idZone: String = "4", val dateDispo: String = "", val creneaux: String = "0")

    @Serializable
    private data class CartCount(val items_count: Int = 0)

    suspend fun ajouterProduit(
        product: StoreProduct,
        choixMenu: String,
        choix1: String,
        choix2: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
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
