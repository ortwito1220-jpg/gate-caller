package com.gatecaller

import android.service.quicksettings.TileService

// Quick Settings tile: tap when leaving to track toward the gate for a few minutes.
class GateTileService : TileService() {
    override fun onClick() = startTracking(this, "tile", manualMin(this))
}
