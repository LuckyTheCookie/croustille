package fr.croustille.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.croustille.data.IzlyRepo

/** Ouvert via le lien SMS : finalise la liaison du compte Izly (usage unique). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivationIzlyScreen(izly: IzlyRepo, url: String, onTermine: () -> Unit) {
    var etat by remember(url) { mutableStateOf("cours") }
    var erreur by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        val r = izly.activer(url)
        if (r.isSuccess) etat = "ok"
        else {
            etat = "ko"
            erreur = r.exceptionOrNull()?.message
        }
    }

    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (etat) {
                "cours" -> {
                    LoadingIndicator()
                    Text("Activation de ton compte Izly…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Ne ferme pas l'application.", style = MaterialTheme.typography.bodySmall)
                }
                "ok" -> {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(64.dp))
                    Text("Compte Izly lié !", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Ton solde s'affichera dans l'onglet Compte.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onTermine) { Text("C'est parti") }
                }
                else -> {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                    Text("Activation impossible", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(erreur ?: "Lien invalide ou expiré.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        etat = "cours"
                        erreur = null
                    }) { Text("Réessayer") }
                    OutlinedButton(onClick = onTermine) { Text("Plus tard") }
                }
            }
            if (etat == "cours") CircularProgressIndicator(modifier = Modifier.size(0.dp))
        }
    }
}
