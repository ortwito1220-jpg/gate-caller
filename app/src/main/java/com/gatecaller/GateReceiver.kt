package com.gatecaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

// Receives the geofence ENTER event and BOOT_COMPLETED.
class GateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            registerGeofence(c)
        } else if (GeofencingEvent.fromIntent(intent)?.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            log(c, "ENTER zone")
            startTracking(c, "geofence", TRACK_TIMEOUT_MIN)
        }
    }
}
