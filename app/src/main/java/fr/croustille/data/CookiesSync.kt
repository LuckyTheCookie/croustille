package fr.croustille.data

import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "Cookies"

/** App (OkHttp) -> WebView : rejoue la session (login + panier) dans le navigateur. */
fun cookiesVersWebView(jar: PersistentCookieJar, pageUrl: String) {
    try {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        for (c in jar.cookiesFor(pageUrl)) {
            val valeur = buildString {
                append("${c.name}=${c.value}")
                if (!c.hostOnly) append("; Domain=${c.domain}")
                append("; Path=${c.path}")
            }
            cm.setCookie("https://crousandgo.crous-strasbourg.fr", valeur)
        }
        cm.flush()
    } catch (e: Exception) {
        Log.w(TAG, "vers WebView", e)
    }
}

/** WebView -> App : récupère les cookies posés pendant la navigation (ex: Izly). */
fun cookiesDepuisWebView(jar: PersistentCookieJar, pageUrl: String) {
    try {
        val url = pageUrl.toHttpUrl()
        val brut = CookieManager.getInstance().getCookie(pageUrl) ?: return
        val cookies = brut.split(';').mapNotNull { morceau ->
            val nom = morceau.substringBefore('=').trim()
            val valeur = morceau.substringAfter('=', "")
            if (nom.isBlank() || nom.startsWith("__")) null
            else Cookie.Builder()
                .name(nom).value(valeur)
                .domain(url.host)
                .path("/")
                .build()
        }
        if (cookies.isNotEmpty()) jar.injecter(url, cookies)
    } catch (e: Exception) {
        Log.w(TAG, "depuis WebView", e)
    }
}
