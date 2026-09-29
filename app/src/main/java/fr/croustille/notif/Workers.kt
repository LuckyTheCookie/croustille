package fr.croustille.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.croustille.data.CroustillantApi
import fr.croustille.data.Prefs
import fr.croustille.data.StoreApi
import fr.croustille.data.cuisineCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private val PARIS = ZoneId.of("Europe/Paris")

private fun estWeekend(): Boolean {
    val j = LocalDate.now(PARIS).dayOfWeek
    return j == DayOfWeek.SATURDAY || j == DayOfWeek.SUNDAY
}

/** Rappel quotidien : prévient quand les menus sont dispos (lun–ven). */
class MenuWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (estWeekend()) return Result.success()
        return try {
            val prefs = Prefs(applicationContext)
            val menus = CroustillantApi.create()
                .menusAVenir(cuisineCode(prefs.restoCode.first())).data
            if (menus.isEmpty()) return Result.success()
            val aujourdHui = LocalDate.now(PARIS).toString()
            if (prefs.lastMenuDate.first() == aujourdHui) return Result.success()
            prefs.setLastMenuDate(aujourdHui)
            notifierMenus(applicationContext, menus.size)
            Result.success()
        } catch (e: Exception) {
            Result.success() // réessaiera au prochain passage, sans vider la batterie
        }
    }
}

/**
 * Surveille la boutique Fonderie : prévient quand le produit le plus bas
 * passe sous le seuil. 2-3 requêtes par passage, rien entre les passages.
 */
class StockWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (estWeekend()) return Result.success()
        return try {
            val prefs = Prefs(applicationContext)
            val seuil = prefs.stockSeuil.first()
            val client = OkHttpClient.Builder().build()
            val store = StoreApi.create(client)
            val produits = withContext(Dispatchers.IO) { store.products() }
            if (produits.isEmpty()) {
                prefs.setLastStock(null, null)
                return Result.success() // boutique vide = cutoff passé, rien à signaler
            }
            var min = Int.MAX_VALUE
            var nomMin = ""
            for (p in produits.take(6)) {
                val stock = withContext(Dispatchers.IO) { stockProduit(client, p.permalink) }
                if (stock != null && stock < min) {
                    min = stock
                    nomMin = p.name
                }
            }
            if (min == Int.MAX_VALUE) return Result.success()
            val dernierMin = prefs.lastStockMin.first()
            if (min <= seuil) {
                // Notifie une fois par palier : pas de spam si le stock ne bouge pas.
                if (dernierMin != min) {
                    notifierStockBas(applicationContext, min, nomMin)
                    prefs.setLastStock(min, LocalDate.now(PARIS).toString())
                }
            } else if (dernierMin != null) {
                prefs.setLastStock(null, null) // réarmé pour la prochaine rupture
            }
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }

    private fun stockProduit(client: OkHttpClient, permalink: String): Int? {
        val req = Request.Builder().url(permalink).get().build()
        val html = client.newCall(req).execute().use { it.body!!.string() }
        // "79 en stock" dans data-product_variations : on prend le minimum.
        return Regex("""(\d+)\s+en stock""").findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .minOrNull()
    }
}
