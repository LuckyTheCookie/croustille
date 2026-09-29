package fr.croustille.notif

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val W_MENU = "menu13h"
private const val W_STOCK = "stock_bas"
private const val W_RDV = "rdv_8h"

private val reseau = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

/** Prochain 13h00 un jour ouvré (heure de Paris). */
private fun delaiProchain13hOuvre(): Long = delaiProchainOuvre(13, 0)

/** Prochain 8h00 un jour ouvré (heure de Paris). */
private fun delaiProchain8hOuvre(): Long = delaiProchainOuvre(8, 0)

private fun delaiProchainOuvre(heure: Int, minute: Int): Long {
    val maintenant = ZonedDateTime.now(ZoneId.of("Europe/Paris"))
    var candidat = maintenant.withHour(heure).withMinute(minute).withSecond(0).withNano(0)
    while (candidat.dayOfWeek == DayOfWeek.SATURDAY ||
        candidat.dayOfWeek == DayOfWeek.SUNDAY ||
        !candidat.isAfter(maintenant.plusMinutes(1))
    ) {
        candidat = candidat.plusDays(1)
    }
    return Duration.between(maintenant, candidat).toMillis()
}

fun planifierMenu13h(ctx: Context, actif: Boolean) {
    val wm = WorkManager.getInstance(ctx)
    if (!actif) {
        wm.cancelUniqueWork(W_MENU)
        return
    }
    val req = PeriodicWorkRequestBuilder<MenuWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delaiProchain13hOuvre(), TimeUnit.MILLISECONDS)
        .setConstraints(reseau)
        .build()
    wm.enqueueUniquePeriodicWork(W_MENU, ExistingPeriodicWorkPolicy.UPDATE, req)
}

/** Vérifie le stock toutes les `heures` + un passage immédiat à l'activation. */
fun planifierStock(ctx: Context, actif: Boolean, heures: Long) {
    val wm = WorkManager.getInstance(ctx)
    if (!actif) {
        wm.cancelUniqueWork(W_STOCK)
        return
    }
    wm.enqueueUniqueWork(
        "$W_STOCK-immediat",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<StockWorker>().setConstraints(reseau).build(),
    )
    val req = PeriodicWorkRequestBuilder<StockWorker>(heures, TimeUnit.HOURS)
        .setConstraints(reseau)
        .build()
    wm.enqueueUniquePeriodicWork(W_STOCK, ExistingPeriodicWorkPolicy.UPDATE, req)
}

/** Tous les jours ouvrés à 8h : y a-t-il une commande à retirer aujourd'hui ? */
fun planifierRdv(ctx: Context, actif: Boolean) {
    val wm = WorkManager.getInstance(ctx)
    if (!actif) {
        wm.cancelUniqueWork(W_RDV)
        return
    }
    val req = PeriodicWorkRequestBuilder<RdvWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delaiProchain8hOuvre(), TimeUnit.MILLISECONDS)
        .setConstraints(reseau)
        .build()
    wm.enqueueUniquePeriodicWork(W_RDV, ExistingPeriodicWorkPolicy.UPDATE, req)
}

/** Suivi du retrait : un passage toutes les 15 min entre le début et la fin. */
fun planifierSuiviRetrait(ctx: Context, numero: String, debutMinutes: Int, finMinutes: Int) {
    val wm = WorkManager.getInstance(ctx)
    val maintenant = minutesParis()
    val data = androidx.work.workDataOf(
        "numero" to numero,
        "debut" to debutMinutes,
        "fin" to finMinutes,
    )
    val delai = ((debutMinutes - maintenant).coerceAtLeast(0)).toLong()
    val req = OneTimeWorkRequestBuilder<PickupWorker>()
        .setInitialDelay(delai, TimeUnit.MINUTES)
        .setInputData(data)
        .build()
    wm.enqueueUniqueWork("suivi-$numero", ExistingWorkPolicy.REPLACE, req)
}

private fun minutesParis(): Int {
    val z = ZonedDateTime.now(ZoneId.of("Europe/Paris"))
    return z.hour * 60 + z.minute
}
