package com.gatecaller

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

// The one setup screen.
@SuppressLint("MissingPermission", "BatteryLife", "SetTextI18n")
class MainActivity : Activity() {
    private val number by lazy { findViewById<EditText>(R.id.number) }
    private val trigger by lazy { findViewById<EditText>(R.id.trigger) }
    private val gateStatus by lazy { findViewById<TextView>(R.id.gateStatus) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        number.setText(gateNumber(this))
        trigger.setText(triggerM(this).toString())
        if (prefs(this).contains("lat")) gateStatus.text = "Gate: %.6f, %.6f".format(gateLat(this), gateLng(this))

        click(R.id.setGate) { setGateHere() }
        click(R.id.track) { save(); startTracking(this, "button", MANUAL_TRACK_MIN) }
        click(R.id.permLocation) { requestPermissions(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), 0) }
        click(R.id.permBackground) { requestPermissions(arrayOf(ACCESS_BACKGROUND_LOCATION), 0) }
        click(R.id.permPhone) { requestPermissions(arrayOf(CALL_PHONE, READ_PHONE_STATE), 0) }
        click(R.id.permNotify) { requestPermissions(arrayOf(POST_NOTIFICATIONS), 0) }
        click(R.id.permBattery) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    override fun onResume() {
        super.onResume()
        mark(R.id.permLocation, "1. Location", granted(ACCESS_FINE_LOCATION))
        mark(R.id.permBackground, "2. Location: Allow all the time", granted(ACCESS_BACKGROUND_LOCATION))
        mark(R.id.permPhone, "3. Phone calls", granted(CALL_PHONE))
        mark(R.id.permNotify, "4. Notifications", granted(POST_NOTIFICATIONS))
        mark(R.id.permBattery, "5. No battery optimization",
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
        if (granted(ACCESS_BACKGROUND_LOCATION)) registerGeofence(this)
        if (logFile(this).exists()) {
            findViewById<TextView>(R.id.log).text = logFile(this).readLines().reversed().joinToString("\n")
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun save() {
        prefs(this).edit()
            .putString("number", number.text.toString())
            .putFloat("trigger", trigger.text.toString().toFloatOrNull() ?: TRIGGER_M)
            .apply()
    }

    // Average GATE_SAMPLES GPS readings and store them as the point where the car should be when the call fires.
    private fun setGateHere() {
        val fixes = ArrayList<Location>()
        val gps = LocationServices.getFusedLocationProviderClient(this)
        gps.requestLocationUpdates(gpsRequest(), object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fixes += result.locations
                gateStatus.text = "Reading GPS ${fixes.size}/$GATE_SAMPLES"
                if (fixes.size < GATE_SAMPLES) return
                gps.removeLocationUpdates(this)
                saveGate(this@MainActivity, fixes.map { it.latitude }.average(), fixes.map { it.longitude }.average())
                registerGeofence(this@MainActivity)
                gateStatus.text = "Gate: %.6f, %.6f (avg accuracy %.1f m)"
                    .format(gateLat(this@MainActivity), gateLng(this@MainActivity), fixes.map { it.accuracy }.average())
            }
        }, Looper.getMainLooper())
    }

    private fun click(id: Int, action: () -> Unit) = findViewById<Button>(id).setOnClickListener { action() }
    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun mark(id: Int, label: String, ok: Boolean) {
        findViewById<Button>(id).text = if (ok) "$label ✓" else label
    }
}
