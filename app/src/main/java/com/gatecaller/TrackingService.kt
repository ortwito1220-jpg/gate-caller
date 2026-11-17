package com.gatecaller

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

fun startTracking(c: Context, source: String, minutes: Float) {
    if (points(c).isEmpty()) return alert(c, "No gate location set")
    TrackingService.running = true
    c.startForegroundService(
        Intent(c, TrackingService::class.java).putExtra("source", source).putExtra("minutes", minutes)
    )
}

// Foreground service: 1 s GPS until the car reaches a gate location (then call) or the time runs out.
@SuppressLint("MissingPermission")
class TrackingService : Service() {
    companion object {
        var running = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gps by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var source = ""
    private var gates = emptyList<Point>()
    private var hits = 0
    private var closest = Float.MAX_VALUE

    private val onFix = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) = result.locations.forEach(::check)
    }

    private val timeout = Runnable {
        log(this, "TIMEOUT closest=%.1fm source=%s".format(closest, source))
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        source = intent!!.getStringExtra("source")!!
        gates = points(this)
        hits = 0
        startForeground(1, notification(this, "Tracking ($source)", false), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        gps.requestLocationUpdates(gpsRequest(), onFix, Looper.getMainLooper())
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, (intent.getFloatExtra("minutes", 0f) * 60_000).toLong())
        return START_NOT_STICKY
    }

    // The one decision rule: 2 readings in a row that are close enough to the nearest gate location
    // and accurate enough.
    private fun check(fix: Location) {
        val (gate, dist) = gates.mapIndexed { i, p -> i + 1 to distance(fix, p) }.minBy { it.second }
        closest = minOf(closest, dist)
        hits = if (dist <= triggerM(this) && fix.accuracy <= maxAccM(this)) hits + 1 else 0

        val where = "dist=%.1fm acc=%.1fm gate=%d source=%s".format(dist, fix.accuracy, gate, source)
        getSystemService(NotificationManager::class.java).notify(1, notification(this, where, false))
        if (hits == 2) {
            call(where)
            stopSelf()
        }
    }

    private fun distance(fix: Location, p: Point): Float {
        val d = FloatArray(1)
        Location.distanceBetween(fix.latitude, fix.longitude, p.lat, p.lng, d)
        return d[0]
    }

    private fun call(where: String) {
        val telecom = getSystemService(TelecomManager::class.java)
        if (telecom.isInCall) {
            alert(this, "Already on a call, gate not called ($where)")
        } else {
            telecom.placeCall(Uri.fromParts("tel", gateNumber(this), null), Bundle())
            prefs(this).edit().putLong("lastCall", System.currentTimeMillis()).apply()
            log(this, "CALL $where")
        }
    }

    override fun onDestroy() {
        running = false
        gps.removeLocationUpdates(onFix)
        handler.removeCallbacks(timeout)
    }

    override fun onBind(intent: Intent?) = null
}
