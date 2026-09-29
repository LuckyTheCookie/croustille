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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.LaunchedEffect
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
import fr.croustille.notif.annulerRetrait
import fr.croustille.notif.planifierMenu13h
import fr.croustille.notif.planifierRdv
import fr.croustille.notif.planifierStock
import fr.croustille.notif.planifierSuiviRetrait
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
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
                    Text("Connexions et rappels", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Section("CrousAndGo") {
                SessionCrous(auth = auth, onPaiement = onPaiement)
            }
        }
        item {
            Section("Izly") {
                CarteIzly(izly = izly)
            }
        }
        item {
            Section("Rappels") {
                CarteRappels(prefs = prefs)
            }
        }
        item {
            Text(
                "Astuce : touche le bandeau du restaurant dans l'onglet Menus pour changer de lieu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Section("Zone test") {
                ZoneTest(prefs = prefs)
            }
        }
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            titre.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        contenu()
    }
}

// ---------------------------------------------------------------------------
// Session CrousAndGo
// ---------------------------------------------------------------------------
@Composable
private fun SessionCrous(auth: WpAuth, onPaiement: () -> Unit) {
    var log by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var connecte by remember { mutableStateOf(auth.hasSession()) }
    var msg by remember {
        mutableStateOf(
            if (auth.hasSession()) "Session active — tes commandes se synchronisent toutes seules."
            else "Connecte-toi pour commander et voir tes commandes.",
        )
    }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Si la session a expiré mais que les identifiants sont mémorisés, on reconnecte tout seul.
    LaunchedEffect(Unit) {
        if (!connecte) {
            try {
                if (auth.assurerSession()) {
                    connecte = true
                    msg = "Reconnecté automatiquement — bon retour !"
                }
            } catch (e: Exception) {
                // Reste en mode déconnecté.
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            if (connecte) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LigneStatut(
                ok = connecte,
                texte = if (connecte) "Connecté" else "Déconnecté",
            )
            if (!connecte) {
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
                                    "Session active — reconnexion auto si la boutique te déconnecte."
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
                Text(
                    "Mot de passe chiffré sur l'appareil : reconnexion automatique.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
                        msg = "Connecte-toi pour commander et voir tes commandes."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Se déconnecter")
                }
            }
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LigneStatut(ok: Boolean, texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(CircleShape)
                .background(if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(8.dp))
        Text(texte, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------
// Izly : connexion directe web (sans SMS) -> solde.
// ---------------------------------------------------------------------------
@Composable
private fun CarteIzly(izly: IzlyRepo) {
    var id by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var solde by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val lie = izly.aDesIdentifiants()
    val scope = rememberCoroutineScope()

    suspend fun charger(nouvelId: String, nouveauPin: String) {
        busy = true
        erreur = null
        val r = izly.solde(nouvelId, nouveauPin)
        busy = false
        if (r.isSuccess) solde = r.getOrNull()
        else erreur = r.exceptionOrNull()?.message
    }
    // Auto-refresh silencieux si identifiants mémorisés.
    LaunchedEffect(Unit) {
        if (lie) {
            val r = izly.soldeSauve()
            if (r?.isSuccess == true) solde = r.getOrNull()
            else erreur = r?.exceptionOrNull()?.message
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
                    Text(
                        if (solde != null) "Compte lié" else "Recharge et paiements CROUS",
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    erreur = null
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
                    "Connexion directe, sans SMS. Identifiants chiffrés sur l'appareil.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rappels (refonte : lignes aérées, chips qui passent à la ligne)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CarteRappels(prefs: Prefs) {
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Spacer(Modifier.height(6.dp))

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
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 4.dp),
            )
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
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 4.dp),
            )
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
                Spacer(Modifier.height(4.dp))
                Text("Vérifier", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { s ->
                        FilterChip(
                            selected = seuil == s,
                            onClick = { scope.launch { prefs.setStockSeuil(s) } },
                            label = { Text("$s menus") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(14.dp).padding(top = 2.dp))
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
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

@Composable
private fun ZoneTest(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val appCtx = LocalContext.current.applicationContext
    var message by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Pour tester les rappels sans attendre demain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        prefs.setRdvRetrait(true)
                        planifierRdv(appCtx, true)
                        androidx.work.WorkManager.getInstance(appCtx).enqueueUniqueWork(
                            "rdv-test",
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            androidx.work.OneTimeWorkRequestBuilder<fr.croustille.notif.RdvWorker>()
                                .setConstraints(
                                    androidx.work.Constraints.Builder()
                                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                        .build(),
                                )
                                .build(),
                        )
                        message = "Détection lancée : regarde tes notifs."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Simuler : commande trouvée (détection 8h)")
            }
            OutlinedButton(
                onClick = {
                    val z = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Paris"))
                    val maintenant = z.hour * 60 + z.minute
                    planifierSuiviRetrait(appCtx, "#TEST", maintenant, maintenant + 120)
                    message = "Suivi lancé : notif persistante avec barre de progression."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Simuler : il est 11h30 (suivi retrait)")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        prefs.setMenu13h(false)
                        prefs.setRdvRetrait(false)
                        prefs.setStockOn(false)
                        planifierMenu13h(appCtx, false)
                        planifierRdv(appCtx, false)
                        planifierStock(appCtx, false, 4)
                        androidx.work.WorkManager.getInstance(appCtx).cancelAllWork()
                        annulerRetrait(appCtx)
                        message = "Tout arrêté : notifs coupées et suivis annulés."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tout arrêter (notifs + suivis)")
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
