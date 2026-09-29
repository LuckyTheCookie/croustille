package fr.croustille.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.croustille.MainActivity

private const val CANAL = "croustille_rappels"

fun canaux(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    if (nm.getNotificationChannel(CANAL) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Rappels Croustille", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

private fun base(ctx: Context): Notification.Builder {
    canaux(ctx)
    val intent = Intent(ctx, MainActivity::class.java)
    val pi = PendingIntent.getActivity(
        ctx, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(ctx, CANAL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pi)
        .setAutoCancel(true)
}

fun notifierMenus(ctx: Context, nbJours: Int) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        1,
        base(ctx)
            .setContentTitle("Les menus sont là !")
            .setContentText("$nbJours jours de menus dispo — commande avant 8h00.")
            .build(),
    )
}

fun notifierStockBas(ctx: Context, restants: Int, nomProduit: String) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        2,
        base(ctx)
            .setContentTitle("Vite, presque épuisé !")
            .setContentText("$nomProduit : plus que $restants menus. Ouvre l'app pour commander.")
            .build(),
    )
}
