package fr.croustille.ui.menus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.croustille.data.CroustillantApi
import fr.croustille.data.FONDERIE_CODE
import fr.croustille.data.MenuJour
import fr.croustille.data.cuisineCode
import fr.croustille.data.formulesMidi
import fr.croustille.data.formulesSoir
import fr.croustille.data.isCommandable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val apiFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val jourFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

private fun MenuJour.localDate(): LocalDate = LocalDate.parse(date, apiFmt)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MenusScreen(
    api: CroustillantApi,
    selection: Int,
    titreLieu: String,
    sousTitreLieu: String,
    onCommander: () -> Unit,
    onChangerLieu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menus by remember(selection) { mutableStateOf<List<MenuJour>?>(null) }
    var erreur by remember(selection) { mutableStateOf(false) }
    var midi by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun charger() {
        erreur = false
        try {
            menus = api.menusAVenir(cuisineCode(selection)).data.sortedBy { it.localDate() }
        } catch (e: Exception) {
            menus = emptyList(); erreur = true
        }
    }
    LaunchedEffect(selection) { charger() }

    val jours = menus ?: emptyList()
    val pager = rememberPagerState(pageCount = { jours.size })
    val chipsState = rememberLazyListState()
    LaunchedEffect(pager, jours.size) {
        snapshotFlow { pager.currentPage }.collectLatest { page ->
            if (jours.isNotEmpty()) chipsState.animateScrollToItem(page)
        }
    }

    Column(modifier.fillMaxSize()) {
        // Hero compact (toucher = changer de lieu)
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface),
                    ),
                )
                .clickable(onClick = onChangerLieu)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.LunchDining, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titreLieu, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(sousTitreLieu, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                IconButton(onClick = { menus = null; scope.launch { charger() } }) {
                    Icon(Icons.Default.Refresh, "Recharger")
                }
            }
        }

        if (menus == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Chargement des menus…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Column
        }

        if (jours.isEmpty()) {
            EmptyMenus(erreur, onCommander)
            return@Column
        }

        // Sélecteur de jour (synchro avec le swipe)
        LazyRow(
            state = chipsState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(jours, key = { _, j -> j.code }) { i, j ->
                val d = j.localDate()
                FilterChip(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                d.format(DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    },
                )
            }
        }

        // Toggle Midi / Soir
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = midi, onClick = { midi = true }, label = { Text("Midi") }, leadingIcon = { Icon(Icons.Default.LunchDining, null, modifier = Modifier.size(18.dp)) })
            FilterChip(selected = !midi, onClick = { midi = false }, label = { Text("Soir") }, leadingIcon = { Icon(Icons.Default.DinnerDining, null, modifier = Modifier.size(18.dp)) })
        }

        // Pages swipeables : un jour par page
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { menus = null; scope.launch { charger() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 12.dp), pageSpacing = 8.dp, modifier = Modifier.fillMaxSize()) { page ->
                val jour = jours[page]
                JourPage(jour, midi, isCommandable(selection), onCommander)
            }
        }
    }
}

@Composable
private fun JourPage(jour: MenuJour, midi: Boolean, commandable: Boolean, onCommander: () -> Unit) {
    val formules = if (midi) jour.formulesMidi() else jour.formulesSoir()
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            AnimatedContent(targetState = jour.date) { _ ->
                Column {
                    Text(
                        jour.localDate().format(jourFmt).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        if (midi) "Déjeuner · 2 formules au choix" else "Dîner",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        if (formules.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pas de service ce jour-là (ou réservations passées). Glisse pour voir un autre jour.")
                    }
                }
            }
        } else {
            itemsIndexed(formules) { i, plats ->
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { 30 }) {
                    FormuleCard(numero = i + 1, plats = plats, midi = midi)
                }
            }
            if (midi && commandable) {
                item {
                    Button(onClick = onCommander, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Commander ce menu à 1 €")
                    }
                }
            }
        }
    }
}

@Composable
private fun FormuleCard(numero: Int, plats: List<String>, midi: Boolean) {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (midi) Icons.Default.LunchDining else Icons.Default.DinnerDining,
                        null, tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Formule $numero", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            plats.forEach { p ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Text("•  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(p, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EmptyMenus(erreur: Boolean, onCommander: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Cake, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                if (erreur) "Impossible de charger" else "Aucun menu affiché",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                "C'est souvent que les réservations sont passées : reviens plus tard. La boutique reste accessible.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            Button(onClick = onCommander) {
                Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Voir la boutique Fonderie")
            }
            AssistChip(
                onClick = {},
                label = { Text("Cutoff : commande avant 8h00") },
                leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}
