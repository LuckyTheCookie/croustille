package fr.croustille.ui.home

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.croustille.data.CartRepo
import fr.croustille.data.CroustillantApi
import fr.croustille.data.FONDERIE_CODE
import fr.croustille.data.IzlyRepo
import fr.croustille.data.OrdersRepo
import fr.croustille.data.PersistentCookieJar
import fr.croustille.data.Prefs
import fr.croustille.data.StoreApi
import fr.croustille.data.WpAuth
import fr.croustille.data.fonderieEntry
import fr.croustille.data.isCommandable
import fr.croustille.ui.account.AccountScreen
import fr.croustille.ui.menus.MenusScreen
import fr.croustille.ui.orders.OrdersScreen
import fr.croustille.ui.shop.ShopScreen

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
                commandable = commandable,
                onCompte = { onglet = 3 },
                onPanierWeb = { rootNav.navigate("paiement") },
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

@Composable
fun PaiementScreen(jar: PersistentCookieJar) {
    // La WebView a sa propre session : on y injecte les cookies de l'app
    // (login WordPress + panier Woo) avant de charger la page.
    // Seule WebView de l'app : paiement Izly (3DS / redirection). Tout le reste est natif.
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                val cm = android.webkit.CookieManager.getInstance()
                cm.setAcceptCookie(true)
                for (c in jar.cookiesFor("https://crousandgo.crous-strasbourg.fr/fonderie/panier/")) {
                    val valeur = buildString {
                        append("${c.name}=${c.value}")
                        if (!c.hostOnly) append("; Domain=${c.domain}")
                        append("; Path=${c.path}")
                    }
                    cm.setCookie("https://crousandgo.crous-strasbourg.fr", valeur)
                }
                cm.flush()
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl("https://crousandgo.crous-strasbourg.fr/fonderie/panier/")
            }
        },
    )
}
