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
| Setup | `MainActivity.kt`, `SettingsNavigator.kt` | The first-run walkthrough, and the deep links onto each permission's settings page. |

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

Gate installs into the normal app drawer like any other app, and the first open
walks the whole setup through in order:

1. **The phone's app list** opens straight away — tick what to guard, tap Done.
2. **"Let Gate see which app you open"** — a dialog explaining the accessibility
   service, with a button onto the Accessibility settings page (deep-linked to
   Gate's own row where the phone supports it).
3. **"One more: display over other apps"** — same again for the overlay
   permission, landing directly on Gate's toggle.

Neither permission can be granted from inside an app; Android insists the user
turns them on themselves, so all any app can do is explain and open the right
page. Declining ends the walkthrough — the three cards on the main screen stay as
the way to grant them later, and to change the app list at any time.

If the accessibility toggle is **greyed out**, that is Android 13+ blocking
"restricted settings" for apps installed outside the Play Store. Gate notices when
you come back without having switched it on and offers a shortcut to App info,
where ⋮ → *Allow restricted settings* unblocks it.

Use **Preview the gate** on the main screen to see the countdown without opening a
guarded app.

Some phones (Samsung, Xiaomi, OnePlus, Huawei) kill background services
aggressively. If the gate stops appearing after a day, exempt Gate from battery
optimisation in Settings → Apps → Gate → Battery.

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
