package fr.croustille.notif

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.croustille.data.CookieStore
import fr.croustille.data.CroustillantApi
import fr.croustille.data.OrdersRepo
import fr.croustille.data.PersistentCookieJar
import fr.croustille.data.Prefs
import fr.croustille.data.StoreApi
import fr.croustille.data.creneauMinutes
import fr.croustille.data.cuisineCode
import fr.croustille.data.dateRetrait
import fr.croustille.data.formulesMidi
import fr.croustille.data.retraitDe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private val PARIS = ZoneId.of("Europe/Paris")

private fun estWeekend(): Boolean {
    val j = LocalDate.now(PARIS).dayOfWeek
    return j == DayOfWeek.SATURDAY || j == DayOfWeek.SUNDAY
}

/** (avancement 0..100, "13h30", fin en millis) pour le suivi du retrait. */
private fun etatSuivi(debut: Int, fin: Int): Triple<Int, String, Long> {
    val z = ZonedDateTime.now(PARIS)
    val maintenant = z.hour * 60 + z.minute
    val av = ((maintenant - debut) * 100 / (fin - debut).coerceAtLeast(1)).coerceIn(0, 100)
    val finTexte = "%dh%02d".format(fin / 60, fin % 60)
    val finMillis = z.withHour(fin / 60).withMinute(fin % 60).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()
    return Triple(av, finTexte, finMillis)
}

/** Rappel quotidien : le repas du lendemain est dispo à la commande (lun–ven). */
class MenuWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (estWeekend()) return Result.success()
        return try {
            val prefs = Prefs(applicationContext)
            val menus = CroustillantApi.create()
                .menusAVenir(cuisineCode(prefs.restoCode.first())).data
            if (menus.isEmpty()) return Result.success()
            val aujourdHui = LocalDate.now(PARIS)
            // Prochain jour avec menu après aujourd'hui (= "demain", ou lundi si week-end).
            val prochain = menus.mapNotNull { m ->
                try {
                    LocalDate.parse(m.date, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                } catch (e: Exception) {
                    null
                }
            }.filter { it.isAfter(aujourdHui) }.minOrNull() ?: return Result.success()
            val cle = "menu:${prochain}"
            if (prefs.lastMenuDate.first() == cle) return Result.success()
            val menuJour = menus.firstOrNull { m ->
                try {
                    LocalDate.parse(m.date, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")) == prochain
                } catch (e: Exception) {
                    false
                }
            }
            val formules = menuJour?.formulesMidi().orEmpty()
            val resume = if (formules.isEmpty()) "Hop, à table"
            else formules.joinToString(" ou ") { it.firstOrNull() ?: "" }.take(90)
            val titreJour = prochain.format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.FRENCH),
            ).replaceFirstChar { it.uppercase() }
            prefs.setLastMenuDate(cle)
            notifierMenus(applicationContext, titreJour, resume)
            Result.success()
        } catch (e: java.io.IOException) {
            Result.retry()
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
            val dernierMinAvant = prefs.lastStockMin.first()
            if (produits.isEmpty()) {
                // Boutique vide alors qu'il y avait du stock : tout est parti (ou cutoff passé).
                if (dernierMinAvant != null) {
                    notifierBoutiqueVide(applicationContext)
                    prefs.setLastStock(null, null)
                }
                return Result.success()
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
        } catch (e: java.io.IOException) {
            Result.retry()
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

/**
 * Tous les jours à 8h : y a-t-il une commande à retirer aujourd'hui ?
 * Si oui : notif "détectée" + planifie le suivi du retrait.
 */
class RdvWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (estWeekend()) return Result.success()
        return try {
            val prefs = Prefs(applicationContext)
            if (!prefs.rdvRetrait.first()) return Result.success()
            val jar = PersistentCookieJar(CookieStore(applicationContext))
            if (!jar.hasSession()) return Result.success() // pas connecté : rien à détecter
            val repo = OrdersRepo(OkHttpClient.Builder().cookieJar(jar).build())
            val aujourdHui = LocalDate.now(PARIS)
            for (commande in repo.commandes().take(5)) {
                val lignes = try {
                    repo.detail(commande)
                } catch (e: Exception) {
                    continue
                }
                val retrait = retraitDe(lignes) ?: continue
                if (retrait.dateRetrait() != aujourdHui) continue
                val (debut, fin) = retrait.creneauMinutes() ?: (11 * 60 + 45 to 13 * 60 + 30)
                val debutNotif = (debut - 15).coerceAtLeast(0)
                notifierCommandeDetectee(
                    applicationContext,
                    commande.numero,
                    "%dh%02d".format(debutNotif / 60, debutNotif % 60),
                )
                planifierSuiviRetrait(applicationContext, commande.numero, debutNotif, fin)
                return Result.success()
            }
            Result.success()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.success()
        }
    }
}

/** Suivi du retrait : notif persistante avec barre de progression, toutes les 15 min. */
class PickupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val numero = inputData.getString("numero") ?: "?"
        val debut = inputData.getInt("debut", 11 * 60 + 30)
        val fin = inputData.getInt("fin", 13 * 60 + 30)
        val (av, finTexte, finMillis) = etatSuivi(debut, fin)
        val notif = construireNotifRetrait(applicationContext, numero, finTexte, av, finMillis)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                5, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(5, notif)
        }
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        val numero = inputData.getString("numero") ?: return Result.success()
        val debut = inputData.getInt("debut", 11 * 60 + 30)
        val fin = inputData.getInt("fin", 13 * 60 + 30)
        val z = ZonedDateTime.now(ZoneId.of("Europe/Paris"))
        val maintenant = z.hour * 60 + z.minute
        if (maintenant >= fin) {
            notifierBonAppetit(applicationContext, numero)
            Glyph.miroir(applicationContext, -1) // éteint
            return Result.success()
        }
        val (avancement, finTexte, finMillis) = etatSuivi(debut, fin)
        notifierRetrait(applicationContext, numero, finTexte, avancement, finMillis)
        Glyph.miroir(applicationContext, avancement)
        // Prochain point dans 15 min (ou à la fin).
        val prochainDelai = (fin - maintenant).coerceAtMost(15).coerceAtLeast(1)
        val suite = OneTimeWorkRequestBuilder<PickupWorker>()
            .setInitialDelay(prochainDelai.toLong(), TimeUnit.MINUTES)
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("suivi-$numero", ExistingWorkPolicy.REPLACE, suite)
        return Result.success()
    }
}
