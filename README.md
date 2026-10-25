# JVDoom

![Java](https://img.shields.io/github/languages/top/CanavarIT/JVDoom?label=java&color=blue)
![Code size](https://img.shields.io/github/languages/code-size/CanavarIT/JVDoom?label=code%20size&color=blue)
![License](https://img.shields.io/github/license/CanavarIT/JVDoom?label=license&color=orange)

A from-scratch **Doom (1993) engine for Android written in pure Java**. No NDK, no native code, no external libraries. It reads the original WAD file and renders, plays, and sounds everything itself.

> ⚠️ **Beta.** The game is fully playable, but there may be small, minor bugs. Bug reports are welcome.

## Features

**Engine**
- Software BSP renderer: walls, flats, sky, sprites, masked textures, light diminishing, animated flats and textures
- Original gameplay: all weapons, monsters with AI, pickups, keys, doors, lifts, switches, teleports, crushers, stairs, light effects, secrets, level exits
- Boss triggers like the original (for example the Barons of E1M8 open the exit)
- Sound effects from the WAD with distance and stereo panning
- **Music**: Doom's own songs (MUS format) are converted to MIDI on the fly and played through Android's built-in MIDI synthesizer
- **Save / Load game**: 6 slots, the whole level state is stored (monsters, items, doors, lifts, lights, switches and your inventory)

**Original Doom interface, taken straight from the WAD**
- Status bar with the animated face, original fonts and numbers
- Classic menu with the skull cursor, sliders, Save / Load screens
- **End-of-level screen** with the world map, counting KILLS / ITEMS / SECRETS, TIME / PAR and the "Entering" screen with the "you are here" marker

**Mobile controls**
- Virtual stick, look area, fire / use buttons, weapon switching, automap
- Touch-friendly menu: tap empty space to move the skull, tap the text to open or toggle an item, and swipes never press anything by accident

**JVDoom Setup** menu with sub-pages
- *Video*: render resolution (180p / 240p / 360p), screen shape (full, 4:3, 5:4, 16:10, 16:9, 21:9, square), smoothing, FPS counter, on-screen kills / items / secrets and level timer, crosshair
- *Picture*: brightness, field of view, colour filters (grayscale, sepia, night vision, inferno)
- *Controls*: aim assist, status bar size, button size and opacity, vibration on damage
- *Gameplay*: map warp (next / previous), restart level, move speed, damage taken, your damage, game speed
- *Cheats*: god mode, noclip, infinite ammo, notarget, all weapons, all keys, full health and armor, kill all monsters
- *Reset settings*

## Included WAD

`app/src/main/assets/doom1.wad` is the **shareware** version of Doom (Episode 1: *Knee-Deep in the Dead*, 9 maps), which id Software allows to be freely distributed. It is **not** the full game. To play the full game or Doom II, replace it with your own legally obtained IWAD (see `PUT_DOOM1.WAD_HERE.txt`).

## How it is built

Everything lives in `app/src/main/java/com/java/jvdoom/`:

| File | Role |
|---|---|
| `MainActivity` | Android entry point, immersive mode, lifecycle, back button |
| `GameView` | `SurfaceView` + game thread (35 Hz tics), touch controls, automap, overlays, settings |
| `Game` | Game state: player, weapons, pickups, ticks, level exit, cheats |
| `Wad` | WAD reader (lumps, map lists, Doom 1 / Doom 2 detection) |
| `Gfx` | Decodes palette, colormaps, patches, textures, flats and sprites |
| `Level` | Loads a map: vertices, lines, sectors, BSP nodes, things |
| `Renderer` | Software BSP renderer drawing into an `int[]` framebuffer, colour filters |
| `Phys`, `AI`, `Monsters`, `Things`, `Mobj` | Collision/movement, monster behaviour and definitions, objects |
| `Specials` | Line specials: doors, lifts, floors, ceilings, switches, teleports, exits, light effects |
| `Hud` | Status bar, face logic, WAD fonts and number drawing |
| `Menu` | Classic Doom menu, Save / Load screens and JVDoom Setup |
| `Intermission` | Original end-of-level screen (stats counting, world map, "you are here") |
| `SoundSystem` | Software mixer on `AudioTrack` |
| `MusicPlayer` | MUS to MIDI converter and background music playback |
| `SaveGame` | Save / load of a game in progress |

Frame flow: the game thread advances the simulation at 35 tics/s, `Renderer` draws the 3D view at low internal resolution, `GameView` scales it up to the screen (optionally smoothed) and draws the HUD, controls and menu on top using the original WAD graphics.

## Build

1. Open the project in Android Studio / AndroidIDE.
2. Build and run (`./gradlew assembleDebug`).

## License

The source code of JVDoom is released under the [MIT License](LICENSE).

**The MIT License does not apply to `doom1.wad`.** The WAD contains the graphics, sounds, music and maps of Doom, which are © id Software. It is the shareware version, distributed under id Software's shareware terms, and is included only so the game can run out of the box.

## Credits

- Doom © id Software. This project is an independent, unofficial engine reimplementation and is not affiliated with or endorsed by id Software or Bethesda.
