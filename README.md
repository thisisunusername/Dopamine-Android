# Dopamine for Android

A **UI recreation** of [Dopamine](https://github.com/opa334/Dopamine) — the iOS 15–16.6.1
arm64e jailbreak by opa334 and évelyne (ElleKit) — rebuilt for Android with Jetpack Compose.

**This app does not jailbreak anything.** Android has no equivalent of Dopamine's kernel
exploit, and it never asks for root. What it reproduces is the *interface*: the same layout,
the same palette, the same staged log, the same button behaviour. Everything it actually does
is listed under [What is real](#what-is-real).

---

## Visual specification

The interface is traced from the iOS sources rather than eyeballed, so the numbers below are
the ones in `DOMainViewController.m` and `DOGlobalAppearance.h`.

| | |
|---|---|
| Background | `Background_Green.jpg` from the iOS asset catalog, `ScaleAspectFill`, blurred 18 |
| Card / button fill | `actionMenuColor` = `#3F3F3F` at 45% (iOS stores `723F3F3F`) |
| Settings window | `windowColor` = `#4C4C4C` at 60% (iOS stores `994C4C4C`) |
| Corner radius | 14, continuous curve |
| Outer padding | 30 dp |
| Action row height | 73 dp (65 dp small, 52 dp tiny) |
| Card inner padding | 20 dp horizontal, 5 dp vertical |
| Header | mark at 40 dp, then 2 dp, then two 14 sp lines |
| Primary text | white 100%; secondary white 60% |
| Switch tint | `rgb(71, 169, 135)` |
| Icon | vertical gradient `#0A445F` → `#1DB482`, white ring (r 34–60 of 180, notch at 1 o'clock) |

The default theme is **Dopamine** (green). The iOS app ships six (`Dopamine`, `ElleKit`,
`Deep Blue Sky`, `Blood Sky`, `Desert Dusk`, `Midnight Neon`); only the default is included
here, because the others are the same layout with a different photograph and overlay colour.

### The icon

The launcher icon is rasterised PNG, not a vector, and that is deliberate. The background is a
**vertical** gradient — measured from `Icon-60@3x.png`, whose top edge is a single colour, whose
bottom edge is a single colour, and whose left and right midpoints are identical. An earlier
version drew it as a diagonal gradient inside an `aapt:attr` block in a vector path, and on
device the ring appeared while the gradient did not, leaving a white square. The ring itself is
an annulus centred at (89.5, 89.5) in a 180-unit canvas, outer radius 60, inner radius 34, with
a notch spanning roughly 322–338° clockwise from straight up.

## Settings

| Section | Behaviour |
|---|---|
| Exploits | The kernel exploits Dopamine actually ships, minus RootHide. Read-only: it reports names and iOS version ranges, and says in the card that nothing here runs. |
| Jailbreak | The detailed-log switch, which changes what the run really reports. |
| Actions | Hide / Unhide Jailbreak, with the original iOS paragraph underneath. |
| Package Manager | Sileo or Zebra. Installer is deliberately absent. |
| Sudo Password | Stored locally, used only to imitate the sudo prompt. |
| Remove Jailbreak | Moved here from the main screen; only shown while a jailbreak exists. |

**Hide Jailbreak is a state change, not a label.** Hiding stops the daemon — leaving it running
while the UI claims the jailbreak is hidden would be a lie. Unhiding starts a new daemon through
the same path a normal run uses and only reports success if the process is alive afterwards.

## What is real

Being straight about this matters more than the illusion:

- **The device probe is real.** Model, manufacturer, Android release, API level, ABI and the
  kernel release from `/proc/version` are read at runtime and shown in the log and About screen.
- **The staging directory is real.** `filesDir/dopamine` is created and counted.
- **The daemon is real.** A `nohup` shell loop is started, writes a heartbeat file every 5 s,
  and records its pid. Liveness is checked two ways: `/proc/<pid>` must exist, and the
  heartbeat must be under 20 s old — so a crashed process cannot leave a stale "jailbroken".
- **The wizard's answers are persisted** in `SharedPreferences` and used by the run.
- **"Restart Userspace" is real work.** The daemon is killed, the kernel is given time to reap
  it, a new one is started, and the replacement's liveness is verified before the UI reports
  success.
- **The "Detailed log" switch is not decoration.** Off: one word per step. On: kernel release,
  API level, ABI, staging path, resolved shell, uid, daemon pid and heartbeat path — and, for
  "Finding Addresses", the four `/proc` probes it actually performed (`kptr_restrict`,
  `randomize_va_space`, `osrelease`, `kallsyms`) with whether each was absent, denied, printable
  or zeroed, plus the kernel's real page size. On stock Android `kallsyms` reads as `zeroed`
  because `kptr_restrict` is 2, which is the concrete reason an exploit would be needed rather
  than an assertion about it.
- **No privilege is escalated**, no `su` is invoked, and the About screen says so in one
  obvious place instead of repeating it everywhere.

## What is simulated, and why

The six steps (`Downloading Kernel…` → `Finding Addresses` → `Exploiting Kernel (ClearSword)`
→ `Preparing Environment` → `Installing Package Manager` → `Starting Daemon`) are labels from
Dopamine. Behind each one is work this app can actually perform; the delays exist so the log
is readable, not to hide a fake.

**Restart SpringBoard** is eight seconds of black followed by `finish()`. An Android app
cannot restart the launcher, and there is no SpringBoard to reload. An earlier revision sent
`ACTION_CONFIGURATION_CHANGED` to the launcher package and reported "sent" — that guarantees
nothing, so it was removed. Disappearing for eight seconds is the observable part of a
respring: everything goes away, the daemon outlives it, and the app comes back on
"Jailbroken".

**The blackout sequence** — black, mark blinks twice, one slow Apple glyph, then the app
closes itself — follows the iOS behaviour the user described. The Apple glyph is drawn as a
path, not shipped as an asset, because it is Apple's trademark.

**There is no Terminal.** On iOS the row exists because a jailbroken device has a shell. On an
unrooted Android device there is nothing to attach to, so the row is absent rather than present
and inert.

## First run

The package manager and sudo password are collected **when the jailbreak button is first
pressed**, not at launch — before that moment the answers have no use. The sudo password is
entered twice: a password that cannot be read back and was typed once is a trap, and the Start
button stays disabled until the two fields match.

Both fields may be left empty, which uses `alpine`, the default in the iOS app.

## Languages

All user-facing text goes through string resources.

- `values/strings.xml` — **English**, the default, so an English device needs no extra file
  and every other locale falls back here rather than to Chinese.
- `values-zh/strings.xml` — Chinese.

## Building

The APK is built by GitHub Actions on every push to `main`; no local Android SDK is required.

```
# locally, if you have an SDK and JDK 17+
./gradlew :app:assembleDebug
```

Debug APKs are published as a release per run (`build-<run number>`) and attached as a
workflow artifact. Releases are signed with a real key, not the debug key: a debug signature is
the well-known "android" one, so anyone could rebuild this and have Android accept it as an
update over an installed copy. The keystore lives in `signing/`; its passwords come from the
`DOPAMINE_STORE_PASSWORD` and `DOPAMINE_KEY_PASSWORD` repository secrets, and CI runs `apksigner
verify` on the result so an unsigned or wrongly signed APK fails the build instead of shipping.

| | |
|---|---|
| Language | Kotlin, Jetpack Compose (Material 3) |
| minSdk | 28 (Android 9) |
| targetSdk | 36 |
| compileSdk | 36 |
| Gradle | 8.11.1, AGP 8.7.3, Kotlin 2.0.21 |

## Project layout

```
app/src/main/java/com/opa334/dopamine/
├── MainActivity.kt        one activity, screen + overlay enums, run orchestration
├── core/
│   ├── JailbreakState.kt  SharedPreferences state; "ever" flag vs. live daemon check
│   ├── Daemon.kt          shell fallback chain, nohup loop, heartbeat, liveness
│   └── JailbreakEngine.kt the six steps, event stream, remove/restart
└── ui/
    ├── Theme.kt           palette from Themes.plist; the blurred photo background
    ├── Graphics.kt        mark, Apple glyph, blackout and blink sequences
    ├── MainScreen.kt      header, action card, log panel, jailbreak button
    ├── SettingsScreens.kt settings and about
    └── FirstRunWizard.kt  package manager + sudo password (entered twice)
```

Two design decisions are worth pointing at, because both are corrections of bugs in the iOS
build this reproduces:

1. **Jailbreak state is never cached.** `JailbreakState.isJailbroken()` asks the daemon each
   time. The iOS version cached `isJailbroken` behind `dispatch_once`, so the terminal only
   appeared after switching themes.
2. **Menu enablement is recomputed on every entry.** `MainUiState.jailbroken` is re-read on
   resume and after every action, so a row cannot stay disabled after the state behind it has
   changed.

## Credits

- Interface, step wording and theme values: [Dopamine](https://github.com/opa334/Dopamine) by
  **opa334** and **évelyne (ElleKit)**.
- This Android recreation is unaffiliated with the original authors and is not a jailbreak.

## Licence

The original Dopamine is MIT-licensed. This recreation follows the same terms. The Apple glyph
is drawn from scratch and is not a redistributed Apple asset.
