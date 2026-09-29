package fr.croustille.notif

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Point d'ancrage Glyph (Nothing).
 *
 * État actuel : le SDK classique "Ketchum" (bandes lumineuses, `displayProgress`)
 * n'est plus distribué par Nothing — seul le SDK Matrix (Phone 3) est public,
 * et il est impossible à valider sans l'appareil en main.
 *
 * En attendant :
 * - la notif de retrait est "ongoing + progress + silencieuse" : c'est exactement
 *   ce que Nothing OS exploite pour ses "Essential notifications" (à activer par
 *   l'utilisateur dans Réglages > Interface Glyph > Notifications essentielles).
 * - dès qu'on connaît le modèle du téléphone, on branche ici le bon SDK :
 *   Phone (3) -> GlyphMatrix SDK (`setAppMatrixFrame`), bandes lumineuses ->
 *   Ketchum `displayProgress(frame, progress)` depuis l'app au premier plan.
 */
object Glyph {
    private const val TAG = "Glyph"

    fun estNothing(): Boolean =
        Build.MANUFACTURER.equals("Nothing", ignoreCase = true)

    /** Progression 0..100, ou -1 pour éteindre. Best effort, jamais de crash. */
    fun miroir(ctx: Context, progression: Int) {
        if (!estNothing()) return
        try {
            Log.d(TAG, "miroir($progression) — SDK non branché, en attente du modèle")
        } catch (e: Exception) {
            Log.w(TAG, "glyph indisponible", e)
        }
    }
}
