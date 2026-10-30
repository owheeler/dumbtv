# dumbtv home: Apple TV look build plan (TV1)

Status: **BUILT AND INSTALLED on TV1, 2026-10-27.** The plan below is kept for reference; read "As built" first.

## As built (2026-10-27)
- **Where the code is:** a separate git worktree, `%USERPROFILE%\dumbtv-appletv`, on branch `tv1-appletv` off 12c21f7.
  The retro (TV2) build is on branch `tv2-retro`, and its tree in `%USERPROFILE%\dumbtv` is untouched.
- **Shared pieces copied in:** `build.sh`, `Catalog.java` (the permission fix and the Appstore helpers), `Sleep.java`,
  and the Guard sleep/wake code, without sounds.
- **Backups:**
  - This build: `%USERPROFILE%\dumbtv-builds\appletv-2026-10-27\` (APK, patch, keystore).
  - The official 1.2 APK that was on TV1: `dumbtv-builds\tv1-before-appletv\`.
- **Decisions made at build time:**
  - Built-in scenery: 6 painted mountain and sky scenes, no image files, shuffled on every wake.
  - Sleep timer added: 15 minutes to 2 hours, with a warning panel 1 minute before.
  - 5-app dock, with names shown only on focus.
  - System sans-serif font (no Inter).
- **Layout:**
  - The shelf photo fills the screen and the dock sits at 60% of the height.
  - Scrolling down fades the shelf into a blurred, darkened copy, with a dark band behind the clock.
  - The grid is a fixed 5 columns (tiles are 304×171 px at 1080p), followed by the Settings, Options and Sleep cards,
    then the HDMI pills.
  - Move works across the dock and the grid. Up/down moves a tile 5 places.
- **New files:**
  - `Scenery.java`: the painted scenes and the blur.
  - `Shelf.java`: the cross-fade slideshow.
  - `TvMenu.java`: the dialogs.
  - `FocusColor` and the old background, accent and tile-size settings are no longer used. They still compile and do no harm.
- **Options:**
  - Change photo every: 15 seconds to 5 minutes, or never
  - Clock
  - Inputs row
  - Open inputs with
  - Appstore
  - Apps on home screen, Inputs on home screen
  - Reset
  - Open Amazon's home screen
- **Your own photos:** `adb push photo.jpg /sdcard/Android/data/tv.dumbtv.home/files/wallpapers/`, any number of them.
  They replace the scenery from the next return home. Uninstalling the launcher deletes them, so keep the originals on the PC.
- **Measured:** 76 MB PSS with the home screen showing (budget 90 MB). The APK is 62 KB.
- **Lesson:** don't `export MSYS2_ARG_CONV_EXCL='*'` before `install.sh --build`. If you do, apksigner gets a `/c/...`
  path and can't find `release.keystore`, and by then the uninstall has already happened. Set it only on adb commands.
- **Not tested on TV1:**
  - The sleep timer actually putting the TV to sleep. It's the same code that was verified on TV2.
  - Move, using the real remote.
  - The Menu-key menus.
- **Rollback:** `adb uninstall tv.dumbtv.home`, then either run `./launcher/install.sh` from `%USERPROFILE%\dumbtv`
  (official 1.2), or `adb install` the saved `official-1.2.apk`.
Base: humblemuzzu/dumbtv @ 12c21f7, launcher v1.2.

## Decisions

| Topic | Choice |
|---|---|
| Top shelf | Photo slideshow: your own photos, slow cross-fade, behind the dock |
| Tile shape | Keep 16:9 (Fire TV banners are made for it), rounded corners |
| Packaging | Replace the look (no style toggle). Roll back by reinstalling the official release |
| Clock | Small, top-right |
| Slideshow photos | Whatever is in the TV's `wallpapers/` folder (order: still open) |

## TV1 facts (checked 2026-10-27)

| | |
|---|---|
| Model / OS | AFTTI43, Fire OS 7.7.1.7 (Android 9, sdk 28), 32-bit |
| Address | `<tv1-ip>:5555` (DHCP, may change; MAC lookup with `arp -a`) |
| Screen | 3840×2160 panel, **UI drawn at 1920×1080**, density 320 (1 dp = 2 px) |
| Launcher now | official dumbtv home **1.2**, **no `home.conf`** (never customised), so there are no settings to carry over |
| debloat | done 2026-10-23. Restore snapshot in `%USERPROFILE%\dumbtv\report\`, extra backup in `%USERPROFILE%\dumbtv-backup-2026-10-23\` |

## Already done by the TV2 build (shared groundwork)

These live in the working tree of `%USERPROFILE%\dumbtv` (now committed on branch `tv2-retro`;
see "Branches" below). A copy is in `%USERPROFILE%\dumbtv-builds\retro-2026-10-27\`.

- **Toolchain on this PC**, nothing to install:
  - JDK 17 (portable): `%LOCALAPPDATA%\Programs\jdk17\jdk-17.0.21.0.2+1`. A second, MSI-installed JDK 17 also exists under `C:\Program Files\Microsoft`. Either works.
  - Android SDK: `%LOCALAPPDATA%\Android\Sdk`, with `platforms;android-25` and `build-tools;34.0.0`, licences accepted.
  - The Android emulator was **not** needed: building and testing on the TV over adb was quick enough. Skip it.
- **`build.sh` works in Git Bash on Windows:** it finds the SDK and JDK on its own, runs `d8`/`apksigner` through
  their jars, falls back to Python when `zip` is missing, and converts paths with `cygpath -m`. The "zip W … header
  mismatch" lines it prints are harmless (apksigner verifies OK).
- **`assets/` support** (`-A assets` in `aapt2 link`). Bundling Inter is now just dropping the file in.
- **Signing key:** `launcher\release.keystore` (git-ignored, backup copy in the folder above). **Both TV builds use it.**
  Lose it and every future update needs an uninstall first.
- **Catalog fix (keep for TV1):** apps whose launch activity needs a permission we lack (e.g. **AirPlay**, which needs
  `com.amazon.airplay.permission.LAUNCH`) are skipped. They could never open, and the official 1.2 shows them anyway.
- **Patterns worth reusing:**
  - `Ui.px()` grid sizing (1/270 of screen height) keeps everything proportional at 1080p.
  - `RetroMenu`'s custom `Dialog` approach replaces `AlertDialog` (restyle it for the tvOS look).
  - `Sound` (SoundPool) if TV1 wants subtle click sounds.
- **Sleep timer is proven** (`Sleep.java` plus the small `Guard` addition): `GLOBAL_ACTION_LOCK_SCREEN` puts these TVs into
  real standby ("Going to sleep by an accessibility service request"), and the remote wakes them normally. Amazon's own
  timer can't be opened by a third-party app (all entry points are signature|amazon). TV1 could get the same `[Sleep]`
  feature for free. **Open question: add it to TV1?**

## Branches (needs a decision before TV1 work starts)

Everything is uncommitted on `base`. The plan:
1. `base`: commit only the shared groundwork, i.e. `build.sh` (Windows plus assets) and the `Catalog` permission fix.
   Optionally `Sleep.java` and the `Guard` sleep/wake additions if TV1 gets the sleep timer.
2. `retro`: branch from `base` and commit the rest (TV2's look). That's exactly what is on TV2 now.
3. `appletv`: branch from `base`. TV1 work starts here.

A fork on your GitHub is optional (push only when you want an off-PC copy).

## Hard constraints (unchanged, now confirmed)
- Plain Java, no libraries, no `INTERNET`, compiled against API 25 (javac `--release 8 -Werror`).
- Keep `Keeper`, `Input`, the manifest permissions and the package id `tv.dumbtv.home` untouched (`restore.sh` relies on it).
  **Don't change `VERSION` or `APK_SHA256` in `version.env`:** `install.sh` uses them to download the official release for rollback.
- No `RenderEffect` blur (API 31). Precompute it.
- Budget: the retro build uses **44 MB** with home showing. Target 90 MB or less for TV1 with the photo shelf.
- Android 9 with `RECEIVE_BOOT_COMPLETED` capped at maxSdk 27, so **`BootReceiver` never fires on Fire OS 7.** Use
  `Guard.onServiceConnected` as the "boot" signal (the retro build already does).

## Target layout

```
┌──────────────────────────────────────────────────────────────┐
│  [photo, full-bleed, cross-fades every N s]           9:41  │  ← clock overlay
│                                                              │
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ bottom scrim gradient ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│
│  [App 1]  [App 2]  [App 3]  [App 4]  [App 5]   ← dock       │
├──────────────────────────────────────────────────────────────┤
│  (scrolling down: shelf slides away, background becomes a    │
│   blurred, darkened copy of the current photo)               │
│  [ .. ]  [ .. ]  [ .. ]  [ .. ]  [ .. ]        ← all apps,  │
│  [ .. ]  [ .. ]  [ .. ]  [Settings] [Options]    5 columns  │
│  Inputs:  ( HDMI 1 )  ( HDMI 2 )  ( HDMI 3 )  ( HDMI 4 )     │
└──────────────────────────────────────────────────────────────┘
```

- **Dock** = the first 5 apps in your order. The grid holds the rest.
- **Settings and Options become tiles** at the end of the grid. Menu (☰) still opens Options when focus isn't on a tile.
- **Focus:** tvOS "lift" (scale about 1.12 with a spring, soft shadow, faint glare). The name fades in under the focused tile only.
- **Inputs:** TV1 has HDMI 1–4 (same model as TV2), shown as a compact row of rounded pills.
- **Alphabetical default order** puts Amazon Music first. Set the order once with Move after install.

## Phases

### Phase 0: Branch setup (the toolchain is done)
1. Make the branch decision above, then `git switch appletv`.
2. Build the unmodified `base` once to confirm: `cd launcher && ./build.sh` (prints `built build/dumbtv-home.apk`).

### Phase 1: Visual foundation (`Ui.java`, `styles.xml`)
- Tokens: background `#0B0B0D`, text white/72%/45%, radius 12dp tiles and 16dp panels, type scale 28/22sp titles,
  15sp labels, 18sp clock. `styles.xml` window background is set to match (the retro build uses `@color/console`; same idea).
- A spring interpolator plus `lift(view, on)`. tvOS list focus: focused = white fill with black text.
- Font: system sans-serif first. Inter (OFL) is now just `assets/fonts/` plus `Typeface.createFromAsset`.

### Phase 2: Tile lift (`Tile.java`)
- No accent ring. `setElevation` with a round-rect outline for the shadow, a pre-allocated diagonal glare gradient on focus,
  and the label fades in below the focused tile only. Move mode: an iOS-style jiggle on the moving tile.
- Keep a single-pass `onDraw` (the retro `Tile` is a good template for structure and the lazy bitmap cache in `Item`).

### Phase 3: Layout, dock and grid (`Home.java`, `Grid.java`)
- View tree: `root` = { shelf layer, blurred backdrop, scrim, `ScrollView{ spacer(≈55%), dock, grid, inputs }`, clock }.
- `Grid`: fixed 5 columns. At 1080p a tile is ≈320×180 px, which is the native banner size.
- Reuse from the retro `Home`:
  - `focusSearch` with `nearest()` for row-to-row moves.
  - The `keyMove` flag pattern, so move sounds and effects only fire for D-pad moves.
  - The `current` static plus `woke()` hook.
- Settings and Options as built-in tiles at the end of the grid.

### Phase 4: Shelf slideshow (new `Shelf.java`, `Blur.java`)
- Reuse `Config.wallpapers()` (sorted by name) for the list. Two `ImageView`s cross-fade (1 s) every `shelf.interval`
  (default 30 s), only while resumed, and release in `onTrimMemory`.
- `Blur`: downscale to 1/8, then a box blur and a dark overlay, done once per photo off-thread.
- `push-photos.ps1`: ffmpeg resize to 1920×1080 JPEG, then `adb push` to `…/files/wallpapers/`.
  **Uninstalling the launcher deletes that folder**, so keep the originals on the PC.

### Phase 5: Chrome
- Options in a tvOS Settings style. The tile menu, rename and confirm dialogs in custom `Dialog`s (copy the
  `RetroMenu` structure, restyled with white-focus rows and rounded translucent panels).

### Phase 6: Polish and ship
- Entry animation must not delay first focus (Guard measures time-to-shown).
- Check: memory via `dumpsys meminfo tv.dumbtv.home` (90 MB or less). Smoothness via `dumpsys gfxinfo tv.dumbtv.home` while sweeping the grid.

## Install on TV1 (tested procedure from TV2)

Run in **Git Bash**. `MSYS2_ARG_CONV_EXCL='*'` stops Git Bash from rewriting `/sdcard/...` paths.

```bash
export MSYS2_ARG_CONV_EXCL='*' ADB_SERIAL=<tv1-ip>:5555
adb connect $ADB_SERIAL
# 1. back up launcher settings (TV1 has none today; harmless if the file is missing)
adb pull /sdcard/Android/data/tv.dumbtv.home/files/home.conf ~/dumbtv-backup-2026-10-23/home-conf-before-appletv.conf
# 2. first switch only: the official build is signed with a different key
adb uninstall tv.dumbtv.home
# 3. build, install, re-hook HOME (rerun once if it says "did not bind the HOME guard")
cd ~/dumbtv && ./launcher/install.sh --build
```

Later updates of your own build: just step 3 (same key, no uninstall). It reinstalls in place and re-hooks HOME.
Each reinstall restarts Guard, which also counts as a "wake".

### Checking it remotely
- **Screenshots:** `adb exec-out screencap -p` returns junk on these TVs (a wrapper prints text first). Use:
  `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png && adb shell rm /sdcard/s.png`.
- **A black screenshot usually means the TV is asleep:** `adb shell dumpsys power | grep mWakefulness`.
  `adb shell input keyevent KEYCODE_WAKEUP` wakes it, and `KEYCODE_SLEEP` puts it back.
- **Logs:** `adb shell logcat -d -t 400 | grep -iE 'tv.dumbtv|FATAL|Permission Denial'`.
- **Long key holds** (e.g. MENU for 3 s) can't be simulated with `adb shell input`. Test those with the real remote.

## Rollback (TV1)

```bash
export MSYS2_ARG_CONV_EXCL='*' ADB_SERIAL=<tv1-ip>:5555
adb uninstall tv.dumbtv.home
cd ~/dumbtv && ./launcher/install.sh            # official 1.2, checksum-verified download
```

Full return to Amazon's launcher and packages: `./scripts/restore.sh` (unchanged, it calls `install.sh --undo`).

## Still open
- Slideshow order: by filename, shuffled, or by time of day (`morning-*` etc.).
- Add the proven `[Sleep]` timer to TV1 too?
- Number of dock apps (plan: 5) and whether the dock shows labels.
- Inter or system sans-serif.
