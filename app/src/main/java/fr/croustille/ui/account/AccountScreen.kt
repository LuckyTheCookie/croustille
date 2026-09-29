package fr.croustille.ui.account

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.croustille.data.IzlyRepo
import fr.croustille.data.Prefs
import fr.croustille.data.WpAuth
import fr.croustille.notif.planifierMenu13h
import fr.croustille.notif.planifierRdv
import fr.croustille.notif.planifierStock
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    auth: WpAuth,
    izly: IzlyRepo,
    prefs: Prefs,
    onPaiement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var log by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var connecte by remember { mutableStateOf(auth.hasSession()) }
    var msg by remember {
        mutableStateOf(
            if (auth.hasSession()) "Session CROUS active — tes commandes se synchronisent toutes seules."
            else "Connecte-toi avec ton compte CrousAndGo pour commander.",
        )
    }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Mon compte", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(if (connecte) "Session CROUS active" else "Connexion CrousAndGo", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!connecte) {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        log, { log = it }, label = { Text("Identifiant ou e-mail") },
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        pwd, { pwd = it }, label = { Text("Mot de passe") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                msg = try {
                                    if (auth.login(log, pwd)) {
                                        connecte = true
                                        pwd = ""
                                        "Session CROUS active — tes commandes se synchronisent toutes seules."
                                    } else "Identifiants refusés — réessaie."
                                } catch (e: Exception) {
                                    "Erreur réseau : ${e.message}"
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Connexion…" else "Se connecter")
                    }
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = onPaiement, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Voir mon panier / payer (Izly)")
                    }
                    OutlinedButton(
                        onClick = {
                            auth.logout()
                            connecte = false
                            log = ""
                            msg = "Connecte-toi avec ton compte CrousAndGo pour commander."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Se déconnecter")
                    }
                }
            }
        }

        IzlyCard(izly)

        RappelsCard(prefs)

        Text(
            "Astuce : touche le bandeau du restaurant dans l'onglet Menus pour changer de lieu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IzlyCard(izly: IzlyRepo) {
    var id by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var solde by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lie by remember { mutableStateOf(izly.aDesIdentifiants()) }
    val scope = rememberCoroutineScope()

    suspend fun charger(nouveauId: String, nouveauPin: String) {
        busy = true
        erreur = null
        val r = izly.solde(nouveauId, nouveauPin)
        busy = false
        if (r.isSuccess) {
            solde = r.getOrNull()
            pin = ""
            lie = true
        } else {
            erreur = r.exceptionOrNull()?.message
        }
    }
    // Auto-refresh silencieux si identifiants mémorisés.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (lie) {
            val r = izly.soldeSauve()
            if (r?.isSuccess == true) solde = r.getOrNull()
        }
    }

    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Solde Izly", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Recharge et paiements CROUS", style = MaterialTheme.typography.bodySmall)
                }
                if (solde != null) {
                    IconButton(onClick = {
                        scope.launch {
                            busy = true
                            val r = izly.soldeSauve()
                            busy = false
                            if (r?.isSuccess == true) solde = r.getOrNull()
                            else erreur = r?.exceptionOrNull()?.message
                        }
                    }) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Icon(Icons.Default.Refresh, "Actualiser")
                    }
                }
            }

            if (solde != null) {
                Text(solde!!, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    izly.oublier()
                    solde = null
                    lie = false
                    id = ""
                }) { Text("Oublier mes identifiants Izly") }
            } else {
                OutlinedTextField(
                    id, { id = it }, label = { Text("E-mail ou mobile Izly") },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    pin, { pin = it }, label = { Text("Code secret (6 chiffres)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { scope.launch { charger(id.trim(), pin.trim()) } },
                    enabled = !busy && id.isNotBlank() && pin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Connexion…" else "Voir mon solde")
                }
                erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Identifiants chiffrés sur l'appareil, jamais envoyés ailleurs qu'à Izly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RappelsCard(prefs: Prefs) {
    val menuOn by prefs.menu13h.collectAsState(initial = false)
    val stockOn by prefs.stockOn.collectAsState(initial = false)
    val rdvOn by prefs.rdvRetrait.collectAsState(initial = false)
    val heures by prefs.stockHours.collectAsState(initial = 4)
    val seuil by prefs.stockSeuil.collectAsState(initial = 10)
    val scope = rememberCoroutineScope()
    val appCtx = LocalContext.current.applicationContext

    var actionPermis by remember { mutableStateOf<(() -> Unit)?>(null) }
    val demandePermis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) actionPermis?.invoke()
        actionPermis = null
    }
    fun avecPermis(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            actionPermis = action
            demandePermis.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Rappels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Pour ne jamais rater un repas", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(4.dp))

            RappelLigne(
                titre = "Menu du lendemain",
                sousTitre = "Chaque jour à 13h (lun–ven)",
                actif = menuOn,
                onBasculer = { v ->
                    if (!v) scope.launch {
                        prefs.setMenu13h(false)
                        planifierMenu13h(appCtx, false)
                    } else avecPermis {
                        scope.launch {
                            prefs.setMenu13h(true)
                            planifierMenu13h(appCtx, true)
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            RappelLigne(
                titre = "Heure du rendez-vous",
                sousTitre = "Détecté à 8h, suivi jusqu'à la fin du retrait",
                actif = rdvOn,
                onBasculer = { v ->
                    if (!v) scope.launch {
                        prefs.setRdvRetrait(false)
                        planifierRdv(appCtx, false)
                    } else avecPermis {
                        scope.launch {
                            prefs.setRdvRetrait(true)
                            planifierRdv(appCtx, true)
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            RappelLigne(
                titre = "Stock bas",
                sousTitre = "Prévient quand il reste peu de menus",
                actif = stockOn,
                onBasculer = { v ->
                    if (!v) scope.launch {
                        prefs.setStockOn(false)
                        planifierStock(appCtx, false, heures.toLong())
                    } else avecPermis {
                        scope.launch {
                            prefs.setStockOn(true)
                            planifierStock(appCtx, true, heures.toLong())
                        }
                    }
                },
            )

            if (stockOn) {
                Text("Vérifier", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 8).forEach { h ->
                        FilterChip(
                            selected = heures == h,
                            onClick = {
                                scope.launch {
                                    prefs.setStockHours(h)
                                    planifierStock(appCtx, true, h.toLong())
                                }
                            },
                            label = { Text(labelHeures(h)) },
                        )
                    }
                }
                Text("M'alerter sous", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { s ->
                        FilterChip(
                            selected = seuil == s,
                            onClick = { scope.launch { prefs.setStockSeuil(s) } },
                            label = { Text("$s menus") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "2–3 petites requêtes par passage, rien entre les passages. Si les notifs n'arrivent pas, désactive l'optimisation batterie pour Croustille.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun RappelLigne(titre: String, sousTitre: String, actif: Boolean, onBasculer: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (titre.contains("Stock")) Icons.Default.Warning else Icons.Default.Notifications,
                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(sousTitre, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = actif, onCheckedChange = onBasculer)
    }
}

private fun labelHeures(h: Int): String = when (h) {
    1 -> "1 h"
    2 -> "2 h"
    4 -> "4 h"
    else -> "$h h"
}
