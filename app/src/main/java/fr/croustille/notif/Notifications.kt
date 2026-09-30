package fr.croustille.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import fr.croustille.MainActivity
import fr.croustille.R

private const val CANAL = "croustille_rappels"
private const val CANAL_RETRAIT = "croustille_retrait"

fun canaux(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    if (nm.getNotificationChannel(CANAL) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Rappels Croustille", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Menus du lendemain, stock bas, commandes détectées."
            },
        )
    }
    if (nm.getNotificationChannel(CANAL_RETRAIT) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CANAL_RETRAIT, "Suivi du retrait", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Barre de progression pendant le créneau de retrait."
            },
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
    val intent = Intent(ctx, MainActivity::class.java)
        .putExtra("onglet", 2)
        .putExtra("notif_id", codeRequete)
        .setAction("fr.croustille.NOTIF_$codeRequete")
    return PendingIntent.getActivity(
        ctx, codeRequete, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

fun notifierMenus(ctx: Context, etiquetteJour: String, resume: String) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(
        1,
        base(ctx)
            .setContentTitle("$etiquetteJour, c'est dispo !")
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
private const val ECHELLE = 1000
private const val TERRACOTTA = 0xFFA63A00L
private const val GRIS = 0xFFB0AA9FL

/** Construit la notif de suivi façon Reef : ProgressStyle + ongoing + CATEGORY_PROGRESS. */
fun construireNotifRetrait(
    ctx: Context,
    numero: String,
    finTexte: String,
    avancement: Int, // 0..100
    finMillis: Long,
    notifId: Int = 5,
): android.app.Notification {
    canaux(ctx)
    val ecoule = (avancement.coerceIn(0, 100) * ECHELLE / 100)
    val style = NotificationCompat.ProgressStyle()
        .setStyledByProgress(false)
        .setProgress(ecoule)
    if (ecoule > 0) {
        style.addProgressSegment(
            NotificationCompat.ProgressStyle.Segment(ecoule).setColor(TERRACOTTA.toInt()),
        )
    }
    if (ECHELLE - ecoule > 0) {
        style.addProgressSegment(
            NotificationCompat.ProgressStyle.Segment(ECHELLE - ecoule).setColor(GRIS.toInt()),
        )
    }
    style.addProgressPoint(
        NotificationCompat.ProgressStyle.Point(ECHELLE).setColor(0xFFFFFFFF.toInt()),
    )
    return NotificationCompat.Builder(ctx, CANAL_RETRAIT)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Retrait en cours · $numero")
        .setContentText("Montre ton numéro au comptoir · fin à $finTexte")
        .setContentIntent(versCommandes(ctx, notifId))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .setShowWhen(true)
        .setWhen(finMillis)
        .setStyle(style)
        .setRequestPromotedOngoing(true)
        .build()
}

/** Notif persistante avec barre de progression jusqu'à la fin du retrait. */
fun notifierRetrait(
    ctx: Context,
    numero: String,
    finTexte: String,
    avancement: Int, // 0..100
    finMillis: Long,
    notifId: Int = 5,
) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.notify(notifId, construireNotifRetrait(ctx, numero, finTexte, avancement, finMillis, notifId))
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
fun annulerRetrait(ctx: Context, id: Int = ID_RETRAIT) {
    ctx.getSystemService(NotificationManager::class.java)?.cancel(id)
}

/** Coupe toutes les notifs Croustille d'un coup. */
fun toutAnnuler(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    for (id in listOf(1, 2, 3, 4, 5, 6, 9)) nm.cancel(id)
}
