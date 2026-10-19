# TV launcher

A custom home screen for an Android TV. Built by GitHub Actions on every push; the APK is attached to the newest release.

## Install on the TV (from Termux, TV already connected with adb)

    curl -L -o launcher.apk https://github.com/ageed77/tv-launcher-/releases/latest/download/tv-launcher.apk
    adb install -r launcher.apk
    adb shell cmd package set-home-activity com.ageed77.tvlauncher/.MainActivity

If the last command isn't supported, press the Home button on the remote and choose "TV Launcher" with "Always".

## Power button

The power pill opens a Sleep / Turn off pop-up. It presses the TV's own power menu buttons through an accessibility service. Turn it on once (after updating the app, run the first line below with an empty value `""` and then both lines again, so the service reloads):

    adb shell settings put secure enabled_accessibility_services com.ageed77.tvlauncher/com.ageed77.tvlauncher.PowerService
    adb shell settings put secure accessibility_enabled 1

## Undo

    adb uninstall com.ageed77.tvlauncher

## Settings

- Weather location: `LAT` and `LON` at the top of `MainActivity.java`.
- Xbox tile: the first press asks which HDMI input is your Xbox. Hold OK on the tile to change it.
