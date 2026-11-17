package com.gatecaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

// Receives the geofence ENTER event, BOOT_COMPLETED and the app-update install result.
class GateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> registerGeofences(c)
            INSTALL_ACTION -> onInstallResult(c, intent)
            else -> if (GeofencingEvent.fromIntent(intent)?.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) onEnter(c)
        }
    }

    private fun onEnter(c: Context) {
        // Overlapping zones of nearby gate locations send several ENTERs: track once, call once.
        if (TrackingService.running) return
        val sinceCall = System.currentTimeMillis() - prefs(c).getLong("lastCall", 0)
        if (sinceCall < cooldownMin(c) * 60_000) return log(c, "ENTER ignored (called ${sinceCall / 60_000} min ago)")
        log(c, "ENTER zone")
        startTracking(c, "geofence", trackMin(c))
    }

    private fun onInstallResult(c: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                c.startActivity(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            PackageInstaller.STATUS_SUCCESS -> log(c, "UPDATE installed")
            else -> alert(c, "Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
