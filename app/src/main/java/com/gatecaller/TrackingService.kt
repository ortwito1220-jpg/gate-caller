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

fun startTracking(c: Context, source: String, minutes: Long) {
    c.startForegroundService(
        Intent(c, TrackingService::class.java).putExtra("source", source).putExtra("minutes", minutes)
    )
}

// Foreground service: 1 s GPS until the car reaches the gate (then call) or the time runs out.
@SuppressLint("MissingPermission")
class TrackingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val gps by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var source = ""
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
        source = intent!!.getStringExtra("source")!!
        hits = 0
        startForeground(1, notification(this, "Tracking ($source)", false), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        gps.requestLocationUpdates(gpsRequest(), onFix, Looper.getMainLooper())
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, intent.getLongExtra("minutes", 0) * 60_000)
        return START_NOT_STICKY
    }

    // The one decision rule: 2 readings in a row that are close enough and accurate enough.
    private fun check(fix: Location) {
        val d = FloatArray(1)
        Location.distanceBetween(fix.latitude, fix.longitude, gateLat(this), gateLng(this), d)
        val dist = d[0]
        closest = minOf(closest, dist)
        hits = if (dist <= triggerM(this) && fix.accuracy <= MAX_ACC_M) hits + 1 else 0

        val where = "dist=%.1fm acc=%.1fm source=%s".format(dist, fix.accuracy, source)
        getSystemService(NotificationManager::class.java).notify(1, notification(this, where, false))
        if (hits == 2) {
            call(where)
            stopSelf()
        }
    }

    private fun call(where: String) {
        val telecom = getSystemService(TelecomManager::class.java)
        if (telecom.isInCall) {
            alert(this, "Already on a call, gate not called ($where)")
        } else {
            telecom.placeCall(Uri.fromParts("tel", gateNumber(this), null), Bundle())
            log(this, "CALL $where")
        }
    }

    override fun onDestroy() {
        gps.removeLocationUpdates(onFix)
        handler.removeCallbacks(timeout)
    }

    override fun onBind(intent: Intent?) = null
}
