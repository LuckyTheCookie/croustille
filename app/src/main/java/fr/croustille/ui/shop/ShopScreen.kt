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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import fr.croustille.data.StoreApi
import fr.croustille.data.StoreProduct
import fr.croustille.data.choixDessert
import fr.croustille.data.choixMenu
import fr.croustille.data.prixAffiche
import fr.croustille.ui.onboarding.StaggeredIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShopScreen(
    store: StoreApi,
    cart: CartRepo,
    commandable: Boolean,
    onCompte: () -> Unit,
    onPanierWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var products by remember { mutableStateOf<List<StoreProduct>?>(null) }
    var fiche by remember { mutableStateOf<StoreProduct?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        products = try { store.products() } catch (e: Exception) { emptyList() }
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
            } else if (products!!.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("Boutique vide : les réservations sont passées, reviens plus tard (avant 8h00).", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    val r = snack.showSnackbar(msg, actionLabel = if (versPanier) "Panier web" else null)
                    if (r == SnackbarResult.ActionPerformed) onPanierWeb()
                }
            },
            cart = cart,
        )
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
