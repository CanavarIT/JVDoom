# JVDoom

A from-scratch **Doom (1993) engine for Android written in pure Java**. No NDK, no native code, no external libraries — it reads the original WAD file and renders everything itself on the CPU.

> ⚠️ **Beta.** The game is fully playable, but there may be small, minor bugs. Bug reports are welcome.

## Features

- Software renderer: BSP walls, flats, sky, sprites, masked textures, light diminishing
- Original gameplay: all weapons, monsters with AI, pickups, keys, doors, lifts, switches, teleports, crushers, secrets
- Original Doom **status bar, face, fonts, menu (skull cursor, sliders)** and **end-of-level intermission screen with the world map** — all taken straight from the WAD
- Sound effects from the WAD with distance and stereo panning
- Touch controls: virtual stick, look area, fire/use buttons, weapon switch, automap
- Touch-friendly menu: tap empty space to move the skull, tap the text to open/toggle, swipes never press anything by accident
- **JVDoom Setup** page: render resolution (180p / 240p / 360p), smoothing, aim assist, FPS counter, HUD/button size and opacity, map warp

## Included WAD

`app/src/main/assets/doom1.wad` is the **shareware** version of Doom (Episode 1: *Knee-Deep in the Dead*, 9 maps), which id Software allows to be freely distributed. It is **not** the full game. To play the full game or Doom II, replace it with your own legally obtained IWAD (see `PUT_DOOM1.WAD_HERE.txt`).

## How it is built

Everything lives in `app/src/main/java/com/java/jvdoom/`:

| File | Role |
|---|---|
| `MainActivity` | Android entry point, immersive mode, lifecycle, back button |
| `GameView` | `SurfaceView` + game thread (35 Hz tics), touch controls, automap, overlays |
| `Game` | Game state: player, weapons, pickups, ticks, level exit |
| `Wad` | WAD reader (lumps, map lists, Doom 1 / Doom 2 detection) |
| `Gfx` | Decodes palette, colormaps, patches, textures, flats and sprites |
| `Level` | Loads a map: vertices, lines, sectors, BSP nodes, things |
| `Renderer` | Software BSP renderer drawing into an `int[]` framebuffer |
| `Phys`, `AI`, `Monsters`, `Things`, `Mobj` | Collision/movement, monster behaviour and definitions, objects |
| `Specials` | Line specials: doors, lifts, floors, ceilings, switches, teleports, exits, light effects |
| `Hud` | Status bar, face logic, WAD fonts and number drawing |
| `Menu` | Classic Doom menu + JVDoom Setup |
| `Intermission` | Original end-of-level screen (stats counting, world map, "you are here") |
| `SoundSystem` | Software mixer on `AudioTrack` |

Frame flow: the game thread advances the simulation at 35 tics/s, `Renderer` draws the 3D view at low internal resolution, `GameView` scales it up to the screen (optionally smoothed) and draws the HUD, controls and menu on top using the original WAD graphics.

## Build

1. Open the project in Android Studio / AndroidIDE.
2. Build and run (`./gradlew assembleDebug`).

## Credits

- Doom © id Software. This project is an independent, unofficial engine reimplementation.
- Shareware `doom1.wad` is distributed under id Software's shareware terms.
