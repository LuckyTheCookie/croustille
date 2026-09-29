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
        // Les notifs (ex: suivi du retrait) peuvent demander un onglet précis.
        val onglet = intent?.getIntExtra("onglet", 0) ?: 0
        setContent { CroustilleTheme { App(prefs, onglet) } }
    }
}
