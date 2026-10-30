# dumbtv home: retro "Cartridge Shelf" build plan (TV2, a kid's room)

Status: **BUILT AND INSTALLED on TV2, 2026-10-27.** Base: humblemuzzu/dumbtv @ 12c21f7, launcher v1.2.
Sibling plan: `APPLETV-PLAN.md` (TV1).

## As built (differences from the plan below)

- **Title** on the start screen: `RETRO-TV` / `HOME CONSOLE` (not DUMBTV). Sound volume 0.25 (`Sound.VOLUME`).
- **Sleep timer works:** a 1-minute test ended in real standby, logged as "Going to sleep by an accessibility service
  request". The fallback card was not needed. Touch `/sdcard/Android/data/tv.dumbtv.home/files/debug` to get a hidden
  `1 MIN (TEST)` option. Delete it afterwards.
- **Start screen:** triggered by `Guard.onServiceConnected` (boot or reinstall) and screen-on. `Home.woke()` covers
  a screen-on that arrives after `onResume`. Auto-dismisses after 15 s.
- **AirPlay hidden:** apps whose launch activity needs a permission we don't hold are skipped by `Catalog`.
- **No emulator was used.** Everything was built here and tested on TV2 over adb.
- **Not verified remotely:** holding MENU for 3 s for parent mode (adb can't simulate a long hold). Check with the real remote.
- `version.env` was deliberately left alone, because rollback via `install.sh` downloads the release named there.
- Memory: 44 MB with home showing.
- Files: `Home`, `Tile`, `Ui`, `Options`, `Config`, `Catalog`, `Item`, `Guard` (additions only), new `Sleep`,
  `Sound`, `RetroMenu`, `tools/make_sounds.py`, `assets/fonts/`, `res/raw/`, `build.sh` (Windows plus assets).
- **Backup** of source changes, APK and signing key: `%USERPROFILE%\dumbtv-builds\retro-2026-10-27\`
  (`retro-tracked-changes.patch` + `retro-untracked-files.tar` apply on top of commit 12c21f7).

The original plan follows, kept for reference.

## Decisions

| Topic | Choice |
|---|---|
| Concept | A, Cartridge Shelf (chosen over the arcade list and the 8-bit world map) |
| Top bar name | `PLAYER 1` |
| Sounds | On by default (low volume), with an off switch in Options |
| Kid-proofing | Yes. Options, TV setup and the cart menu need **parent mode** (hold Menu 3 s) |
| Sleep timer | A `[SLEEP]` button that is **always** available, outside kid-proofing |
| Start screen | Yes. "PRESS START" when the TV is switched on (never on HOME presses) |

## Concept

Apps are game cartridges standing on a console shelf. The focused cartridge lifts out with a yellow
pixel outline, and a blinking `▶ PRESS OK TO PLAY` sits under the shelf. Opening an app slides the cartridge
down into the slot. HDMI inputs are "controller ports". A pixel font is used throughout, with 8-bit sounds
and optional faint CRT scanlines.

```
┌──────────────────────────────────────────────────────────────┐
│ PLAYER 1                              SLEEP 29:12   7:42 PM  │  ← countdown only while a timer runs
│        ┌──────┐                                              │
│ ┌────┐ │▓▓▓▓▓▓│ ┌────┐ ┌────┐ ┌────┐                         │  ← focused cart lifted,
│ │art │ │ art  │ │art │ │art │ │art │  …                      │    yellow pixel outline
│ │    │ │      │ │    │ │    │ │    │                         │
│ └||||┘ └||||||┘ └||||┘ └||||┘ └||||┘                         │
│ ════════════════ console slot / shelf ══════════════════════ │
│                 ▶ PRESS OK TO PLAY   (blinks)                │
│ PORTS: ● HDMI 1  ○ HDMI 2                          [SLEEP]   │  ← [OPTIONS] [SETUP] appear
└──────────────────────────────────────────────────────────────┘    only in parent mode
```

## Constraints
- Plain Java, no libraries, no `INTERNET`, compiled against API 25 and running on Fire OS 7.
- `Keeper`, `Input`, the manifest permissions, the package id `tv.dumbtv.home` and `install.sh`'s HOME hook
  stay untouched. **`Guard` gets one small, additive change** (the sleep action and warning overlay, below).
  Its window-watching and HOME takeover logic are not modified.
- Budget: memory 80 MB or less with home showing (60–70 MB today). Janky frames under 5% while sweeping the shelf.
  The launch animation adds 250 ms at most before the app starts.

## Visual spec

| Token | Value |
|---|---|
| Background (console body) | `#1D1A2B` |
| Shelf / slot | `#0E0C16` |
| Cartridge plastic / teeth | `#55535E` / `#3C3A44` |
| Highlight (outline, prompt, top bar) | `#F8D030` |
| Text / muted | `#FFFFFF` / `#9A97AD` |
| Connected port | `#5EE07A` |
| Font | Press Start 2P (SIL OFL, ≈80 KB) at multiples of its 8 px grid so it stays crisp |

- **Cartridge label = the app's own banner** (recognisable for a kid). Optional "pixelated labels" draw
  it from a 1/4-size cached bitmap with `setFilterBitmap(false)`. Computed once per app.
- **Corners are stepped (pixel), not rounded**, on the outline, the windows and the buttons.
- **Motion is "8-bit":** the lift uses a 3-step interpolator (about 120 ms), and blinks are a hard 1 Hz on/off.

## Findings from TV2 (checked 2026-10-27, read-only)

The launcher **cannot open Amazon's own sleep timer.** Every entry point is limited to Amazon-signed apps:

| Entry point | Guarded by | Level |
|---|---|---|
| `quicksettings/.StartQSActivity` (`…action.SLEEP_VIEW`) | `inputpreference.permission.LAUNCH_INPUTS` | signature\|amazon |
| `quicksettings/.StartBroadcastReceiver` | `quicksettings.permission.START` | signature\|amazon |
| `settings.v2/.sleep.SleepTimerBroadcastReceiver` (`…action.SLEEP`) | `CALL_GO_TO_SLEEP` | Amazon |
| `DisplayAndSoundsActivity` | `LAUNCHER_SETTINGS` | Amazon |

So the launcher runs **its own sleep timer**. The way to put the TV to sleep is `Guard` (already a
bound accessibility service on this TV) calling `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. That's API 28,
which is exactly Fire OS 7, and AOSP implements it as `PowerManager.goToSleep`. No new permission is needed. **This has to
be confirmed on the real TV in Phase 0b** (it should go to standby with sound stopping, not just a black screen).

Not affected by anything here: holding HOME on the remote still opens Fire OS's quick menu, which includes
Amazon's own Sleep Timer.

## Phases

### Phase 0: Shared setup (same as APPLETV-PLAN Phase 0)
Install the toolchain, fork, apply the Windows `build.sh` patch, build the stock launcher and run it in the emulator.
Do this once on a shared `base` branch, then branch `retro` from it (and `appletv` from it too).

### Phase 0b: Sleep spike on TV2 (do this first. It decides the sleep design)
- Throwaway debug build: a broadcast receiver that, given a debug intent, calls
  `Guard.running.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`.
- On TV2, while an app is playing video: `adb shell am broadcast -n tv.dumbtv.home/.DebugSleep`.
- **Pass:** the TV goes to standby (backlight off, audio stops) and the remote's power button wakes it normally.
- **Fail fallback:** the `[SLEEP]` button opens a retro help card: "HOLD ⌂ HOME → SLEEP TIMER". That's Amazon's
  timer through the quick menu, which a kid-proofed launcher can't block anyway.
- Roll back by reinstalling whatever was there before (back up `home.conf` first, as always).

### Phase 1: Assets and build
- Add an `assets/` dir and `-A assets` to `build.sh`'s `aapt2 link`.
- `assets/fonts/PressStart2P.ttf` and its OFL licence text.
- **Sounds, made by us:** a small Python script synthesises square-wave blips, writing `move.wav`, `select.wav`,
  `back.wav`, `error.wav`, `start.wav` (a short fanfare for the start screen) and `sleep.wav` (a descending "power down")
  into `res/raw/`, each under 10 KB at 22 kHz mono. Commit the script and its output.

### Phase 2: Theme foundation (`Ui.java`)
- Retro tokens, `Ui.pixel(context, sizeStep)` for pixel-font `TextView`s.
- `RetroWindow` drawable: a dark blue box with a white 2-step pixel border (the classic RPG dialog), used for
  the menus, Options, rename, the sleep picker and the start screen.
- A stepped interpolator and a blink helper. It ticks only while Home is resumed and is removed in `onPause`.

### Phase 3: Cartridge tile (`Tile.java`)
- Single-pass `onDraw`: the plastic body with grip ridges, the inset label (banner, pixelated banner, or icon plus
  pixel-font name) and teeth along the bottom.
- Focus: lift by about 12% of the height and draw the stepped yellow outline. Move mode blinks the outline, and ◀ ▶ swaps it
  (the existing `shift()`).
- Inputs ("ports"): small pixel boxes, `● HDMI 1`, with the connected dot in green.

### Phase 4: Home layout (`Home.java`)
- Top bar: `PLAYER 1` on the left. On the right, `SLEEP mm:ss` (only while a timer runs) and a pixel clock.
  This replaces the big clock and date block.
- The shelf is the existing horizontal strip with centred scrolling. The slot bar and the blinking prompt sit under it.
- Bottom row: the ports, then `[SLEEP]` on the right. In parent mode `[OPTIONS]` and `[SETUP]` appear next to it.
  Focus order: shelf ↓ bottom row.
- The shelf only: the grid layout is dropped from the UI.
- Optional **scanlines**, baked into the background bitmap (not a full-screen overlay). Default **off**.
  Measure with gfxinfo before turning them on.

### Phase 5: Sound and launch animation
- `SoundPool` loaded in `onCreate` and released in `onDestroy`. Blip on D-pad focus moves only (not programmatic focus),
  select on OK, back on Back. **Default on**, low volume. Option `sounds=on|off`.
- Launch: the cart slides into the slot (200 ms) and the intent fires as it lands. Skipped when
  `animator_duration_scale` is 0.

### Phase 6: Sleep timer (new `Sleep.java`, small addition to `Guard`)
- `[SLEEP]` opens a `RetroWindow` picker: `15 / 30 / 45 / 60 / 90 MIN`, `+15 MIN` and `OFF` (the last two only while
  a timer runs). It's always available and never behind parent mode.
- State: the end time is stored in `home.conf` (`sleep.at=<epoch ms>`), so the countdown survives the launcher
  being killed. Two `AlarmManager.setExactAndAllowWhileIdle` alarms go to a `SleepReceiver`: one at T−60 s, one at T.
- **T−60 s warning:** `Guard` shows a small retro box over whatever is playing, `GAME OVER IN 60 · ⌂ HOME TO
  EXTEND`, using its existing `TYPE_ACCESSIBILITY_OVERLAY` window. It's non-focusable, so it doesn't steal the remote,
  and it disappears after 10 s. Extending = HOME → `[SLEEP]` → `+15 MIN`.
- **At T:** play `sleep.wav`, call `GLOBAL_ACTION_LOCK_SCREEN` through `Guard`, and clear `sleep.at`.
- Cleared by `BootReceiver` (already exists) and on screen-on, so a stale timer never fires after the TV is turned back on.
- If `Guard` isn't bound when the alarm fires: no action, and the next Home shows `SLEEP TIMER FAILED`. Never silently pretend.

### Phase 7: Kid-proofing (parent mode)
- **Hold Menu (☰) 3 s anywhere on Home** → `PARENT MODE` badge in the top bar, plus `[OPTIONS]` and `[SETUP]` show
  and the cart menu (MOVE / RENAME / HIDE / INFO) is enabled. Ends after 2 minutes idle, on leaving Home, or on
  holding Menu again.
- While locked: a short Menu press or a long-press of OK on a cart does nothing (a soft `error.wav`). Back on Home
  still jumps to the first cart.
- `[SLEEP]`, opening apps and the ports always work.
- Parent mode is a speed bump for a kid, not security: anyone with adb or the Fire OS quick menu can still change settings.

### Phase 8: Start screen ("PRESS START")
- **When:** the first time Home shows within 60 s of the screen turning on (wake from standby) or of a boot.
  `Guard` already runs all the time, so it records `ACTION_SCREEN_ON`, and `BootReceiver` covers cold boots.
  It never shows on ordinary HOME presses.
- **What:** a full-screen layer *inside* Home (not a separate activity, so Guard's takeover timing is unchanged):
  a pixel "DUMBTV" logo that drops in, then `PRESS START` blinking, then the `start.wav` fanfare on OK. Any key dismisses it.
  It auto-dismisses after 15 s to the shelf. The first cart is focused once it's gone.
- Option: `start_screen=on|off` (default on).

### Phase 9: Retro menus (`Options.java`, cart menu, rename)
- The cart menu and rename go in a `RetroWindow` with a `▶` cursor (replacing the `AlertDialog`). Rename keeps the system keyboard.
- An OPTIONS screen in the same style: Sounds, Start screen, Scanlines, Pixelated labels, Clock 12/24, Ports row,
  Open inputs with, show/hide apps, Reset, Open Amazon's home screen. Removed from the UI: accent, tile size, layout,
  labels, background/wallpaper. Unknown keys in `home.conf` must still load.

### Phase 10: Polish and ship to TV2
- Bump `version.env`: `VERSION_NAME=1.2-retro.1`. Keep the existing tests. Add host tests for the sleep-alarm maths
  (end time, warning time, +15, clearing) and the stepped interpolator, and run `bash -n` on the scripts.
- In the emulator: visual check, `dumpsys meminfo tv.dumbtv.home` 80 MB or less, `dumpsys gfxinfo` sweep.
- On TV2: a full sleep-timer run (pick 15 min, see the warning, TV sleeps, wakes into PRESS START), parent mode
  enter/exit, and HOME from inside an app (no start screen).

## Install and rollback (TV2)

TV2: `<tv2-ip>` (DHCP, the IP may change). Uninstalling deletes `home.conf`, so back it up first.

```bash
adb -s <tv2> pull /sdcard/Android/data/tv.dumbtv.home/files/home.conf home-tv2.conf
adb -s <tv2> uninstall tv.dumbtv.home
ADB_SERIAL=<tv2> ./launcher/install.sh --build   # on the retro branch. Rerun once if "did not bind the HOME guard"
adb -s <tv2> push home-tv2.conf /sdcard/Android/data/tv.dumbtv.home/files/home.conf

# rollback to official dumbtv home
adb -s <tv2> uninstall tv.dumbtv.home
ADB_SERIAL=<tv2> ./launcher/install.sh
adb -s <tv2> push home-tv2.conf /sdcard/Android/data/tv.dumbtv.home/files/home.conf
```

The `appletv` and `retro` builds share one signing key (`release.keystore`). Back it up.

## Still open
- Pixelated labels on or off by default (judge on the real TV; some logos get hard to read).
- Sleep picker presets: 15/30/45/60/90 is the plan. Add "end of this episode" style options? (Not possible:
  the launcher can't see what's playing.)
