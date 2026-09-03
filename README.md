# Gate

An Android app that puts a ten-second pause in front of the apps you choose.

You tap Instagram (or whatever is on your list). Instead of Instagram, a pale blue
screen asks something of you first. Which thing is your choice, in setup:

| Task | What it asks |
| --- | --- |
| **Breathe** | Ten seconds. **INHALE** while a line sweeps up the screen and the number counts 5 → 1, then **EXHALE** as it comes back down counting 5 → 1 again. Nothing to press. |
| **Two sums** | Two problems, one each from two of adding, subtracting, multiplying and dividing. Type each answer. A wrong one keeps the same problem. |
| **A chapter of Tehillim** | A short chapter in Hebrew, at random from the ten under 250 characters. *I have read it* appears after five seconds. |

Only when the task is done do two buttons appear.

Websites work the same way: add `youtube.com` to the list and the gate stops you
when that site opens in a browser, whichever browser it is.

The app is never named on this screen — its icon is the only cue. Ten seconds of
breathing works better without the name of what you are reaching for in front of
you.

Three lines appear with the buttons, so the decision is made against what the day
has actually looked like:

```
Last used 2 hours ago
Reached for 7 times in the last 24 hours
You turned back 5 of them
```

The buttons are:

- **I don't want to use the app** — the large button. Leaves the app and closes it.
- **Continue to the app** — the small, quiet one underneath. Opens the app you tapped.

There is no way past the countdown: back does nothing while it runs.

## How it works

| Piece | File | What it does |
| --- | --- | --- |
| Foreground watcher | `GateAccessibilityService.kt` | An accessibility service that hears `TYPE_WINDOW_STATE_CHANGED` and, when the app coming forward is on your list, launches the gate over it. |
| The pause screen | `GateActivity.kt` | Runs one 10s animator that drives both the line and the two counts of five, then reveals the buttons. |
| The line | `SweepLineView.kt` | A custom view drawing a full-width line at a height set by `progress` (0 = bottom, 1 = top) — the pacer to breathe along with. |
| The tasks | `BreathTask.kt`, `MathTask.kt`, `PsalmTask.kt` | One `GateTask` each: shown, started, and calling back once when satisfied. |
| Tehillim | `PsalmRepository.kt`, `assets/psalms_he.json` | All 150 chapters from the Westminster Leningrad Codex; only those under `MAX_CHARS` are ever drawn from. |
| Your list | `AppPickerActivity.kt`, `Prefs.kt` | Every launchable app, with the guarded ones ticked; stored in `SharedPreferences`. |
| Setup | `MainActivity.kt`, `SettingsNavigator.kt` | The first-run walkthrough, and the deep links onto each permission's settings page. |
| Websites | `SiteGuard.kt`, `SiteListActivity.kt` | Turning what you type into a host, spotting browsers, and finding the address bar inside one. |
| The record | `UsageLog.kt`, `StatsActivity.kt` | A rolling 24-hour log per app and per site, and the page that shows all of it. |

### The numbers

Every gate is recorded against one target: the app's package, or the site written
as `site:youtube.com` (`GateTarget`). So the counts are per app **and** per site —
guarding both the YouTube app and youtube.com keeps two separate tallies.

Three lines appear on the gate itself, and **See the numbers** on the setup screen
opens the whole log: totals across the last 24 hours, then a row per app and site
sorted by how often it was reached for. Each row carries one bar — the share of
reaching-for that was turned back — on a neutral track. One measure, one mark; the
counts are written out beside it, so nothing is encoded in colour alone.

Anything the log remembers is listed even if it has since been taken off the
guard list: it still happened today.

### Websites

A site is caught by reading the browser's address bar through the accessibility
service. Three limits on that, all in `GateAccessibilityService.checkBrowser`:

- **Only while the website list is not empty.** With no sites guarded, the window
  content is never touched at all — the app-only setup reads nothing.
- **Only in browsers.** A known list of browser packages plus a name check for
  forks; every other app is left alone.
- **Only the address**, at most once every 400ms, and it is matched and discarded.
  No page content is read, and no URL is stored.

`youtube.com` guards the host and everything under it — `m.youtube.com`,
`music.youtube.com` — but not `myyoutube.com`.

Declining on a site steps back off the page and leaves the browser, but does
**not** kill it: your other tabs would go with it. Declining on an app still
closes the app.

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

### The Hebrew text

`assets/psalms_he.json` is the Westminster Leningrad Codex, via Open Scriptures
`morphhb`, with cantillation marks stripped and the vowel points kept. All 150
chapters are bundled so the length limit is a one-line change:
`PsalmRepository.MAX_CHARS`, at 250, gives ten chapters — 93, 100, 117, 120, 123,
128, 131, 133, 134 and 150. Lowering it to 100 leaves only Psalm 117. See
`tools/` for how the asset is regenerated.

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

Nothing leaves the phone, and nothing is written to a network at any point.

Guarding websites needs the accessibility service to read the browser's address
bar, so `canRetrieveWindowContent` is now `true` and Android will warn you about
that when you switch the service on. The warning is accurate about what the
service *could* do; what it actually does is bounded by the three limits under
**Websites** above. If you guard no websites, it reads nothing.

The only stored data is your app list, your website list and the
passes and the 24-hour usage counts, in this app's own `SharedPreferences`. Nothing
older than 24 hours is kept, apart from the timestamp of the last entry into each
app.
