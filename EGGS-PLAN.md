# dumbtv home: "Secret Arcade" easter egg plan (TV2 retro launcher)

Status: PLAN ONLY, decisions confirmed (2026-10-27). Builds on the retro Cartridge Shelf launcher in `RETRO-PLAN.md`.

## Goal
Add a small, easy-to-miss pixel egg in a corner of the TV2 home screen. Selecting it and pressing OK three
times hatches it into a SECRET ARCADE with three short, original retro games:

| Game | Inspired by | One-line pitch |
|---|---|---|
| **SNAKE BYTE** | snake | Eat berries, grow longer, don't bite yourself. |
| **BRICK BOP** | brick breaker | Paddle and ball against pixel-art brick pictures. |
| **STAR SWARM** | fixed space shooter | Aliens swoop in on curved paths, line up in formation, then dive at you. |

All three are original games in their genres: new names, sprites, levels and enemy patterns. They don't
copy any specific game's characters, levels or signature mechanics. For example, STAR SWARM has no
tractor-beam ship capture.

## Constraints (same rules as the launcher)
- Plain Java, no libraries, no new permissions, still **no INTERNET**. Compiled by the existing `build.sh`
  (javac `--release 8`, API 25 android.jar, d8, apksigner, `release.keystore`).
- Played with the Fire TV remote's D-pad, OK, BACK and Play/Pause. A Bluetooth gamepad also works
  (its D-pad and A/B buttons arrive as standard key events).
- **The remote can't press the D-pad and OK at the same time reliably** (they sit on the same ring). No game
  needs both at once. STAR SWARM always auto-fires.
- The sleep timer keeps working during games. Its alarms run through Guard whatever is on screen, and the
  "GAME OVER IN 60" warning shows over the game. The game also pauses itself.
- HOME always returns to the shelf, as it does now through Guard.
- Each round is short (1–5 minutes) and can be paused at any point.

## The egg
- **Where:** bottom-left corner, left of `PORTS:`, inside the screen's 16-cell side margin.
- **How it looks:** a 7×9-cell pixel egg in `Ui.SLOT` with a few `Ui.PLASTIC` speckles, drawn at about 35%
  opacity, so it reads as a background decoration.
- **How you reach it:** it's only reachable by pressing LEFT from the leftmost bottom-row item: the first port,
  or SLEEP if no ports are shown. This hooks into the existing `focusSearch` override in `Home.build()`.
  It's never focused by default and never reached with UP or DOWN.
- **When it has focus:** it goes to full colour and wobbles every 1.5 s. The name line shows `? ? ?` instead
  of a hint.
- **Cracking it:** each OK press adds a crack (sfx_crack), and the third press hatches it (sfx_hatch). The
  hatch animation is a 6-frame stepped animation, about 400 ms, before the SECRET ARCADE opens. If you move
  away, the cracks reset after 10 s.
- **Turning it off:** a new Options choice, `SECRET ARCADE: ON/OFF` (default ON), lets a parent hide the
  egg completely.

## Architecture (new files under `launcher/src/tv/dumbtv/home/`)

| File | Role | ≈ lines |
|---|---|---|
| `Egg.java` | Corner View: pixel egg drawing, wobble, crack stages, hatch callback | 150 |
| `Arcade.java` | Activity (not exported). Hosts one `Screen` at a time: menu, game, pause | 180 |
| `Engine.java` | View with a Choreographer frame loop: fixed 60 Hz update, 320×180 logical canvas scaled ×6 with no filtering, held-key tracking | 200 |
| `Screen.java` | Small base class: `update()`, `draw(Canvas)`, `key(code, down)`, `pause()` | 40 |
| `Sprites.java` | Sprites as `String[]` pixel maps plus a palette, baked into Bitmaps once; pixel font from the existing PressStart2P typeface | 200 |
| `Scores.java` | Best score per game, saved in `home.conf` (`arcade.<game>.best`) | 30 |
| `SnakeByte.java` | Game 1 | 250 |
| `BrickBop.java` | Game 2, levels as ASCII maps | 400 |
| `StarSwarm.java` | Game 3 | 600 |

That's about 2,000 lines in total, plus about 30 KB of APK size. Changes to existing files:
- `Home.java`: add the egg and the LEFT focus hook. Start `Arcade` when it hatches.
- `Config.java`: add the `ARCADE` choice.
- `Guard.warn()`: pause the arcade if it's open (a static `Arcade.current`, the same pattern as `Home.current`).
- `Sound.java`: raise max streams from 2 to 4.
- `make_sounds.py`: add new sound effects.
- `AndroidManifest.xml`: add `<activity android:name=".Arcade" android:exported="false" android:screenOrientation="landscape" />`.

Keys in all three games:
- D-pad: move.
- OK: action, or confirm in menus.
- BACK: pause menu (RESUME / QUIT). BACK again quits.
- Play/Pause: pause.

## SECRET ARCADE menu
- A 320×180 scene with the title `SECRET ARCADE` and three mini cartridges in the same style as the shelf.
- LEFT/RIGHT picks a game and OK plays it. Under each cartridge is its best score.
- BACK goes back to the shelf.

## Game designs

### 1. SNAKE BYTE (build first: smallest, and it proves the engine)
- **Board:** 8 px cells, 40×20 board below a 20 px score bar.
- **Mode:** CLASSIC only. Hitting a wall or yourself ends the game. There's no mode picker.
- **Speed:** starts at 7 moves/s, gets 0.25 faster per berry, and stops at 15.
- **Controls:** a 2-turn input buffer, so quick turns on the remote aren't lost. A 180° reverse is ignored.
- **Food:**
  - A berry is worth +10 and adds one segment.
  - Every 8th berry, a gold star appears for 5 s. It's worth +50 and adds three segments.
- **Death:** the board flashes and the segments pop off tail-first, then the score is compared with the best.

### 2. BRICK BOP
- **Paddle:** 32 px wide. Holding a direction speeds it up over about 150 ms, so it can be nudged a little
  or moved fast.
- **Ball:**
  - The bounce angle depends on which of 5 zones of the paddle it hits.
  - It speeds up slightly every 8 paddle hits.
  - It waits on the paddle until you press OK, or launches by itself after 3 s.
- **Bricks:** 16×8 px, 18 columns. There are three kinds:
  - normal: one hit
  - tough: two hits, and it shows a crack after the first
  - steel: can't be broken, used sparingly
- **Levels:** 6 hand-drawn ASCII maps: smiley, heart, game controller, rocket, castle, rainbow. After level 6
  they loop, a little faster.
- **Power-ups:** about 1 brick in 8 drops a capsule:
  - **W**: wide paddle
  - **M**: three balls
  - **S**: slow ball
  - **1UP**: extra life (rare)
- **Lives:** 3.

### 3. STAR SWARM
- **Ship:** sits at the bottom and moves LEFT/RIGHT. It always auto-fires, with at most 2 shots on screen.
  OK isn't used during play. There's no fire setting.
- **Waves:**
  - Aliens enter in groups of 4–8 along curved paths and settle into an 8×4 formation that sways from side to side.
  - Every few seconds, 1–3 of them break off, dive at the ship in an arc firing a few shots, then loop back in from the top.
  - Dives get faster and more frequent with each wave.
- **Enemies** (all original sprites):

  | Enemy | Hits | Points |
  |---|---|---|
  | Blip (drone) | 1 | 50 |
  | Zapper (crab) | 1 | 80, and it shoots more |
  | Ringer (saucer) | 1 | 100, and it dives in a zig-zag |
  | Boss | 2 | 150, and it changes colour when hit |

- **Bonus round (every 4th wave):** a meteor shower. Rocks drift down and you score points for shooting them.
  The rocks don't fire back.
- **Power-ups:** a bonus UFO sometimes crosses the top. Shooting it gives TWIN SHOT for 20 s or a one-hit SHIELD.
- **Lives:** 3, plus an extra life at 20,000 points.
- **Background:** a two-layer scrolling starfield.

## Sound
`make_sounds.py` gains these square-wave effects, at the same 0.25 volume and respecting `SOUNDS: OFF`:
- `sfx_crack`, `sfx_hatch`
- `sfx_eat`, `sfx_bounce`, `sfx_brick`
- `sfx_shoot`, `sfx_boom`, `sfx_power`
- `sfx_level`, `sfx_over`

There's no background music. It would be large, and annoying over time.

## High scores
- Each game keeps one best score; there's no initials entry. Beating it shows `NEW HIGH SCORE!` with sfx_level
  on the game-over card.
- Scores live in `home.conf`, so they survive launcher updates.
- `RESET HIGH SCORES` goes in Options, in parent mode only.

## Install timing (user's request)
**Install everything in one go, and only when the user says it's a go.** It's a surprise, so it must go on
while TV2 is idle and the kid isn't watching. Build and test each phase on the PC only, with no intermediate
installs on TV2. The one install also ships the extra sleep timer option: **2 HOURS** (120 min), already in
the code. The countdown shows `h:mm:ss` above an hour.

Before installing, check `dumpsys power mWakefulness` and the foreground app. After the install, leave the TV
on the home screen as it was found, or asleep. Then delete the `/sdcard` screenshots.

## Phases (build order; all installed together at the end)
0. **Back up the current build first.** The `retro-2026-10-27` backup predates the Appstore change. Copy the
   current APK, patch and `home.conf` to `dumbtv-builds/retro-<date>-appstore/`.
1. **Engine, egg, arcade menu, SNAKE BYTE, scores.** This is the riskiest part: frame pacing on the AFTTI43
   (check with `dumpsys gfxinfo tv.dumbtv.home`) and how the remote handles held keys.
2. **BRICK BOP.**
3. **STAR SWARM.**
4. **Polish:**
   - pause when the sleep warning shows
   - Options toggle and reset
   - tune the sounds
5. **Documentation:** update the RETRO-PLAN "as built" notes and the memory, then refresh the backup.

Tests over adb:
- Drive with `input keyevent`: LEFT to the egg, then OK ×3, then play.
- Take screenshots with `adb shell screencap -p /sdcard/x.png` plus pull. `exec-out` gives junk on these TVs.
- Check memory (it's currently 44 MB PSS) and frame drops while playing.
- Feel and difficulty need the kid with the real remote.

## Install and rollback
- Install as usual: `ADB_SERIAL=<tv2> ./launcher/install.sh --build`. It uses the same key, so `home.conf`
  is kept.
- To switch it off: Options, then `SECRET ARCADE: OFF`. The egg disappears and nothing else changes.
- To roll back fully, reinstall the Phase 0 backup APK with `adb install -r`. It uses the same signing key,
  so settings are kept.

## Decisions (confirmed by the user 2026-10-27)
1. Egg corner: bottom-left, next to PORTS.
2. Hatching: 3 OK presses (crack, crack, hatch).
3. STAR SWARM: always auto-fires.
4. SNAKE BYTE: CLASSIC only (walls end the game).
5. No daily arcade time limit. The sleep timer covers it.
6. High scores: just the best score per game, no initials.
