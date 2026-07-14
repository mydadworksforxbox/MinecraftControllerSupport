# Minecraft Controller Support

Base Minecraft Forge mod scaffold for Minecraft 1.12.2.

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

- automatic LWJGL controller detection and reconnect scans
- an F8 diagnostic overlay showing every axis, button, and POV value
- analog left-stick movement and curved right-stick camera control
- jump, sneak, sprint, attack/use, inventory, drop, pause, and hotbar controls
- controller-driven menus and inventories with a right-stick cursor
- A for left click, X for right click, and B/back to close a screen
- edge detection for one-shot button actions
- safe release of simulated attack/use inputs in menus and after disconnects

Default indices target a common Xbox-style layout. Controller drivers and Steam
Input can report different indices, so use the F8 overlay and edit
`run/config/minecraftcontrollersupport.cfg` when calibration is needed.

The tested Bluetooth XInput layout is left Y/X on axes 0/1, right Y/X on axes
2/3, and the shared trigger on axis 4.

Each stick direction can be reversed independently with `invertMovementX`,
`invertMovementY`, `invertCameraX`, and `invertCameraY` in that config file.
