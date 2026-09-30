package fr.croustille.ui.maj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.croustille.data.MajRepo
import fr.croustille.data.Prefs
import fr.croustille.data.ReleaseInfo
import kotlinx.coroutines.launch
import java.io.File

/** Belle popup de mise à jour : notes, téléchargement avec progression, installation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogMaj(
    info: ReleaseInfo,
    maj: MajRepo,
    prefs: Prefs,
    onFermer: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progres by remember { mutableIntStateOf(-1) }
    var apk by remember { mutableStateOf<File?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun telecharger() {
        busy = true
        erreur = null
        progres = 0
        scope.launch {
            try {
                apk = maj.telecharger(info, ctx.applicationContext) { p -> progres = p }
            } catch (e: Exception) {
                erreur = e.message ?: "Téléchargement impossible."
                progres = -1
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onFermer() },
        icon = { Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text("Croustille ${info.version} dispo !", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.notes.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                                .verticalScroll(rememberScrollState())
                                .height(120.dp),
                        )
                    }
                }
                if (progres >= 0 && apk == null && erreur == null) {
                    LinearProgressIndicator(
                        progress = { progres / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Téléchargement… $progres %", style = MaterialTheme.typography.bodySmall)
                }
                erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (apk == null) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Button(onClick = ::telecharger) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Télécharger")
                    }
                } else {
                    Button(onClick = {
                        try {
                            maj.installer(ctx, apk!!)
                            onFermer()
                        } catch (e: Exception) {
                            erreur = "Ouvre l'APK depuis tes téléchargements, ou autorise l'installation."
                        }
                    }) { Text("Installer") }
                }
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onFermer, enabled = !busy) { Text("Plus tard") }
                TextButton(
                    onClick = {
                        scope.launch {
                            prefs.setMajIgnoree(info.tag)
                            onFermer()
                        }
                    },
                    enabled = !busy,
                ) { Text("Ignorer cette version") }
            }
        },
    )
}
