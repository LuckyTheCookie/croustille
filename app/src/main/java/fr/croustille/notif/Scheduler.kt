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

private val reseau = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

/** Prochain 13h00 un jour ouvré (heure de Paris). */
private fun delaiProchain13hOuvre(): Long {
    var candidat = ZonedDateTime.now(ZoneId.of("Europe/Paris"))
        .withHour(13).withMinute(0).withSecond(0).withNano(0)
    while (candidat.dayOfWeek == DayOfWeek.SATURDAY ||
        candidat.dayOfWeek == DayOfWeek.SUNDAY ||
        !candidat.isAfter(ZonedDateTime.now(ZoneId.of("Europe/Paris")).plusMinutes(1))
    ) {
        candidat = candidat.plusDays(1)
    }
    return Duration.between(ZonedDateTime.now(ZoneId.of("Europe/Paris")), candidat).toMillis()
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
