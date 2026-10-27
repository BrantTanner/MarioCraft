# Configurable playable controls — issue 20

Keyboard bindings appear under Options > Controls > MarioCraft. Minecraft supplies
rebinding, mouse-button binding, conflict labels, reset and options.txt persistence.
Defaults are WASD/full, Ctrl/0.4 walk, Alt/0.7 intermediate, Space/A, J/B, Shift/Z,
arrows/C, Q/E/L/R and Enter/Start. Diagonals are normalized; walk wins when both
speed modifiers are held. Modifier release immediately restores the remaining
speed. Start opens and resumes only the pause menu and is sampled outside the
paused native clock. L/R currently provide shoulder yaw controls; semantic C/L/R
controls are available for the camera-mode implementation in issue 23.

## Controller settings

Mods > MarioCraft > Config edits the persistent controller profile in
`config/mariocraft-controls.cfg`. Slot is a validated integer: -1 automatic,
0–3 explicit. Axis/button indices, inversion and radial cutoff are configurable;
-1 disables a button. An absent axis/button produces neutral input. Settings
apply when the standard Forge configuration screen is saved.

The supported hardware backend is Windows XInput (`XInput9_1_0`, called via JNA).
It polls stateless slots, detects disconnects on each poll and can find newly
connected devices without repeatedly creating JInput native environments.
Xbox-compatible XInput controllers are supported; DirectInput-only devices are
outside this backend. This is a deliberate support boundary, not a hardware test.

| Axis index | Raw value |
| --- | --- |
| 0, 1 | Left stick X/Y (Y negative forward) |
| 2, 3 | Right stick X/Y |
| 4, 5 | Left/right trigger, 0–1 |

Movement defaults to axes 0/1. Other axes may be selected as movement axes;
right-stick camera and trigger-to-action bindings are not implemented. Stick
endpoints are normalized using each signed half-range. The default radial drift
cutoff is 0.08, applied once; magnitudes above it are retained and diagonals cap
at 1. Lower configured cutoffs remain effective through native input submission.

| Button index | XInput button | Default semantic binding |
| --- | --- | --- |
| 0 | A | A / jump |
| 1 | B | B / attack |
| 2, 3 | X, Y | Unbound |
| 4 | Left shoulder | Z / crouch |
| 5 | Right shoulder | R |
| 6 | Back | Unbound |
| 7 | Start | Start / pause/resume |
| 8 | Left stick click | L |
| 9 | Right stick click | Unbound |
| 10, 11, 12, 13 | D-pad left/right/up/down | C left/right/up/down |

A non-neutral controller sample takes precedence over keyboard gameplay input.
Focus loss, menu opening, profile changes and disconnect reset the input gate.
The player must release to neutral before held movement/actions can resume.
Keyboard and controller are gated independently. Pause menu Start remains
available separately from gameplay controls. Other screens retain normal GUI
mouse handling; arbitrary menus are not closed by Start.

## Vanilla ownership

While the single-player native session owns Mario, vanilla gameplay binding
contexts disable movement, inventory, drop, swap, attack, use and pick-block
without changing the user's saved key codes. This includes the default E/Q
collisions with Mario R/L. Client interaction events and outgoing digging,
entity-use and item-use packets are blocked under native ownership; the native
mob-contact adapter still applies server damage directly. The narrow existing
movement-packet ownership rule remains. World/mob AI is unchanged.

An owned MouseHelper consumes relative mouse motion before EntityRenderer can
apply it, while leaving GUI mouse events and bindable mouse buttons available.
Closing ownership restores the previous MouseHelper and vanilla binding contexts,
along with the existing perspective/auto-jump/movement-gate cleanup.

## Evidence and reproduction

Nine unit tests passed with Java 8u504, Gradle 4.9 and Forge 14.23.5.2864, followed
by JAR assembly/reobfuscation. Tests cover analog endpoints/button bits and the
16-byte XInput state layout; configured axes/buttons and cutoff below 0.08;
keyboard speed/diagonal handling, simultaneous A/B/Z, modifier release;
focus/menu/reconnect gating; and existing fixed-clock/interpolation behavior.

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -GameplayProbe
```

The successful integrated run used a new normally generated Overworld, seed
20261026, without terrain edits. It alternated actual Minecraft KeyBinding
samples with synthetic raw controller samples through the configured profile
mapper on successive native frames, covering 0.4/0.7/full movement, A and B.
It temporarily rebound jump to K, saved/reloaded Minecraft options, exercised
jump, and restored the original binding. C input turned the live camera. A
synthetic Start edge used the production pause/resume handler; native frame 170
and world tick 225 froze during the real pause screen. The probe also asserted
vanilla inventory/drop/attack/use contexts were inactive and the owned mouse
helper was installed. Shared-gate simultaneous-action/focus/release scenarios
ran inside the client; they are input-policy checks, not hardware-focus events.

[Sanitized scalar output](validation/TICKET20-RESULT.json): travel 3.055 blocks,
jump rise 2.585 blocks, landing, camera clearance 4 blocks, keyboard/controller
sample equality, binding persistence and suppression checks passed. Stationary
client/server error was 0 blocks. Three native admissions produced three server
hits on the separate test-only NoAI pig. Ordinary AI-enabled mobs received up
to 341 updates. No moved-wrongly/moved-too-quickly warning occurred. Native
teardown and server/world save/unload completed; the wrapper exited successfully
and required fresh passing JSON. Raw logs, images, ROM/native resources and
worlds stay private under ignored `.local/`.

These checks do not claim physical controller calibration, real USB hotplug,
physical OS focus transitions, pixel QA of the settings screen, or precise
cross-device trajectory equivalence. Disconnect/focus/release state policy is
covered synthetically; XInput's live no-device polling ran without an unavailable
backend warning. Camera modes/foreground occlusion remain issue 23; incoming
native damage remains issue 25. This slice does not complete the broader mod.
