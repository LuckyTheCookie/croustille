package fr.croustille.ui.home

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.croustille.data.CartRepo
import fr.croustille.data.CroustillantApi
import fr.croustille.data.FONDERIE_CODE
import fr.croustille.data.IzlyPay
import fr.croustille.data.IzlyRepo
import fr.croustille.data.IzlyStore
import fr.croustille.data.OrdersRepo
import fr.croustille.data.PersistentCookieJar
import fr.croustille.data.Prefs
import fr.croustille.data.StoreApi
import fr.croustille.data.WpAuth
import fr.croustille.data.cookiesDepuisWebView
import fr.croustille.data.cookiesVersWebView
import fr.croustille.data.estUrlPaiementIzly
import fr.croustille.data.fonderieEntry
import fr.croustille.data.isCommandable
import fr.croustille.ui.account.AccountScreen
import fr.croustille.ui.menus.MenusScreen
import fr.croustille.ui.orders.OrdersScreen
import fr.croustille.ui.shop.ShopScreen
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun MainScreen(
    api: CroustillantApi,
    store: StoreApi,
    auth: WpAuth,
    cart: CartRepo,
    orders: OrdersRepo,
    izly: IzlyRepo,
    prefs: Prefs,
    selection: Int,
    rootNav: NavController,
    ongletInitial: Int = 0,
) {
    var onglet by remember(selection) { mutableIntStateOf(ongletInitial.coerceIn(0, 3)) }
    val commandable = isCommandable(selection)
    var titreLieu by remember(selection) { mutableStateOf(if (selection == FONDERIE_CODE) fonderieEntry().nom else "…") }
    var sousTitre by remember(selection) { mutableStateOf(if (selection == FONDERIE_CODE) fonderieEntry().adresse!! else "") }
    LaunchedEffect(selection) {
        if (selection != FONDERIE_CODE) {
            try {
                val r = api.restaurant(selection).data
                titreLieu = r.nom
                sousTitre = r.adresse ?: r.zone ?: ""
            } catch (e: Exception) {
                titreLieu = "Mon restaurant"
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = onglet == 0,
                    onClick = { onglet = 0 },
                    icon = { Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(26.dp)) },
                    label = { Text("Menus") },
                )
                NavigationBarItem(
                    selected = onglet == 1,
                    onClick = { onglet = 1 },
                    icon = { Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(26.dp)) },
                    label = { Text("Commander") },
                )
                NavigationBarItem(
                    selected = onglet == 2,
                    onClick = { onglet = 2 },
                    icon = { Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(26.dp)) },
                    label = { Text("Commandes") },
                )
                NavigationBarItem(
                    selected = onglet == 3,
                    onClick = { onglet = 3 },
                    icon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(26.dp)) },
                    label = { Text("Compte") },
                )
            }
        },
    ) { pad ->
        val mod = Modifier.padding(pad)
        when (onglet) {
            0 -> MenusScreen(
                api = api,
                selection = selection,
                titreLieu = titreLieu,
                sousTitreLieu = sousTitre,
                onCommander = { onglet = 1 },
                onChangerLieu = { rootNav.navigate("crous") { popUpTo("main") { inclusive = true } } },
                modifier = mod,
            )
            1 -> ShopScreen(
                store = store,
                cart = cart,
                izly = izly,
                commandable = commandable,
                onCompte = { onglet = 3 },
                onPanierWeb = { rootNav.navigate("paiement") },
                onCommandePayee = { onglet = 2 },
                modifier = mod,
            )
            2 -> OrdersScreen(
                repo = orders,
                onCompte = { onglet = 3 },
                modifier = mod,
            )
            else -> AccountScreen(
                auth = auth,
                izly = izly,
                prefs = prefs,
                onPaiement = { rootNav.navigate("paiement") },
                modifier = mod,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaiementScreen(jar: PersistentCookieJar, client: OkHttpClient, izlyStore: IzlyStore) {
    // La WebView a sa propre session : on y injecte les cookies de l'app
    // (login WordPress + panier Woo) avant de charger la page.
    var paiementIzly by remember { mutableStateOf<String?>(null) }
    var messagePaiement by remember { mutableStateOf<String?>(null) }
    var paiementBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var vue by remember { mutableStateOf<WebView?>(null) }

    // Seule WebView de l'app : tunnel Crous + Izly. Tout le reste est natif.
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                cookiesVersWebView(jar, "https://crousandgo.crous-strasbourg.fr/fonderie/panier/")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (url.estUrlPaiementIzly()) {
                            // On récupère la session web puis on paie en natif si possible.
                            cookiesDepuisWebView(jar, view.url ?: url)
                            if (izlyStore.lireIdentifiants() != null) {
                                paiementIzly = url
                                return true
                            }
                        }
                        return false
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl("https://crousandgo.crous-strasbourg.fr/fonderie/panier/")
                vue = this
            }
        },
    )

    paiementIzly?.let { urlAutorisation ->
        ModalBottomSheet(onDismissRequest = {
            if (!paiementBusy) {
                paiementIzly = null
                vue?.loadUrl(urlAutorisation) // repli : parcours manuel sur le site
            }
        }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Payer avec Izly ?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Croustille peut valider ce paiement avec ton compte Izly mémorisé, sans ressaisir tes identifiants.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                messagePaiement?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        paiementBusy = true
                        messagePaiement = null
                        scope.launch {
                            val (id, pin) = izlyStore.lireIdentifiants()!!
                            val r = IzlyPay(client).autoriser(urlAutorisation, id, pin)
                            paiementBusy = false
                            if (r.isSuccess) {
                                paiementIzly = null
                                vue?.loadUrl(r.getOrNull()!!)
                            } else {
                                messagePaiement = r.exceptionOrNull()?.message
                            }
                        }
                    },
                    enabled = !paiementBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (paiementBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text("Payer avec mon compte Izly")
                }
                OutlinedButton(
                    onClick = {
                        paiementIzly = null
                        vue?.loadUrl(urlAutorisation)
                    },
                    enabled = !paiementBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Non, payer sur le site") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
