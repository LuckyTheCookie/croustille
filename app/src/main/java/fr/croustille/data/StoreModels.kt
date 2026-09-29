package fr.croustille.data

import kotlinx.serialization.Serializable

@Serializable
data class Term(val name: String, val slug: String? = null)

@Serializable
data class ProductAttribute(val name: String, val terms: List<Term> = emptyList())

@Serializable
data class VariationAttribute(val name: String, val value: String? = null)

@Serializable
data class Variation(val id: Long, val attributes: List<VariationAttribute> = emptyList())

@Serializable
data class Price(val price: String = "0", val currency_symbol: String = "€")

@Serializable
data class StoreProduct(
    val id: Long,
    val name: String,
    val slug: String,
    val permalink: String,
    val prices: Price = Price(),
    val attributes: List<ProductAttribute> = emptyList(),
    val variations: List<Variation> = emptyList(),
    val is_in_stock: Boolean = true,
)

fun StoreProduct.prixAffiche(): String {
    val centimes = prices.price.toLongOrNull() ?: 0
    return "${centimes / 100},${(centimes % 100).toString().padStart(2, '0')} ${prices.currency_symbol}"
}

/** "100" (centimes) -> "1,00 €". */
fun centimesVersEuros(centimes: String): String {
    val c = centimes.toLongOrNull() ?: 0
    return "${c / 100},${(c % 100).toString().padStart(2, '0')} €"
}

/** Contenu du panier (montants en centimes, format Store API). */
data class LignePanier(val name: String = "", val quantity: Int = 0, val ligneTotal: String = "0")

data class Panier(val items: List<LignePanier> = emptyList(), val total: String = "0")

fun StoreProduct.choixMenu(): List<String> =
    attributes.firstOrNull { it.name == "Choix menu" }?.terms?.map { it.name } ?: emptyList()

fun StoreProduct.choixDessert(n: String): List<String> =
    attributes.firstOrNull { it.name == n }?.terms?.map { it.name } ?: emptyList()
