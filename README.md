# Minecraft Controller Support

Minecraft Controller Forge mod for Minecraft 1.12.2.

## Requirements

- A 64-bit Java 8 JDK selected through `JAVA_HOME`

The included wrapper uses Gradle 4.9, matching the official Forge 1.12.2 MDK.
Newer Gradle and Java versions are not supported by this Minecraft version.

## Useful commands

```powershell
.\gradlew.bat eclipse
.\gradlew.bat idea
.\gradlew.bat runClient
.\gradlew.bat build
```

The built mod jar is written to `build/libs`.

## Controller prototype

The current milestone includes:

- native Windows XInput input and rumble (fast, no JInput polling stalls),
  with LWJGL/JInput as the fallback for non-XInput pads
- automatic controller detection and reconnect scans
- controller input is ignored while the game window is unfocused
- an F8 diagnostic overlay showing every axis, button, POV value, rumble path,
  and the mod's own per-tick/per-frame cost in milliseconds (hidden by default)
- analog left-stick movement and curved right-stick camera control
- keyboard and mouse stay fully usable alongside the controller; inputs merge
  instead of overriding each other
- RT breaks/attacks, LT interacts/places; jump on A, drop on B,
  sprint on button 8, crouch toggle on button 9 (`toggleSneak`),
  and X or Y opens the inventory
- D-pad Up cycles first/third person in-game
- controller rumble on block break, on taking damage (scaled with how hard the
  hit or fall was), on striking an entity, and a long pulse on death, with a
  Windows XInput fallback when the legacy JInput Bluetooth driver reports zero
  motors
- a "Controller..." button on the pause menu (and the Mods list Config button)
  for editing sensitivity and every mapping in-game
- controller-driven menus and inventories with a right-stick cursor
- A for left click, X for right click, and B/back to close a screen
- D-pad navigation that snaps between slots and buttons (`guiDpadNavigation`)
- LB/RB or left-stick for GUI wheel scrolling (stick deflection sets the speed)
- button 9 in a GUI toggles drag-scroll mode: the right stick scrolls and an
  autoscroll arrow marker replaces the crosshair
- LT/RT switch creative inventory tabs
- the cursor slows down near slots and buttons for precise picks
- automatic hand-off between the mouse and the controller cursor: moving one
  hides the other
- edge detection for one-shot button actions
- safe release of simulated attack/use inputs in menus and after disconnects

Default indices target a common Xbox-style layout. Controller drivers and Steam
Input can report different indices, so use the F8 overlay and edit
`run/config/minecraftcontrollersupport.cfg` when calibration is needed.

The tested Bluetooth XInput layout is left Y/X on axes 0/1, right Y/X on axes
2/3, and the shared trigger on axis 4.

Each stick direction can be reversed independently with `invertMovementX`,
`invertMovementY`, `invertCameraX`, and `invertCameraY` in that config file.
