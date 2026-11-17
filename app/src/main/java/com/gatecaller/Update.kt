package com.gatecaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

const val REPO = "ortwito1220-jpg/gate-caller"
const val INSTALL_ACTION = "com.gatecaller.INSTALL"

fun installedVersion(c: Context) = c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode

// Every push to main publishes a GitHub Release "v<build number>" with the signed APK.
// If the latest release is newer than this app, download it and hand it to the system installer.
// Runs on a background thread; `status` reports progress for the screen.
fun checkAndInstallUpdate(c: Context, status: (String) -> Unit) {
    status("Checking…")
    val release = JSONObject(get("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json")
        .bufferedReader().readText())
    val latest = release.getString("tag_name").removePrefix("v").toLong()
    if (latest <= installedVersion(c)) return status("Up to date (version $latest)")

    status("Downloading version $latest…")
    val installer = c.packageManager.packageInstaller
    val session = installer.openSession(
        installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
    )
    val apkUrl = release.getJSONArray("assets").getJSONObject(0).getString("url")
    get(apkUrl, "application/octet-stream").use { apk ->
        session.openWrite("gate-caller.apk", 0, -1).use { out ->
            apk.copyTo(out)
            session.fsync(out)
        }
    }
    val result = PendingIntent.getBroadcast(
        c, 1, Intent(c, GateReceiver::class.java).setAction(INSTALL_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    session.commit(result.intentSender)
    session.close()
    status("Installing version $latest…")
}

// GitHub API GET. The APK download redirects to a storage URL that must be fetched without the token.
private fun get(url: String, accept: String): InputStream {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = false
    conn.setRequestProperty("Accept", accept)
    if (BuildConfig.UPDATE_TOKEN.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.UPDATE_TOKEN}")
    return when (conn.responseCode) {
        200 -> conn.inputStream
        in 300..399 -> URL(conn.getHeaderField("Location")).openStream()
        else -> error("GitHub answered HTTP ${conn.responseCode}")
    }
}
