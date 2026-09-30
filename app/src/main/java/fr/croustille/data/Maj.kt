package fr.croustille.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import fr.croustille.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "Maj"
private const val DEPOT = "LuckyTheCookie/croustille"

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val taille: Long,
)

/** Compare "1.2.3" vs "1.2.3" : >0 si a plus récent. */
fun comparerVersions(a: String, b: String): Int {
    val pa = a.trimStart('v', 'V').split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
    val pb = b.trimStart('v', 'V').split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val d = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return 0
}

class MajRepo(private val client: OkHttpClient) {

    /** Dernière release GitHub, ou null si à jour / injoignable / ignorée. */
    suspend fun chercher(ignoree: String?): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$DEPOT/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Croustille/${BuildConfig.VERSION_NAME}")
                .get().build()
            val corps = client.newCall(req).execute().use {
                if (!it.isSuccessful) return@withContext null
                it.body?.string() ?: return@withContext null
            }
            val o = JSONObject(corps)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return@withContext null
            val tag = o.optString("tag_name")
            if (tag.isBlank() || tag == ignoree) return@withContext null
            if (comparerVersions(tag, BuildConfig.VERSION_NAME) <= 0) return@withContext null
            val assets = o.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val nom = a.optString("name")
                if (nom.endsWith(".apk")) {
                    return@withContext ReleaseInfo(
                        tag = tag,
                        version = tag.trimStart('v', 'V'),
                        notes = o.optString("body").take(2000),
                        apkUrl = a.optString("browser_download_url"),
                        taille = a.optLong("size"),
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "check maj", e)
            null
        }
    }

    /** Télécharge l'APK vers le cache, avec progression 0..100. */
    suspend fun telecharger(info: ReleaseInfo, ctx: Context, onProgres: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "Croustille/${BuildConfig.VERSION_NAME}")
                .get().build()
            client.newCall(req).execute().use { rep ->
                if (!rep.isSuccessful) throw IllegalStateException("Téléchargement impossible (${rep.code}).")
                val total = rep.body?.contentLength() ?: info.taille.takeIf { it > 0 } ?: -1
                val sortie = File(ctx.cacheDir, "maj.apk")
                var lus = 0L
                rep.body!!.byteStream().use { entree ->
                    sortie.outputStream().use { out ->
                        val tampon = ByteArray(64 * 1024)
                        while (true) {
                            val n = entree.read(tampon)
                            if (n < 0) break
                            out.write(tampon, 0, n)
                            lus += n
                            if (total > 0) onProgres(((lus * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                sortie
            }
        }

    /** Ouvre l'installeur système (l'utilisateur valide l'installation). */
    fun installer(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(intent)
    }
}

fun clientMaj(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.MINUTES)
    .build()
