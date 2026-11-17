package com.gatecaller

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Tunables ----
const val TRIGGER_M = 6f            // default trigger distance; editable in the app
const val MAX_ACC_M = 10f           // ignore GPS readings less accurate than this
const val ZONE_RADIUS_M = 1000f     // geofence radius around each gate location
const val TRACK_TIMEOUT_MIN = 10L   // tracking time after a geofence ENTER
const val MANUAL_TRACK_MIN = 5L     // tracking time after the tile / button
const val GATE_SAMPLES = 15         // GPS readings averaged by "Set gate here"
const val CALL_COOLDOWN_MIN = 10L   // after a call, ignore geofence ENTERs this long (tile/button still work)

// ---- Settings ----
class Point(val lat: Double, val lng: Double)

fun prefs(c: Context) = c.getSharedPreferences("gate", Context.MODE_PRIVATE)
fun gateNumber(c: Context) = prefs(c).getString("number", "")!!
fun triggerM(c: Context) = prefs(c).getFloat("trigger", TRIGGER_M)

// Gate locations, stored as "lat,lng;lat,lng". Falls back to the single "lat"/"lng" of version 1.
fun points(c: Context): List<Point> {
    val p = prefs(c)
    val raw = p.getString("points", null)
        ?: if (p.contains("lat")) "${p.getString("lat", "")},${p.getString("lng", "")}" else ""
    return raw.split(";").filter { it.isNotEmpty() }.map {
        val (lat, lng) = it.split(",")
        Point(lat.toDouble(), lng.toDouble())
    }
}

fun savePoints(c: Context, list: List<Point>) {
    prefs(c).edit()
        .putString("points", list.joinToString(";") { "${it.lat},${it.lng}" })
        .remove("lat").remove("lng")
        .apply()
    registerGeofences(c)
}

// ---- GPS: high accuracy, every 1 s ----
fun gpsRequest() = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()

// ---- Geofences: one per gate location, ENTER only. A new ENTER can only come after an exit,
// so it is one call per arrival. ----
@SuppressLint("MissingPermission")
fun registerGeofences(c: Context) {
    val client = LocationServices.getGeofencingClient(c)
    val intent = PendingIntent.getBroadcast(
        c, 0, Intent(c, GateReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    val fences = points(c).mapIndexed { i, p ->
        Geofence.Builder()
            .setRequestId("gate$i")
            .setCircularRegion(p.lat, p.lng, ZONE_RADIUS_M)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
    }
    client.removeGeofences(intent).addOnCompleteListener {
        if (fences.isEmpty()) return@addOnCompleteListener
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
        client.addGeofences(request, intent)
            .addOnFailureListener { alert(c, "Geofence not registered: ${it.message}") }
    }
}

// ---- Log (plain text file, shown in the app) ----
fun logFile(c: Context) = File(c.filesDir, "log.txt")
fun log(c: Context, line: String) {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    logFile(c).appendText("$time  $line\n")
}

// ---- Notifications ----
fun notification(c: Context, text: String, alert: Boolean): Notification {
    val channel = if (alert) "alerts" else "tracking"
    val importance = if (alert) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW
    c.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(channel, channel, importance))
    return Notification.Builder(c, channel)
        .setSmallIcon(R.drawable.ic_gate)
        .setContentTitle("Gate Caller")
        .setContentText(text)
        .build()
}

fun alert(c: Context, text: String) {
    log(c, text)
    c.getSystemService(NotificationManager::class.java).notify(2, notification(c, text, true))
}
