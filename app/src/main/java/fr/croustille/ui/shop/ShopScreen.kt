package fr.croustille.ui.shop

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.croustille.data.CartRepo
import fr.croustille.data.IzlyRepo
import fr.croustille.data.Panier
import fr.croustille.data.StoreApi
import fr.croustille.data.StoreProduct
import fr.croustille.data.centimesVersEuros
import fr.croustille.data.choixDessert
import fr.croustille.data.choixMenu
import fr.croustille.data.estUrlPaiementIzly
import fr.croustille.data.prixAffiche
import fr.croustille.ui.onboarding.StaggeredIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShopScreen(
    store: StoreApi,
    cart: CartRepo,
    izly: IzlyRepo,
    commandable: Boolean,
    onCompte: () -> Unit,
    onPanierWeb: () -> Unit,
    onCommandePayee: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var products by remember { mutableStateOf<List<StoreProduct>?>(null) }
    var fiche by remember { mutableStateOf<StoreProduct?>(null) }
    var panier by remember { mutableStateOf<Panier?>(null) }
    var urlPaiement by remember { mutableStateOf<String?>(null) }
    var checkoutBusy by remember { mutableStateOf(false) }
    var payBusy by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun chargerPanier() {
        panier = try {
            cart.contenu().takeIf { it.items.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
    suspend fun dire(msg: String) { snack.showSnackbar(msg) }

    LaunchedEffect(Unit) {
        products = try { store.products() } catch (e: Exception) { emptyList() }
        chargerPanier()
    }

    Scaffold(modifier, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Commander", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Crous & Go' · Fonderie · retrait 11h45-13h30", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!commandable) {
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text("La commande n'est disponible que pour la Fonderie.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (products == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    panier?.let { p ->
                        item {
                            CartePanier(
                                panier = p,
                                busy = checkoutBusy,
                                onPayer = {
                                    val creds = izly.lireIdentifiants()
                                    if (creds == null) {
                                        scope.launch {
                                            snack.showSnackbar("Lie ton compte Izly dans l'onglet Compte.")
                                            onCompte()
                                        }
                                        return@CartePanier
                                    }
                                    checkoutBusy = true
                                    scope.launch {
                                        val r = cart.commander()
                                        checkoutBusy = false
                                        if (r.isFailure) {
                                            val choix = snack.showSnackbar(
                                                r.exceptionOrNull()?.message ?: "Checkout impossible.",
                                                actionLabel = "Finir sur le site",
                                            )
                                            if (choix == SnackbarResult.ActionPerformed) onPanierWeb()
                                        } else {
                                            val redirect = r.getOrNull()!!.first
                                            if (redirect.estUrlPaiementIzly()) {
                                                urlPaiement = redirect
                                            } else {
                                                // La boutique renvoie ailleurs (ex page order-pay) : finir sur le site.
                                                scope.launch {
                                                    dire("Suite du paiement sur le site.")
                                                    onPanierWeb()
                                                }
                                            }
                                        }
                                    }
                                },
                                onVoirSite = onPanierWeb,
                            )
                        }
                    }
                    if (products!!.isEmpty()) {
                        item {
                            Text(
                                "Boutique vide : les réservations sont passées, reviens plus tard (avant 8h00).",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    itemsIndexed(products!!, key = { _, p -> p.id }) { i, p ->
                        StaggeredIn(i) { ProductCard(p, onChoisir = { fiche = p }) }
                    }
                }
            }
        }
    }

    fiche?.let { p ->
        FicheProduit(
            produit = p,
            onFermer = { fiche = null },
            onAjoute = { msg, versPanier ->
                scope.launch {
                    chargerPanier()
                    val r = snack.showSnackbar(msg, actionLabel = if (versPanier) "Panier web" else null)
                    if (r == SnackbarResult.ActionPerformed) onPanierWeb()
                }
            },
            cart = cart,
        )
    }

    urlPaiement?.let { url ->
        SheetPaiement(
            montant = panier?.let { centimesVersEuros(it.total) } ?: "",
            busy = payBusy,
            onFermer = {
                if (payBusy) return@SheetPaiement
                // La commande existe déjà côté boutique (en attente) : le token est
                // à usage unique, on le jette. Repayer = repasser par "Payer".
                urlPaiement = null
                scope.launch { dire("Commande créée : retrouve-la dans Mes commandes pour payer.") }
            },
            onConfirmer = {
                val creds = izly.lireIdentifiants()
                if (creds == null) {
                    urlPaiement = null
                    onCompte()
                    return@SheetPaiement
                }
                payBusy = true
                scope.launch {
                    val r = cart.payerIzly(url, creds.first, creds.second)
                    payBusy = false
                    urlPaiement = null // token consommé dans tous les cas : anti-rejeu
                    if (r.isFailure) {
                        val choix = snack.showSnackbar(
                            r.exceptionOrNull()?.message ?: "Paiement refusé.",
                            actionLabel = "Site web",
                        )
                        if (choix == SnackbarResult.ActionPerformed) onPanierWeb()
                    } else {
                        // Preuve de paiement : le panier doit être vide côté boutique.
                        val vide = try {
                            cart.contenu().items.isEmpty()
                        } catch (e: Exception) {
                            false
                        }
                        chargerPanier()
                        if (vide) {
                            scope.launch { dire("Payé ! N° dans l'onglet Commandes.") }
                        } else {
                            scope.launch { dire("Paiement envoyé : vérifie le statut dans Mes commandes.") }
                        }
                        onCommandePayee()
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartePanier(
    panier: Panier,
    busy: Boolean,
    onPayer: () -> Unit,
    onVoirSite: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Panier (${panier.items.sumOf { it.quantity }})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            panier.items.forEach { l ->
                Row {
                    Text(
                        "${l.name} × ${l.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        centimesVersEuros(l.ligneTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    centimesVersEuros(panier.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "1 repas midi + 1 repas soir max (pas deux midis).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPayer, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Payer ${centimesVersEuros(panier.total)}")
                }
                OutlinedButton(onClick = onVoirSite, enabled = !busy) { Text("Site") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetPaiement(
    montant: String,
    busy: Boolean,
    onFermer: () -> Unit,
    onConfirmer: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Payer avec Izly ?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Montant : $montant. Croustille valide la commande (CGV cochées) puis paie avec ton compte Izly mémorisé.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onConfirmer, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Valider et payer $montant")
            }
            OutlinedButton(onClick = onFermer, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Annuler")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(p: StoreProduct, onChoisir: () -> Unit) {
    Card(
        onClick = onChoisir,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.LunchDining, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    p.prixAffiche(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                p.choixMenu().forEach { choix ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(choix, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text("Dessert au choix") }, leadingIcon = { Icon(Icons.Default.Cake, null, modifier = Modifier.size(14.dp)) })
                    AssistChip(onClick = onChoisir, label = { Text("Composer") })
                }
                val desserts = p.choixDessert("Choix 1")
                if (desserts.isNotEmpty()) {
                    Text(
                        "Douceur : " + desserts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicheProduit(
    produit: StoreProduct,
    cart: CartRepo,
    onFermer: () -> Unit,
    onAjoute: (String, Boolean) -> Unit,
) {
    val menus = produit.choixMenu()
    val d1 = produit.choixDessert("Choix 1")
    val d2 = produit.choixDessert("Choix 2")
    var selMenu by remember { mutableStateOf(menus.firstOrNull() ?: "") }
    var sel1 by remember { mutableStateOf(d1.firstOrNull() ?: "") }
    var sel2 by remember { mutableStateOf(d2.firstOrNull() ?: "") }
    var ajout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                Text(produit.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    produit.prixAffiche() + " · quantités limitées",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
            }
            item {
                Text("Ton plat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(menus.size) { i ->
                OptionRadio(menus[i], menus[i] == selMenu) { selMenu = menus[i] }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Dessert 1", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(d1.size) { i ->
                OptionRadio(d1[i], d1[i] == sel1) { sel1 = d1[i] }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Dessert 2", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(d2.size) { i ->
                OptionRadio(d2[i], d2[i] == sel2) { sel2 = d2[i] }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Retrait Fonderie · 11h45-13h30 · commande avant 8h00", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        ajout = true
                        scope.launch {
                            val r = cart.ajouterProduit(produit, selMenu, sel1, sel2)
                            ajout = false
                            if (r.isSuccess) {
                                onFermer()
                                onAjoute("Menu ajouté au panier !", true)
                            } else {
                                onAjoute("Ajout direct impossible (${r.exceptionOrNull()?.message}). Connecte-toi ou finalise sur le site.", true)
                            }
                        }
                    },
                    enabled = !ajout && selMenu.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (ajout) "Ajout…" else "Ajouter au panier · ${produit.prixAffiche()}")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun OptionRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
