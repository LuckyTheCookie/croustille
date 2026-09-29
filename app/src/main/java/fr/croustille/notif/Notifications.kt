package fr.croustille.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.croustille.MainActivity

private const val CANAL = "croustille_rappels"
private const val CANAL_RETRAIT = "croustille_retrait"

fun canaux(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    if (nm.getNotificationChannel(CANAL) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Rappels Croustille", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
    if (nm.getNotificationChannel(CANAL_RETRAIT) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CANAL_RETRAIT, "Suivi du retrait", NotificationManager.IMPORTANCE_LOW),
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

/** Tap -> ouvre l'app directement sur l'onglet Commandes. */
private fun versCommandes(ctx: Context, codeRequete: Int): PendingIntent {
    val intent = Intent(ctx, MainActivity::class.java).putExtra("onglet", 2)
    return PendingIntent.getActivity(
        ctx, codeRequete, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

fun notifierMenus(ctx: Context, titreJour: String, resume: String) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        1,
        base(ctx)
            .setContentTitle("Demain : $titreJour, c'est dispo !")
            .setContentText("$resume — commande avant 8h00.")
            .setContentIntent(versCommandes(ctx, 1))
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
            .setContentIntent(versCommandes(ctx, 2))
            .build(),
    )
}

fun notifierBoutiqueVide(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        3,
        base(ctx)
            .setContentTitle("Boutique vide")
            .setContentText("Tout est parti ou les réservations sont fermées. Reviens plus tard !")
            .setContentIntent(versCommandes(ctx, 3))
            .build(),
    )
}

fun notifierCommandeDetectee(ctx: Context, numero: String, heure: String) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        4,
        base(ctx)
            .setContentTitle("Commande $numero détectée pour aujourd'hui")
            .setContentText("Je te préviendrai quand il sera l'heure ($heure).")
            .setContentIntent(versCommandes(ctx, 4))
            .build(),
    )
}

private const val ID_RETRAIT = 5

/** Notif persistante avec barre de progression jusqu'à la fin du retrait. */
fun notifierRetrait(
    ctx: Context,
    numero: String,
    finTexte: String,
    avancement: Int, // 0..100
) {
    canaux(ctx)
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        ID_RETRAIT,
        Notification.Builder(ctx, CANAL_RETRAIT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Retrait en cours · $numero")
            .setContentText("Montre ton numéro au comptoir · fin à $finTexte")
            .setContentIntent(versCommandes(ctx, 5))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, avancement, false)
            .build(),
    )
}

fun notifierBonAppetit(ctx: Context, numero: String) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.cancel(ID_RETRAIT)
    nm.notify(
        6,
        base(ctx)
            .setContentTitle("Bon appétit !")
            .setContentText("Retrait $numero terminé. À demain ?")
            .setContentIntent(versCommandes(ctx, 6))
            .build(),
    )
}

/** Coupe la notif de suivi du retrait (bouton "tout arrêter", debug). */
fun annulerRetrait(ctx: Context) {
    ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_RETRAIT)
}
