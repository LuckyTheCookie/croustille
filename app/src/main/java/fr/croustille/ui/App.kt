package fr.croustille.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.croustille.data.CartRepo
import fr.croustille.data.CookieStore
import fr.croustille.data.CroustillantApi
import fr.croustille.data.IzlyRepo
import fr.croustille.data.IzlyStore
import fr.croustille.data.OrdersRepo
import fr.croustille.data.PersistentCookieJar
import fr.croustille.data.Prefs
import fr.croustille.data.STRASBOURG_CODE
import fr.croustille.data.StoreApi
import fr.croustille.data.WpAuth
import fr.croustille.ui.account.ActivationIzlyScreen
import fr.croustille.ui.home.MainScreen
import fr.croustille.ui.home.PaiementScreen
import fr.croustille.ui.onboarding.CrousScreen
import fr.croustille.ui.onboarding.RestosScreen
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(prefs: Prefs, ongletInitial: Int = 0, activationIzly: String? = null) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val jar = remember { PersistentCookieJar(CookieStore(ctx)) }
    val client = remember { OkHttpClient.Builder().cookieJar(jar).build() }
    val api = remember { CroustillantApi.create() }
    val store = remember { StoreApi.create(client) }
    val auth = remember { WpAuth(client, jar) }
    val cart = remember { CartRepo(store, client) }
    val orders = remember { OrdersRepo(client) }
    val izlyStore = remember { IzlyStore(ctx) }
    val izly = remember { IzlyRepo(OkHttpClient.Builder().build(), izlyStore) }

    val done by prefs.onboardingDone.collectAsState(initial = null)
    val selection by prefs.restoCode.collectAsState(initial = -1)

    if (done == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() }
        return
    }

    var activation by remember(activationIzly) { mutableStateOf(activationIzly) }
    activation?.let { url ->
        ActivationIzlyScreen(izly = izly, url = url, onTermine = { activation = null })
        return
    }

    NavHost(nav, startDestination = if (done == true) "main" else "crous") {
        composable("crous") {
            CrousScreen(api, prefs, onCrous = { code -> nav.navigate("restos/$code") })
        }
        composable(
            "restos/{region}",
            arguments = listOf(navArgument("region") { type = NavType.IntType }),
        ) { backStack ->
            val code = backStack.arguments?.getInt("region") ?: STRASBOURG_CODE
            RestosScreen(
                api = api,
                prefs = prefs,
                regionCode = code,
                regionNom = "",
                onChoisi = { nav.navigate("main") { popUpTo("crous") { inclusive = true } } },
            )
        }
        composable("main") {
            MainScreen(api, store, auth, cart, orders, izly, prefs, selection, nav, ongletInitial)
        }
        composable("paiement") { PaiementScreen(jar, client, izlyStore) }
    }
}
