# Gate

An Android app that puts a ten-second pause in front of the apps you choose.

You tap Instagram (or whatever is on your list). Instead of Instagram, a full-screen
count appears: a line sweeps up the screen while the number counts 5 → 1, then the
line comes back down while it counts 5 → 1 again. Only then do two buttons appear:

- **I don't want to use the app** — the large button. Leaves the app and closes it.
- **Continue to the app** — the small, quiet one underneath. Opens the app you tapped.

There is no way past the countdown: back does nothing while it runs.

## How it works

| Piece | File | What it does |
| --- | --- | --- |
| Foreground watcher | `GateAccessibilityService.kt` | An accessibility service that hears `TYPE_WINDOW_STATE_CHANGED` and, when the app coming forward is on your list, launches the gate over it. |
| The pause screen | `GateActivity.kt` | Runs one 10s animator that drives both the line and the two counts of five, then reveals the buttons. |
| The line | `SweepLineView.kt` | A custom view drawing a full-width line at a height set by `progress` (0 = bottom, 1 = top). |
| Your list | `AppPickerActivity.kt`, `Prefs.kt` | Every launchable app, with the guarded ones ticked; stored in `SharedPreferences`. |

### Passes

Pressing **Continue to the app** hands out a *pass*, and the count does not come back
while you are in that app — no timer runs out under you, no second gate when you move
between screens or follow a link out and back.

The pass is spent once you have properly left: when the app comes forward again after
30 seconds or more somewhere else, the next open gets the gate. That 30-second grace
is what stops a glance at a notification from counting as leaving.

### Closing the app

"I don't want to use the app" does two things: the accessibility service performs
the global Home action to leave the app, then calls
`ActivityManager.killBackgroundProcesses()` on it once it is in the background.
Android does not let one app force-stop another outright, so this is as close to
"closed" as a normal app can get: the process is killed, though the app may still
appear as a card in Recents until the system prunes it.

## Setup on the phone

Gate installs into the normal app drawer like any other app. Opening it the first
time goes straight to the list of apps on the phone so you can tick what to guard;
after that the main screen has three steps:

1. **Turn on the Gate service** — Accessibility settings → Gate → On. Without this
   the app cannot tell which app you are opening.
2. **Allow display over other apps** — this is what lets the gate reliably come to
   the front from the background on Android 10 and later. Skipping it means the
   gate may silently fail to appear on some phones.
3. **Choose the apps** — the same picker, reachable any time, with a search box.
   Ticks save as you make them.

Use **Preview the gate** on the main screen to see the countdown without opening a
guarded app.

## Building

Standard Android Studio project — open the folder and hit Run, or from a machine
with the Android SDK installed:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # with a device or emulator attached
```

- minSdk 26 (Android 8.0), targetSdk 35, Kotlin, view binding, Material 3.
- Requires `local.properties` with `sdk.dir=/path/to/Android/Sdk`, or the
  `ANDROID_HOME` environment variable.

> Note: this project has not been compiled — the environment it was written in has
> no Android SDK and could not reach `dl.google.com` to fetch one. Expect the usual
> first-build dependency download, and treat the first `assembleDebug` as the real
> check.

## Privacy

Nothing leaves the phone. The accessibility service is configured with
`canRetrieveWindowContent="false"`: it is told which app has come to the front and
nothing about what is on the screen. The only stored data is your app list and the
passes, in this app's own `SharedPreferences`.
