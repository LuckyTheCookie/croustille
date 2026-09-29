package fr.croustille.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.croustille.data.CroustillantApi
import fr.croustille.data.FONDERIE_CODE
import fr.croustille.data.Prefs
import fr.croustille.data.Region
import fr.croustille.data.Restaurant
import fr.croustille.data.STRASBOURG_CODE
import fr.croustille.data.fonderieEntry
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Étape 1 : choisir son CROUS
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrousScreen(api: CroustillantApi, prefs: Prefs, onCrous: (Int) -> Unit) {
    var regions by remember { mutableStateOf<List<Region>?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        regions = try {
            api.regions().data.sortedBy { it.libelle }
        } catch (e: Exception) {
            listOf(Region(STRASBOURG_CODE, "Strasbourg"))
        }
    }
    Scaffold { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OnboardingHero(
                titre = "Ton CROUS,",
                accent = "tes menus.",
                sousTitre = "Choisis ton CROUS pour voir les restos et leurs plats du jour.",
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher un CROUS") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (regions == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() }
            } else {
                val filtrees = regions!!.filter { it.libelle.contains(query, ignoreCase = true) }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(filtrees, key = { _, r -> r.code }) { i, r ->
                        StaggeredIn(i) {
                            Card(
                                onClick = { onCrous(r.code) },
                                colors = if (r.code == STRASBOURG_CODE)
                                    CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                                else CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Crous", style = MaterialTheme.typography.labelMedium)
                                        Text(r.libelle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.ArrowForward, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Étape 2 : choisir son lieu (Fonderie épinglée en tête pour Strasbourg)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestosScreen(
    api: CroustillantApi,
    prefs: Prefs,
    regionCode: Int,
    regionNom: String,
    onChoisi: () -> Unit,
) {
    var restos by remember { mutableStateOf<List<Restaurant>?>(null) }
    var nomRegion by remember(regionNom) { mutableStateOf(regionNom) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(regionCode) {
        if (nomRegion.isBlank()) {
            try {
                nomRegion = api.regions().data.firstOrNull { it.code == regionCode }?.libelle ?: ""
            } catch (e: Exception) { /* garde vide */ }
        }
        restos = try {
            val liste = api.restaurantsDeRegion(regionCode).data
                .filter { it.nom.isNotBlank() }
                .sortedWith(compareBy({ !it.ouvert }, { it.nom }))
            if (regionCode == STRASBOURG_CODE) listOf(fonderieEntry()) + liste else liste
        } catch (e: Exception) {
            if (regionCode == STRASBOURG_CODE) listOf(fonderieEntry()) else emptyList()
        }
    }
    Scaffold { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OnboardingHero(
                titre = if (nomRegion.isBlank()) "Où manges-tu ?" else "Crous $nomRegion,",
                accent = if (nomRegion.isBlank()) "" else "où manges-tu ?",
                sousTitre = if (regionCode == STRASBOURG_CODE)
                    "La Fonderie est en tête : menus de l'Illberg, retrait sur place."
                else "Sélectionne ton restaurant habituel.",
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher un lieu") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (restos == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                val filtres = restos!!.filter {
                    it.nom.contains(query, ignoreCase = true) ||
                        (it.adresse ?: "").contains(query, ignoreCase = true)
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(filtres, key = { _, r -> r.code }) { i, r ->
                        val fonderie = r.code == FONDERIE_CODE
                        StaggeredIn(i) {
                            Card(
                                onClick = {
                                    if (busy) return@Card
                                    busy = true
                                    scope.launch {
                                        prefs.choisirResto(regionCode, r.code)
                                        onChoisi()
                                    }
                                },
                                colors = if (fonderie)
                                    CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                                else CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(48.dp).clip(CircleShape)
                                            .background(
                                                if (fonderie) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondaryContainer,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (fonderie) Icons.Default.ShoppingBag else Icons.Default.LunchDining,
                                            null,
                                            tint = if (fonderie) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        if (fonderie) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CheckCircle, null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "RECOMMANDÉ · COMMANDABLE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Text(r.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        r.adresse?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(2.dp))
                                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                        }
                                        (r.zone ?: "")?.takeIf { it.isNotBlank() }?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(2.dp))
                                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.ArrowForward, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingHero(titre: String, accent: String, sousTitre: String) {
    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.LunchDining, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Croustille", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(14.dp))
            Text(titre, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text(accent, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(sousTitre, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn() + slideInVertically { 40 + index * 8 },
    ) { content() }
}
