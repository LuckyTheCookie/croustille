package fr.croustille

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.croustille.data.Prefs
import fr.croustille.ui.App
import fr.croustille.ui.CroustilleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        afficher()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        afficher()
    }

    private fun afficher() {
        val prefs = Prefs(applicationContext)
        val i = intent
        // Les notifs (ex: suivi du retrait) peuvent demander un onglet précis.
        val onglet = i?.getIntExtra("onglet", 0) ?: 0
        // Lien d'activation Izly reçu par SMS.
        val activation = i?.dataString?.takeIf { "/tools/Activation" in it }
        setContent { CroustilleTheme { App(prefs, onglet, activation) } }
    }
}
