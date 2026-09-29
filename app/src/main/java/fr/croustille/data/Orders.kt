package fr.croustille.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private const val COMPTE = "https://crousandgo.crous-strasbourg.fr/fonderie/mon-compte/"

data class Commande(
    val numero: String, // ex "#4567"
    val date: String,
    val statut: String,
    val total: String,
    val lienDetail: String,
)

data class LigneCommande(
    val nom: String,
    val quantite: String,
    val total: String,
    val meta: List<Pair<String, String>> = emptyList(), // ex Zone/Jour/Heure de récupération
)

data class Retrait(val jour: String, val heure: String, val zone: String)

/** Extrait les infos de retrait (plugin CrousLocation) des lignes de commande. */
fun retraitDe(lignes: List<LigneCommande>): Retrait? {
    fun cherche(mot: String): String = lignes.asSequence()
        .flatMap { it.meta.asSequence() }
        .firstOrNull { (k, _) -> k.lowercase().contains(mot) }?.second.orEmpty()
    val jour = cherche("jour")
    if (jour.isBlank()) return null
    return Retrait(jour = jour, heure = cherche("heure"), zone = cherche("zone"))
}

/** "28/9/2026" (format du site, sans zéros) -> LocalDate. */
fun Retrait.dateRetrait(): java.time.LocalDate? = try {
    val p = jour.split('/', '.').map { it.trim().toInt() }
    java.time.LocalDate.of(p[2], p[1], p[0])
} catch (e: Exception) {
    null
}

/** "11:45-13:30" -> (début minutes, fin minutes). */
fun Retrait.creneauMinutes(): Pair<Int, Int>? = try {
    val (a, b) = heure.split('-').map { it.trim() }
    fun m(s: String): Int {
        val (h, mi) = s.split(':').map { it.toInt() }
        return h * 60 + mi
    }
    m(a) to m(b)
} catch (e: Exception) {
    null
}

class PasConnecte : IllegalStateException("Connecte-toi dans l'onglet Compte pour voir tes commandes.")

/** Lit "Mes commandes" WooCommerce avec la session de l'app (sans WebView). */
class OrdersRepo(
    private val client: OkHttpClient,
    private val assurerSession: suspend () -> Boolean = { true },
) {

    suspend fun commandes(): List<Commande> = withContext(Dispatchers.IO) {
        try {
            commandesBrut()
        } catch (e: PasConnecte) {
            if (!assurerSession()) throw e
            try {
                commandesBrut()
            } catch (e2: PasConnecte) {
                throw e2
            }
        }
    }

    private fun commandesBrut(): List<Commande> {
        val html = get("${COMPTE}orders/")
        val doc = Jsoup.parse(html, COMPTE)
        if (doc.selectFirst("form.woocommerce-form-login, form.login") != null) throw PasConnecte()
        return doc.select("table.woocommerce-orders-table tbody tr").mapNotNull { tr ->
            val lien = tr.selectFirst(".woocommerce-orders-table__cell-order-number a") ?: return@mapNotNull null
            Commande(
                numero = lien.text().trim(),
                date = tr.selectFirst(".woocommerce-orders-table__cell-order-date")?.text()?.trim().orEmpty(),
                statut = tr.selectFirst(".woocommerce-orders-table__cell-order-status")?.text()?.trim().orEmpty(),
                total = tr.selectFirst(".woocommerce-orders-table__cell-order-total")?.text()?.trim().orEmpty(),
                lienDetail = lien.absUrl("href"),
            )
        }
    }

    suspend fun detail(commande: Commande): List<LigneCommande> = withContext(Dispatchers.IO) {
        try {
            detailBrut(commande)
        } catch (e: PasConnecte) {
            if (!assurerSession()) throw e
            detailBrut(commande)
        }
    }

    private fun detailBrut(commande: Commande): List<LigneCommande> {
        val html = get(commande.lienDetail)
        val doc = Jsoup.parse(html, COMPTE)
        if (doc.selectFirst("form.woocommerce-form-login, form.login") != null) throw PasConnecte()
        return doc.select("table.woocommerce-table--order-details tbody tr.woocommerce-table__line-item").map { tr ->
            val nom = tr.selectFirst(".woocommerce-table__product-name")?.ownText()?.trim().orEmpty()
            val qte = tr.selectFirst(".product-quantity")?.text()?.trim().orEmpty()
            val meta = tr.select(".wc-item-meta li").map { li ->
                val cle = li.selectFirst(".wc-item-meta-label")?.ownText()?.trim()?.trimEnd(':').orEmpty()
                val valeur = li.selectFirst("p, .wc-item-meta-value")?.text()?.trim()
                    ?: li.ownText().trim()
                cle to valeur
            }.filter { it.first.isNotBlank() }
            LigneCommande(
                nom = nom,
                quantite = qte,
                total = tr.selectFirst(".woocommerce-table__product-total")?.text()?.trim().orEmpty(),
                meta = meta,
            )
        }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).get().build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Boutique injoignable (${resp.code}).")
            resp.body!!.string()
        }
    }
}
