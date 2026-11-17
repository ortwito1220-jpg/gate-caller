package com.gatecaller

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

// The one setup screen.
@SuppressLint("MissingPermission", "BatteryLife", "SetTextI18n")
class MainActivity : Activity() {
    private val number by lazy { findViewById<EditText>(R.id.number) }
    private val trigger by lazy { findViewById<EditText>(R.id.trigger) }
    private val setGate by lazy { findViewById<Button>(R.id.setGate) }
    private val progress by lazy { findViewById<ProgressBar>(R.id.progress) }
    private val track by lazy { findViewById<Button>(R.id.track) }
    private val trackTextColor by lazy { track.textColors }
    private val gps by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var reading: LocationCallback? = null

    // Refresh the tracking button every second while the screen is open.
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            showTracking()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        number.setText(gateNumber(this))
        trigger.setText(triggerM(this).toString())
        progress.max = GATE_SAMPLES

        click(R.id.setGate) { addGateHere() }
        click(R.id.track) {
            if (TrackingService.running) {
                stopService(Intent(this, TrackingService::class.java))
                log(this, "STOP button")
            } else {
                save()
                startTracking(this, "button", MANUAL_TRACK_MIN)
            }
            showTracking()
        }
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
        if (granted(ACCESS_BACKGROUND_LOCATION)) registerGeofences(this)
        showPoints()
        showLog()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        save()
    }

    override fun onDestroy() {
        super.onDestroy()
        reading?.let { gps.removeLocationUpdates(it) }
    }

    private fun save() {
        prefs(this).edit()
            .putString("number", number.text.toString())
            .putFloat("trigger", trigger.text.toString().toFloatOrNull() ?: TRIGGER_M)
            .apply()
    }

    // Average GATE_SAMPLES GPS readings and add them as a point where the car should be when the call fires.
    private fun addGateHere() {
        val fixes = ArrayList<Location>()
        setGate.isEnabled = false
        setGate.text = "Reading GPS…"
        progress.progress = 0
        progress.visibility = View.VISIBLE
        reading = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fixes += result.locations
                progress.progress = fixes.size
                if (fixes.size < GATE_SAMPLES) return
                gps.removeLocationUpdates(this)
                reading = null
                savePoints(this@MainActivity, points(this@MainActivity) +
                    Point(fixes.map { it.latitude }.average(), fixes.map { it.longitude }.average()))
                progress.visibility = View.GONE
                setGate.isEnabled = true
                setGate.text = "Set gate here"
                showPoints()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Gate set!")
                    .setMessage("Gate location %d saved (average GPS accuracy %.1f m)."
                        .format(points(this@MainActivity).size, fixes.map { it.accuracy }.average()))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        gps.requestLocationUpdates(gpsRequest(), reading!!, Looper.getMainLooper())
    }

    private fun showPoints() {
        val list = findViewById<LinearLayout>(R.id.points)
        list.removeAllViews()
        val gates = points(this)
        if (gates.isEmpty()) list.addView(TextView(this).apply { text = "No gate location set" })
        gates.forEachIndexed { i, p ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "%d.  %.6f, %.6f".format(i + 1, p.lat, p.lng) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Remove"
                setOnClickListener { confirmRemove(i) }
            })
            list.addView(row)
        }
    }

    private fun confirmRemove(i: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove gate location ${i + 1}?")
            .setPositiveButton("Remove") { _, _ ->
                savePoints(this, points(this).filterIndexed { j, _ -> j != i })
                showPoints()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTracking() {
        val on = TrackingService.running
        track.text = if (on) "Tracking to gate… tap to stop" else "Leaving: track to gate now"
        track.backgroundTintList = if (on) ColorStateList.valueOf(Color.parseColor("#2E7D32")) else null
        track.setTextColor(if (on) ColorStateList.valueOf(Color.WHITE) else trackTextColor)
    }

    private fun showLog() {
        if (logFile(this).exists()) {
            findViewById<TextView>(R.id.log).text = logFile(this).readLines().reversed().joinToString("\n")
        }
    }

    private fun click(id: Int, action: () -> Unit) = findViewById<Button>(id).setOnClickListener { action() }
    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun mark(id: Int, label: String, ok: Boolean) {
        findViewById<Button>(id).text = if (ok) "$label ✓" else label
    }
}
