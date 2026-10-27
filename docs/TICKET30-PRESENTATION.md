# MarioCraft presentation and MVP boundaries — issue 30

MarioCraft now starts at a Minecraft-style dirt-background title screen with
Singleplayer, Settings, Controls and Quit. World selection/creation, pause and
settings retain Minecraft's existing screens and gain MarioCraft branding.
There are no course menus. Keyboard rebinding remains in Options > Controls;
the controller profile and original-sound toggle use the existing Forge config
screen. Minecraft options, sound sliders and pause-on-focus-loss retain their
normal options.txt persistence. MarioCraft's controller/sound configuration uses
its existing Forge configuration file.

## Evidence and bounded design

This slice uses the existing Minecraft lifecycle, resources/audio, controls,
health and persistence reports, plus the pinned native audio API and local
source. It introduces no new retail/emulator investigation or course content.
The native sound definitions put actions in bank 0, motion loops in bank 1 and
Mario voice in bank 2. Bank 8 contains GENERAL2 object effects and is excluded.
The bank-1 shell/cannon IDs are additionally excluded. No Mario object engine,
pickups, caps/cap-loss controller, cannons, stars or objectives is instantiated.

The mod-owned sound boundary replaces only the pinned library's play_sound
adapter at build time. Accepted requests enter the native synthesizer once;
the strongly retained JNA callback counts events and never emits a second sound.
All queued native positions point at a process-lifetime zero-position array,
not a Mario object or GlobalState that can be freed during unload. Original
ROM audio banks initialize once per process: the pinned loader retains its
private ROM copy. Session end resets sound state and request count and disables
audio before deleting Mario. Session start resets the synthesizer. The ROM,
native library, generated textures and PCM remain private local resources.

Only the Mario owner thread calls native initialization, requests, synthesis,
reset or teardown. After updating native game sound, the adapter disables the
level and environment sequence players before generating PCM. Only the SFX
sequence player remains; Mario course/background music cannot enter the output.
Minecraft's own MusicTicker and music volume remain independent.

PCM is signed 16-bit little-endian stereo at 32 kHz. One 1088-frame block and two
1056-frame blocks per three 30 Hz steps exactly match 32000 frames per second.
Minecraft's managed Paulscode SoundSystem owns the raw-data stream, command
thread and device. One unique source name belongs to each Mario session.
The adapter compares SoundSystem instance identity after reload and creates
its source in the new generation; the old engine cleans its old generation.
Playback operations queue through SoundSystem, with no direct OpenAL operations
in production. Players volume controls Mario sounds and Minecraft's master
volume controls the shared engine. Pause, menu/focus loss, muting and teardown
stop and flush pending playback; muted running simulations synthesize and
discard output. Playback errors disable audio and preserve Mario simulation.
Reflection uses verified mapped and SRG field names for packaged execution.

An early direct-OpenAL implementation failed the real sound-engine-reload test
because asynchronous context teardown raced playback. It was replaced with the
managed command API. That failed private run is not acceptance evidence.

## Supported worlds and restrictions

The MVP admits ordinary single-player Survival worlds without Hardcore.
Existing Creative, Adventure, Spectator and Hardcore saves show a paused
Save-and-Quit screen; their game mode and data are preserved. New world creation
keeps Minecraft's Survival default and disables mode cycling. Multiplayer and
Open-to-LAN controls are absent/blocked. Nonlocal NetworkManager admission also
rejects command-line or direct-connection constructor paths while retaining
integrated local channels.

Player-specific Forge guards cancel mining, placing, item/block/entity use,
vanilla attacks, item/XP pickup, containers, player commands and dimension travel.
The outbound gate also rejects alternative click-window, creative inventory,
held-item, enchantment, recipe, dig/drop/swap and use packets. Inventory/creative
container GUI openings are canceled. Hotbar, experience, hunger and equipment
HUD elements are hidden. The existing native health path ignores Minecraft
armor/healing/hunger rules; food NBT is preserved across each server player tick.
Existing hidden inventory, armor and XP are retained rather than erased.

Restrictions do not cancel mob living updates, ordinary world item/XP entities,
mob loot, terrain updates or world saving. Native Mario combat continues through
its existing direct damage path. Portal entry is blocked for the player; the
older hazard/respawn fixtures use a named transfer helper requiring the matching
explicit probe property. Semantic completion of a saved pending death may still
restore Mario to the Overworld through the existing persistence adapter.

The integrated test exposed a watched-chunk handoff defect: relocating a held
player to distant real terrain could leave the client with an empty placeholder
chunk and zero initial collision surfaces, because movement packets were held
before vanilla could update chunk tracking. Explicit serverUpdateMovingPlayer
after the fixture teleport resolved that exact failure. Restore and safe respawn
now perform the same server-side handoff after their guarded teleports. Normal
authoritative END poses also call the API; its ordinary distance threshold keeps
stationary updates cheap. No movement-packet ownership was relaxed.

## Executable verification

Run the bounded integrated probe with the locally prepared Java/Gradle paths:

```powershell
./scripts/dev.ps1 -JavaHome <local-java8> -GradleHome <local-gradle-cache> -Task client -PresentationProbe
./scripts/dev.ps1 -JavaHome <local-java8> -GradleHome <local-gradle-cache> -Task build
./scripts/dev.ps1 -JavaHome <local-java8> -GradleHome <local-gradle-cache> -Task nativeSmoke
```

The presentation probe creates a disposable normally generated Survival save,
drives actual client menus and Mario inputs, attempts alternative packet and
server-event paths, checks original voice/action events and nonzero PCM,
Minecraft music, managed playback, pause/focus/mute behavior, sound-system reload,
then pauses and performs normal Save/Quit and reopens the same save. It checks
that pickup entities remain, ordinary mob ticks advance, hidden inventory is
preserved, and native/client/audio ownership closes. Raw logs, worlds and any
captures stay private. Scalar acceptance results are recorded separately under
validation. PCM and managed playback are objective evidence; no claim of human
listening or physical speaker audibility is made.

The final integrated run passed all 73 required boolean checks on both loads,
with two native creates/closes, two server starts and two sound-engine starts
(including the deliberate reload). Both loads produced two Mario voice events,
nonzero action events and PCM peak 22378, and actual managed playback was active.
The strict verifier found zero client/server runtime ERROR/FATAL lines, movement
warnings or audio adapter failures after world startup. Known Forge development
classpath scanning warnings before world startup are not counted as game errors.
See [scalar results](validation/TICKET30-PRESENTATION-CHECKS.json).
The final build passed 51 unit tests with zero failures/errors/skips; native smoke
passed two lifecycle cycles, eight ABI checks, variable jump/air steering and
active attack fallback checks. See [build results](validation/TICKET30-BUILD-CHECKS.json).

The last integrated run includes restore/respawn teleport chunk handoffs. The
subsequent per-pose chunk-tracking call is build/native-smoke checked; actual
long-distance traversal across watched-chunk boundaries, native-combat regression
and a clean reobfuscated installation remain explicit release validation in
issue 31. No long-distance or physical-audibility claim is made here. Earlier
fixture/timing failures and the direct-OpenAL reload failure remain private
diagnostics, separate from the accepted final result.
