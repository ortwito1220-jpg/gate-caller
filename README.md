# Gate Caller

Android app that calls the gate's phone number when the car is a few metres from the gate.
The spec is in [CLAUDE.md](CLAUDE.md). Tunables are at the top of
[Settings.kt](app/src/main/java/com/gatecaller/Settings.kt).

## One-time: signing keystore

The APK is signed with a fixed keystore kept in repo secrets, so every new build installs over the old one.
Run these in PowerShell, in a folder **outside** the repo (for example `Documents\gate-caller-key`).

1. Create a key and certificate (valid ~27 years):

   ```
   openssl req -x509 -newkey rsa:2048 -nodes -days 10000 -subj "/CN=gate-caller" -keyout key.pem -out cert.pem
   ```

2. Pack them into a keystore. It asks for an export password twice; pick one and remember it:

   ```
   openssl pkcs12 -export -inkey key.pem -in cert.pem -name gate -out keystore.p12
   ```

3. Store the keystore as a secret (base64):

   ```
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\keystore.p12")) | gh secret set KEYSTORE_BASE64 -R ortwito1220-jpg/gate-caller
   ```

4. Store the password as a secret (it prompts; paste the password from step 2):

   ```
   gh secret set KEYSTORE_PASSWORD -R ortwito1220-jpg/gate-caller
   ```

5. Start a build:

   ```
   gh workflow run build.yml -R ortwito1220-jpg/gate-caller
   ```

Keep `keystore.p12` and the password somewhere safe. If they are lost, a new build can only be installed
after uninstalling the app. `key.pem` and `cert.pem` can be deleted.

## Get the APK onto the phone

1. GitHub → repo → **Actions** → latest "Build APK" run → download the `gate-caller-apk` artifact (a zip).
2. Unzip, copy `app-release.apk` to the phone, open it and allow "install unknown apps".

## Phone setup (OnePlus / OxygenOS)

In the app:

1. Enter the gate phone number.
2. Tap the five permission buttons in order until each shows ✓.
   For "Allow all the time", choose it on the settings page that opens.
3. Drive to the spot where the car should be when the call fires, tap **Set gate here** and wait for the
   progress bar to fill (15 readings) and the "Gate set!" popup. Repeat at other spots to add more gate
   locations (for example, approaching from a different street); the call fires near any of them.

In OxygenOS settings (so the system does not kill the app):

- Settings → Apps → Gate Caller → Battery usage → **Allow background activity**.
- Settings → Apps → Auto launch → enable Gate Caller.
- Open recent apps, tap the menu on Gate Caller's card → **Lock**.

For leaving: add the **Gate** tile to Quick Settings (edit the tiles, drag "Gate" in). Tap it when you start driving.

## Tuning

The log at the bottom of the app shows `ENTER zone`, `CALL dist=… acc=… source=…` and
`TIMEOUT closest=…` lines; `gate=N` is the number of the nearest gate location. While tracking, the notification shows the live distance and accuracy.
