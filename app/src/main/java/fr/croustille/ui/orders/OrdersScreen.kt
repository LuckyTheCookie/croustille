package fr.croustille.ui.orders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.croustille.data.Commande
import fr.croustille.data.LigneCommande
import fr.croustille.data.OrdersRepo
import fr.croustille.data.PasConnecte
import fr.croustille.data.Retrait
import fr.croustille.data.dateRetrait
import fr.croustille.data.retraitDe
import fr.croustille.ui.onboarding.StaggeredIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrdersScreen(
    repo: OrdersRepo,
    onCompte: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var commandes by remember { mutableStateOf<List<Commande>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Commande?>(null) }
    var refresh by remember { mutableStateOf(false) }
    var retraitHero by remember { mutableStateOf<Retrait?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun charger() {
        erreur = null
        try {
            commandes = repo.commandes()
            retraitHero = null
        } catch (e: PasConnecte) {
            commandes = null
            erreur = "connecte-toi"
        } catch (e: Exception) {
            erreur = e.message ?: "Chargement impossible."
        }
    }
    LaunchedEffect(Unit) { charger() }
    // Date de retrait (et non date d'achat) pour la dernière commande.
    LaunchedEffect(commandes) {
        val premiere = commandes?.firstOrNull()
        if (premiere != null) {
            try {
                retraitHero = retraitDe(repo.detail(premiere))
            } catch (e: Exception) {
                retraitHero = null
            }
        }
    }

    BackHandler(enabled = selection != null) { selection = null }

    selection?.let { c ->
        TicketScreen(commande = c, repo = repo, onRetour = { selection = null }, modifier = modifier)
        return
    }

    Column(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mes commandes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Glisse vers le bas pour actualiser", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { scope.launch { refresh = true; charger(); refresh = false } }) {
                    Icon(Icons.Default.Refresh, "Actualiser")
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = refresh,
            onRefresh = { scope.launch { refresh = true; charger(); refresh = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                commandes == null && erreur == null -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Récupération de tes commandes…")
                        }
                    }
                }
                erreur == "connecte-toi" -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Connecte-toi pour voir tes commandes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Button(onClick = onCompte) { Text("Aller à Mon compte") }
                        }
                    }
                }
                erreur != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Oups : $erreur", textAlign = TextAlign.Center)
                            OutlinedButton(onClick = { scope.launch { charger() } }) { Text("Réessayer") }
                        }
                    }
                }
                commandes!!.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Text("Aucune commande pour l'instant — compose ton menu dans l'onglet Commander !", textAlign = TextAlign.Center)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { HeroDerniere(commandes!!.first(), retraitHero, onVoir = { selection = commandes!!.first() }) }
                        itemsIndexed(commandes!!.drop(1), key = { _, c -> c.numero + c.date }) { i, c ->
                            StaggeredIn(i) { LigneCommandeCard(c, onVoir = { selection = c }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroDerniere(c: Commande, retrait: Retrait?, onVoir: () -> Unit) {
    val presse = LocalClipboardManager.current
    Card(
        onClick = onVoir,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ConfirmationNumber, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("DERNIÈRE COMMANDE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                c.numero,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                fontSize = 64.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                texteRetrait(retrait) ?: "${c.statut} · commandée le ${c.date}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { presse.setText(AnnotatedString(c.numero)) }) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copier le numéro")
            }
        }
    }
}

/** "À retirer le mardi 29 septembre · 11h45–13h30" (ou texte brut si inanalysable). */
fun texteRetrait(r: Retrait?): String? {
    if (r == null) return null
    val quand = r.dateRetrait()?.format(
        java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH),
    )?.replaceFirstChar { it.uppercase() } ?: r.jour
    val heure = r.heure.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return "À retirer le $quand$heure"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LigneCommandeCard(c: Commande, onVoir: () -> Unit) {
    Card(
        onClick = onVoir,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.numero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${c.statut} · ${c.date}", style = MaterialTheme.typography.bodySmall)
            }
            Text(c.total, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TicketScreen(
    commande: Commande,
    repo: OrdersRepo,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lignes by remember(commande) { mutableStateOf<List<LigneCommande>?>(null) }
    var erreur by remember(commande) { mutableStateOf<String?>(null) }
    val presse = LocalClipboardManager.current
    LaunchedEffect(commande) {
        try {
            lignes = repo.detail(commande)
        } catch (e: Exception) {
            erreur = e.message
        }
    }
    BackHandler(onBack = onRetour)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ConfirmationNumber, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(
                        commande.numero,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 56.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text("Montre ce numéro au retrait", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    val retrait = lignes?.let { retraitDe(it) }
                    Text(
                        texteRetrait(retrait) ?: "${commande.statut} · ${commande.date} · ${commande.total}",
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { presse.setText(AnnotatedString(commande.numero)) }) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copier")
                    }
                }
            }
        }
        if (lignes == null && erreur == null) {
            item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } }
        }
        erreur?.let {
            item { Text("Détail indisponible : $it", style = MaterialTheme.typography.bodySmall) }
        }
        lignes?.let { ls ->
            items(ls.size) { i ->
                val l = ls[i]
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { 24 }) {
                    Card(
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Text(l.nom, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(l.total, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (l.quantite.isNotBlank()) Text(l.quantite, style = MaterialTheme.typography.bodySmall)
                            l.meta.forEach { (k, v) ->
                                Text("$k : $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onRetour, modifier = Modifier.fillMaxWidth()) { Text("Retour aux commandes") }
        }
    }
}
