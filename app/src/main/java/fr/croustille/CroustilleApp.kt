package fr.croustille

import android.app.Application
import fr.croustille.data.Prefs
import fr.croustille.notif.planifierMenu13h
import fr.croustille.notif.planifierRdv
import fr.croustille.notif.planifierStock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Replanifie les rappels au démarrage (dont après un reboot : les one-shots ne survivent pas). */
class CroustilleApp : Application() {
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        portee.launch {
            try {
                val prefs = Prefs(this@CroustilleApp)
                if (prefs.menu13h.first()) planifierMenu13h(this@CroustilleApp, true)
                if (prefs.rdvRetrait.first()) planifierRdv(this@CroustilleApp, true)
                if (prefs.stockOn.first()) {
                    planifierStock(this@CroustilleApp, true, prefs.stockHours.first().toLong())
                }
            } catch (e: Exception) {
                android.util.Log.w("CroustilleApp", "replanification", e)
            }
        }
    }
}
