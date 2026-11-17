# Gate Caller: Handoff for Claude Code

Build a small Android app that automatically calls my gate's phone number when my car is about 5 m from the gate. The gate opens on an incoming call and hangs up by itself.

Please:
1. Create a new **private** GitHub repo `gate-caller` (same way you set up my other repos).
2. Save this file in the repo as `CLAUDE.md`.
3. Build the app and the GitHub Actions workflow described below, then push.

## Phone and setup
- OnePlus Nord (4), OxygenOS. The OS kills background apps aggressively.
- No Android Studio. The APK is built by GitHub Actions and sideloaded onto the phone.
- The parking spot is 30–70 m from the gate.

## Behavior

### Arriving (automatic)
1. Idle: one geofence (~1 km radius) around each gate location. While tracking, further ENTERs are ignored. It has to be this wide because background geofence events can arrive a couple of minutes late.
2. Geofence ENTER → start a foreground service with high-accuracy GPS every 1 s.
3. Call when **2 readings in a row** have `distance ≤ TRIGGER_M` (to the nearest gate location) and `accuracy ≤ MAX_ACC_M`.
4. Stop the service after the call, or after 10 minutes with no call.
5. Re-arm only after geofence EXIT. That gives one call per arrival, with no cooldown timer.

### Leaving (manual)
- A Quick Settings tile (and a button in the app) starts the same tracking service for ~5 minutes.
- No auto-detection for leaving. Car Bluetooth, Wi-Fi, activity recognition and NFC were all considered and rejected.

### Calling
- Place the call from the background via `TelecomManager.placeCall`. Starting the dialer with an Activity intent is blocked from the background.
- If already on a call: don't call; show a notification instead.
- No hang-up logic (the gate hangs up itself).

### Setup screen (one screen)
- Gate phone number.
- Gate locations: a list (one phone number for all). Each can be removed (with a confirm dialog).
- "Set gate here": average ~15 GPS readings and **add** a location. This is the point where the **car** is when the call should fire. Shows a progress bar while reading, then a "Gate set!" dialog.
- "Leaving" button: turns green with "Tracking to gate… tap to stop" while the tracking service runs (from any source); tapping it then stops tracking.
- Trigger distance (default 6 m).
- Buttons for the OnePlus permissions: battery optimization exemption, location "Allow all the time", phone call permission, notifications.
- Log view.

### Other
- Re-register the geofence after boot (`BOOT_COMPLETED`).
- Log each trigger (time, distance, accuracy, source: geofence or tile) to a plain text file, shown in the app, for tuning.

## Code style (important)
- Kotlin, minimal dependencies (only `play-services-location`). No database, no DI, no architecture layers.
- About 4–5 files: settings (SharedPreferences), receiver (geofence + boot), tracking service, tile service, main activity.
- Tunables are constants at the top of one file: `TRIGGER_M`, `MAX_ACC_M`, `ZONE_RADIUS_M`, `TRACK_TIMEOUT_MIN`, `MANUAL_TRACK_MIN`.
- **No defensive checks for unlikely edge cases.** One decision rule. If something unexpected happens, show a notification instead of writing recovery code. Keep it readable.

## Build (GitHub Actions)
- The workflow builds a release APK on push and uploads it as an artifact.
- Sign it with a **fixed keystore** stored in repo secrets (base64), so updates install over the old version. Walk me through generating the keystore and adding the secrets.

## Not doing (for now)
- Different phone numbers per gate location, schedules, pattern learning, Bluetooth beacon, approach direction, mock-location protection.
