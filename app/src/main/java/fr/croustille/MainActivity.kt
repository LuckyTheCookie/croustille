package fr.croustille

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.croustille.data.Prefs
import fr.croustille.ui.App
import fr.croustille.ui.CroustilleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(applicationContext)
        setContent { CroustilleTheme { App(prefs) } }
    }
}
