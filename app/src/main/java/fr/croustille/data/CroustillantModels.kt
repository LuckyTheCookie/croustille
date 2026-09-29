package fr.croustille.data

import kotlinx.serialization.Serializable

@Serializable
data class ApiWrapper<T>(val success: Boolean, val data: T)

@Serializable
data class Region(val code: Int, val libelle: String)

@Serializable
data class Restaurant(
    val code: Int,
    val nom: String,
    val adresse: String? = null,
    val zone: String? = null,
    val image_url: String? = null,
    val ouvert: Boolean = true,
)

@Serializable
data class Plat(val code: Int, val ordre: Int, val libelle: String)

@Serializable
data class Categorie(val code: Long, val libelle: String, val ordre: Int, val plats: List<Plat> = emptyList())

@Serializable
data class Repas(val code: Long, val type: String, val categories: List<Categorie> = emptyList())

@Serializable
data class MenuJour(val code: Long, val date: String, val repas: List<Repas> = emptyList())

/** Sépare "Plat A + accompagnements OU Plat B" en 2 formules. "OU" = code 1630 côté API. */
fun MenuJour.formulesMidi(): List<List<String>> {
    val plats = repas.firstOrNull { it.type == "midi" }
        ?.categories?.firstOrNull()
        ?.plats?.sortedBy { it.ordre }?.map { it.libelle } ?: return emptyList()
    val out = mutableListOf(mutableListOf<String>())
    for (p in plats) {
        if (p.equals("OU", ignoreCase = true)) out.add(mutableListOf())
        else out.last().add(p)
    }
    return out.filter { it.isNotEmpty() }
}

fun MenuJour.formulesSoir(): List<List<String>> {
    val plats = repas.firstOrNull { it.type == "soir" }
        ?.categories?.firstOrNull()
        ?.plats?.sortedBy { it.ordre }?.map { it.libelle } ?: return emptyList()
    val out = mutableListOf(mutableListOf<String>())
    for (p in plats) {
        if (p.equals("OU", ignoreCase = true)) out.add(mutableListOf())
        else out.last().add(p)
    }
    return out.filter { it.isNotEmpty() }
}

// ---------------------------------------------------------------------------
// Fonderie : point de retrait CrousAndGo (absent de la base CROUStillant).
// Menus = cuisine de l'Illberg (code 1399), commande = boutique /fonderie/.
// ---------------------------------------------------------------------------
const val FONDERIE_CODE = -1
const val FONDERIE_CUISINE = 1399
const val STRASBOURG_CODE = 25

fun fonderieEntry() = Restaurant(
    code = FONDERIE_CODE,
    nom = "Crous & Go' · Fonderie",
    adresse = "16 rue de la Fonderie, Mulhouse",
    zone = "Retrait 11h45 – 13h30",
    image_url = null,
    ouvert = true,
)

/** Code cuisine CROUStillant à interroger pour un choix donné. */
fun cuisineCode(selection: Int): Int = if (selection == FONDERIE_CODE) FONDERIE_CUISINE else selection

/** Seule la Fonderie est commandable (boutique WooCommerce connue). */
fun isCommandable(selection: Int): Boolean = selection == FONDERIE_CODE
