package fr.croustille.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

// Protocole documenté par IzlyOpenSource + Ezly (LiterateInk), aussi utilisé par Papillon :
// 1. SOAP `Logon` (id + code) -> SMS d'activation.
// 2. `tokenize(url du SMS)` -> session complète (tokens + seed OTP), via deep link.
// 3. REST `IsSessionValid` -> solde ; `LogonLight` (code + OTP) pour rafraîchir.

private const val SOAP_URL = "https://soap.izly.fr/Service.asmx"
private const val REST = "https://rest.izly.fr/Service/PublicService.svc/rest/"
private const val UA = "ksoap2-android/2.6.0+"
private const val CLIENT_VERSION = "8.0"
private const val JSON_MT = "application/json"

@Serializable
data class IzlySession(
    val identifier: String,
    val accessToken: String,
    val refreshToken: String = "",
    val sessionID: String = "",
    val nsse: String = "",
    val seed: String = "",
    val counter: Long = 0,
    val userPublicID: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
)

class BesoinReauth : IllegalStateException("Session Izly expirée.")

class IzlyStore(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        "izly",
        MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun lireSession(): IzlySession? =
        sp.getString("session", null)?.let { json.decodeFromString<IzlySession>(it) }

    fun sauverSession(s: IzlySession) = sp.edit { putString("session", json.encodeToString(s)) }

    fun lireSecret(): String? = sp.getString("secret", null) ?: sp.getString("pin_temp", null)

    /** Identifiants mémorisés (définitifs, sinon temporaires en cours d'activation). */
    fun lireIdentifiants(): Pair<String, String>? {
        val id = sp.getString("id", null) ?: sp.getString("id_temp", null)
        val pin = sp.getString("secret", null) ?: sp.getString("pin_temp", null)
        return if (id.isNullOrBlank() || pin.isNullOrBlank()) null else id to pin
    }

    fun sauverSecretTemporaire(id: String, pin: String) = sp.edit {
        putString("id_temp", id)
        putString("pin_temp", pin)
    }

    fun promouvoirSecret() {
        val id = sp.getString("id_temp", null)
        val pin = sp.getString("pin_temp", null)
        sp.edit {
            id?.let { putString("id", it) }
            pin?.let { putString("secret", it) }
            remove("id_temp")
            remove("pin_temp")
        }
    }

    fun oublier() = sp.edit { clear() }
}

class IzlyRepo(private val client: OkHttpClient, private val store: IzlyStore) {

    fun aUneSession(): Boolean = store.lireSession() != null

    fun aDesIdentifiants(): Boolean = store.lireSecret() != null || aUneSession()

    fun oublier() = store.oublier()

    /** Étape 1 : identifiants -> SMS d'activation envoyé par Izly. */
    suspend fun login(id: String, pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val props = listOf(
                "version" to CLIENT_VERSION, "channel" to "AIZ", "format" to "T",
                "model" to "A", "language" to "fr", "user" to id, "password" to pin,
                "smoneyClientType" to "PART", "rooted" to "0",
            ).joinToString("\n") { (k, v) -> prop(k, v) }
            val resultat = soap("Logon", props)
            if ("UserData" !in resultat) {
                return@withContext Result.failure(IllegalStateException("Réponse Izly inattendue."))
            }
            store.sauverSecretTemporaire(id, pin)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Étape 2 : lien du SMS (deep link) -> session complète persistée. */
    suspend fun activer(urlActivation: String): Result<IzlySession> = withContext(Dispatchers.IO) {
        try {
            val morceaux = urlActivation.trim().trimEnd('/').split('/')
            val actCode = morceaux.last()
            val telephone = morceaux[morceaux.size - 2]
            val props = listOf(
                "version" to CLIENT_VERSION, "channel" to "AIZ", "format" to "T",
                "model" to "A", "language" to "fr", "user" to telephone,
                "smoneyClientType" to "PART", "rooted" to "0", "actCode" to actCode,
            ).joinToString("\n") { (k, v) -> prop(k, v) } +
                "\n<password i:null=\"true\" />"
            val logon = objet(soap("Logon", props), "Logon")
            fun f(tag: String): String = champ(logon, tag)
            val session = IzlySession(
                identifier = f("UID"),
                accessToken = f("ACCESS_TOKEN"),
                refreshToken = f("REFRESH_TOKEN"),
                sessionID = f("SID"),
                nsse = f("NSSE"),
                seed = f("SEED"),
                userPublicID = f("USER_PUBLIC_ID"),
                email = f("EMAIL"),
                firstName = f("FNAME"),
                lastName = f("LNAME"),
            )
            if (session.accessToken.isBlank() || session.seed.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Activation incomplète."))
            }
            val secret = store.lireIdentifiants()?.second
                ?: return@withContext Result.failure(IllegalStateException("Code secret perdu, recommence la connexion."))
            store.sauverSession(session)
            store.promouvoirSecret()
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Solde en euros (rafraîchit la session si besoin). */
    suspend fun solde(): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val session = store.lireSession()
                ?: return@withContext Result.failure(IllegalStateException("Compte Izly non lié."))
            try {
                Result.success(balance(session))
            } catch (e: BesoinReauth) {
                if (!refresh()) {
                    return@withContext Result.failure(
                        IllegalStateException("Session expirée : reconnecte-toi (nouveau SMS)."),
                    )
                }
                Result.success(balance(store.lireSession()!!))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------

    private fun balance(s: IzlySession): Double {
        val rep = rest(
            "IsSessionValid",
            session = s,
            version = "1.0",
            contentType = JSON_MT,
            body = JSONObject().put("sessionId", s.sessionID).toString(),
        )
        val o = JSONObject(rep)
        if (o.has("ErrorMessage")) {
            val code = o.optInt("Code", -1)
            if (code == 140 || code == 570) throw BesoinReauth()
            throw IllegalStateException("${o.optString("ErrorMessage")} ($code)")
        }
        val up = o.getJSONObject("IsSessionValidResult").getJSONObject("UP")
        return up.getString("BAL").replace(',', '.').toDouble()
    }

    /** `LogonLight` : prolonge la session grâce au secret + OTP (compteur persisté). */
    private fun refresh(): Boolean {
        val session = store.lireSession() ?: return false
        val secret = store.lireSecret() ?: return false
        val (otp, compteurSuivant) = otp(session)
        val req = Request.Builder().url(REST + "LogonLight")
            .header("User-Agent", UA)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("channel", "AIZ")
            .header("clientVersion", CLIENT_VERSION)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("format", "T")
            .header("language", "fr")
            .header("model", "A")
            .header("passOTP", secret + otp)
            .header("password", secret)
            .header("smoneyClientType", "PART")
            .header("userId", session.identifier)
            .header("version", "2.0")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        val rep = client.newCall(req).execute().use { it.body!!.string() }
        val o = JSONObject(rep)
        if (o.has("Code")) {
            val code = o.optInt("Code", -1)
            if (code == 571) return false // non rafraîchissable
            if (code == 140 || code == 570) return false
            throw IllegalStateException("${o.optString("ErrorMessage")} ($code)")
        }
        val r = o.getJSONObject("LogonLightResult").getJSONObject("Result")
        val maj = session.copy(
            sessionID = r.optString("SessionId", session.sessionID),
            nsse = r.optString("NSSE", session.nsse),
            counter = compteurSuivant,
        )
        val tokens = r.optJSONObject("Tokens")
        val finale = if (tokens != null) {
            maj.copy(
                accessToken = tokens.optString("AccessToken", maj.accessToken),
                refreshToken = tokens.optString("RefreshToken", maj.refreshToken),
            )
        } else maj
        store.sauverSession(finale)
        return true
    }

    /** HOTP : HMAC-SHA1(clef = seed base64, message = compteur 8 octets), base64url. */
    private fun otp(s: IzlySession): Pair<String, Long> {
        val ctr = s.counter
        val packed = CharArray(8) { i -> ((ctr ushr ((7 - i) * 8)) and 0xFF).toInt().toChar() }
            .concatToString().toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(Base64.decode(s.seed, Base64.DEFAULT), "HmacSHA1"))
        val code = Base64.encodeToString(mac.doFinal(packed), Base64.URL_SAFE or Base64.NO_WRAP)
        return code to ctr + 1
    }

    private fun rest(
        route: String,
        session: IzlySession,
        version: String,
        contentType: String,
        body: String,
    ): String {
        val req = Request.Builder().url(REST + route)
            .header("User-Agent", UA)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("channel", "AIZ")
            .header("clientVersion", CLIENT_VERSION)
            .header("Content-Type", contentType)
            .header("format", "T")
            .header("language", "fr")
            .header("model", "A")
            .header("sessionId", session.sessionID)
            .header("smoneyClientType", "PART")
            .header("userId", session.identifier)
            .header("version", version)
            .post(body.toRequestBody(contentType.toMediaType()))
            .build()
        return client.newCall(req).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("Izly injoignable (${it.code}).")
            it.body!!.string()
        }
    }

    private fun soap(methode: String, proprietes: String): String {
        val enveloppe = """<?xml version="1.0" encoding="utf-8"?>
<v:Envelope xmlns:i="http://www.w3.org/2001/XMLSchema-instance" xmlns:d="http://www.w3.org/2001/XMLSchema" xmlns:c="http://schemas.xmlsoap.org/soap/encoding/" xmlns:v="http://schemas.xmlsoap.org/soap/envelope/"><v:Header/><v:Body>
<$methode xmlns="Service" id="o0" c:root="1">
$proprietes
</$methode>
</v:Body></v:Envelope>"""
        val req = Request.Builder().url(SOAP_URL)
            .header("User-Agent", UA)
            .header("Content-Type", "text/xml;charset=utf-8")
            .header("smoneyClientType", "PART")
            .header("SOAPAction", "Service/$methode")
            .post(enveloppe.toRequestBody("text/xml;charset=utf-8".toMediaType()))
            .build()
        val rep = client.newCall(req).execute().use {
            if (it.code != 200) throw IllegalStateException("Izly injoignable (${it.code}).")
            it.body!!.string()
        }
        val brut = Regex("<${methode}Result>(.*)</${methode}Result>", RegexOption.DOT_MATCHES_ALL)
            .find(rep)?.groupValues?.get(1)
            ?: throw IllegalStateException("Réponse Izly illisible.")
        val clair = brut.replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        if ("<E>" in clair || "<E " in clair) {
            val msg = champ(clair, "Msg").ifBlank { "Erreur Izly" }
            val code = champ(clair, "Code")
            throw IllegalStateException("$msg ($code)")
        }
        return clair
    }

    private fun objet(xml: String, tag: String): String =
        Regex("<$tag>(.*)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: xml

    private fun champ(xml: String, tag: String): String =
        Regex("<$tag>([^<]*)</$tag>").find(xml)?.groupValues?.get(1).orEmpty()

    private fun prop(nom: String, valeur: String): String =
        "<$nom i:type=\"d:string\">${echapper(valeur)}</$nom>"

    private fun echapper(s: String): String = buildString {
        for (c in s) {
            if (c.isLetterOrDigit() || c == ' ' || c == '.') append(c) else append("&#${c.code};")
        }
    }.replace("<", "&lt;").replace(">", "&gt;")
}
