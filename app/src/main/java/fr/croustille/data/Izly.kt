package fr.croustille.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

private const val IZLY = "https://mon-espace.izly.fr"

/** Identifiants Izly chiffrés (opt-in explicite de l'utilisateur). */
class IzlyStore(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        "izly",
        MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun lire(): Pair<String, String>? {
        val id = sp.getString("id", null)
        val pin = sp.getString("pin", null)
        return if (id.isNullOrBlank() || pin.isNullOrBlank()) null else id to pin
    }

    fun sauver(id: String, pin: String) = sp.edit {
        putString("id", id)
        putString("pin", pin)
    }

    fun oublier() = sp.edit { clear() }
}

/** Solde Izly via mon-espace.izly.fr (formulaire web classique). */
class IzlyRepo(private val client: OkHttpClient, private val store: IzlyStore) {

    fun aDesIdentifiants(): Boolean = store.lire() != null

    fun oublier() = store.oublier()

    suspend fun solde(id: String, pin: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Page de login : token anti-CSRF + cookies.
            val page = get("$IZLY/Home/Logon")
            val token = Regex("""name="__RequestVerificationToken"[^>]*value="([^"]+)"""")
                .find(page)?.groupValues?.get(1)
                ?: return@withContext Result.failure(IllegalStateException("Page Izly injoignable."))
            // 2. Connexion.
            val body = FormBody.Builder()
                .add("__RequestVerificationToken", token)
                .add("ReturnUrl", "")
                .add("Username", id)
                .add("Password", pin)
                .build()
            val accueil = post("$IZLY/Home/Logon", body)
            if (accueil.contains("name=\"Password\"") && accueil.contains("Username")) {
                return@withContext Result.failure(IllegalStateException("Identifiants Izly refusés."))
            }
            // 3. Solde sur la page d'accueil.
            val montant = Regex("""Solde[\s\S]{0,120}?(\d[\d\s]*[.,]\d{2})\s*€""").find(accueil)
                ?.groupValues?.get(1)?.replace("\\s".toRegex(), " ")
                ?: return@withContext Result.failure(IllegalStateException("Connecté, mais solde introuvable."))
            store.sauver(id, pin)
            Result.success("$montant €")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun soldeSauve(): Result<String>? {
        val (id, pin) = store.lire() ?: return null
        return solde(id, pin)
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "Croustille/1.0").get().build()
        return client.newCall(req).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("Izly injoignable (${it.code}).")
            it.body!!.string()
        }
    }

    private fun post(url: String, body: FormBody): String {
        val req = Request.Builder().url(url).header("User-Agent", "Croustille/1.0").post(body).build()
        return client.newCall(req).execute().use { it.body!!.string() }
    }
}
