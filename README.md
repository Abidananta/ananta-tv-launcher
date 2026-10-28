# Ananta TV Launcher

Apple TV-inspired Android TV home launcher starter for Xiaomi Mi Box 4. Dark cinematic background with purple/cyan glow, app discovery, D-pad navigation and a GitHub Actions APK build.

## Layout (v2)

- **Dock** (glass bar, smaller tiles): only OTT / streaming apps. Tiles slide *inside* the dock and fade at its edges.
- **All apps** (below the dock, Apple TV style): every other installed app in a grid. D-pad Down from the dock enters it, Up from the first row returns.
- **Hold OK** on any app to move it between the dock and All apps (remembered).
- Hero artwork loader: browser User-Agent, redirect handling (incl. http->https), cleartext allowed, jpeg/png/webp only, tries every image the app provides, no more 400-row cap. If an app has no artwork a brand card is shown instead.
- Debug artwork problems with: `adb logcat -s AnantaTV`

## What this starter includes

- Android HOME activity so Android can offer it as the Home launcher.
- Leanback launcher entry for Android TV app visibility.
- Discovers installed launchable apps and starts them.
- D-pad left/right navigation with a genuinely scrolling/centered app dock, up/down between app dock and shortcut row, OK to launch.
- Animated focused-card scaling and focus-ring accents.
- Launcher icon and Android TV banner resources.
- Dark gradient hero card and purple/cyan glass-inspired app cards.
- GitHub Actions workflow to build and upload a debug APK without Android Studio.

## Important current limitations

- This is a starter/concept build, not a complete tvOS clone. Visual layout and Android TV compatibility still need testing on the target Mi Box firmware.
- Live TV / Movies / Series are visual shortcut tiles only; they do not include media catalogs or playback.
- The UI shows up to 7 apps at once and recenters the visible dock around the selected app while moving left/right.
- Settings opens Android Settings. Do not remove the stock launcher.
- Debug APK is not a release-signed APK. Use Android's normal install confirmation.

## Build using GitHub Actions (no Android Studio)

1. Create a new **public or private** repository on GitHub named `Ananta-TV-Launcher`.
2. Upload all files and folders from this project to the repository root.
3. Commit to the `main` branch.
4. Open **Actions** in the repository. Approve workflow execution if GitHub asks.
5. Open **Build Ananta TV Launcher APK**, select the latest successful run, and download artifact `Ananta-TV-debug-apk`.
6. Extract the ZIP artifact. The APK inside is `app-debug.apk`.

The workflow uses GitHub-hosted Ubuntu, Java 17 and Gradle 8.9. It installs Android platform 35 and Build Tools 35.0.0. It intentionally does not request the retired SDK package named `tools` (which causes `Warning: Failed to find package 'tools'` on current SDK repositories). It does not require Android Studio.

## Install on Xiaomi Mi Box 4

1. On the Mi Box, open Settings → About and tap **Build** several times to enable Developer options if needed.
2. In Developer options, enable USB debugging or network debugging only if you need ADB. Keep the device on your trusted home network.
3. Easiest option: copy the APK to a USB drive if your Mi Box setup supports it, then open it with a trusted file manager and allow that installer to install this APK.
4. Alternative ADB install from a computer on the same trusted network:
   - Find the Mi Box IP in Settings → Network.
   - Enable Network debugging / Network ADB if your firmware exposes it.
   - On a computer with Android platform-tools installed, run `adb connect BOX_IP:5555` only if that connection method is supported and the TV displays/accepts the debugging authorization prompt.
   - Run `adb install -r app-debug.apk`.
   - Do not expose ADB to the internet; disable debugging after setup.

## Select Ananta TV as Home

1. Press Home on the remote. Android may show a Home-app chooser.
2. Choose **Ananta TV** and select **Always** only after testing it.
3. If no chooser appears, open Settings → Apps → Default apps / Home app (menu labels vary by Android TV version) and choose Ananta TV.
4. Test launching several apps and returning Home.

## Recovery / return to stock launcher

- Open Android Settings → Apps → Ananta TV → Clear defaults if available, then press Home and choose the stock launcher.
- If Home is stuck, open Settings → Apps → Ananta TV → Uninstall (or use a computer with authorized ADB: `adb uninstall com.ananta.tvlauncher`). The stock launcher is not modified by this project.
- Keep the stock launcher installed. This project does not flash firmware, require root, or modify system partitions.

## Development notes

- Application ID: `com.ananta.tvlauncher`
- Minimum Android API: 23
- Target SDK: 28 for broad Android TV compatibility in this starter. Review target SDK and package visibility behavior before a Play Store release.
- The manifest declares `HOME`, `DEFAULT` and `LEANBACK_LAUNCHER`.
- No credentials, signing keys or secrets are needed to build the debug APK.


## Fix for the SDK setup workflow error

If your previous Actions run stopped at **Set up Android SDK** with `Warning: Failed to find package 'tools'`, upload this fixed project version. The workflow now asks the setup action for `platform-tools` only, then installs the exact Android platform and build-tools packages separately.
