package com.kambo.minecraftcontrollersupport.config;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import net.minecraftforge.common.config.Config;

@Config(modid = MinecraftControllerSupport.MOD_ID)
public final class ControllerConfig {
    @Config.Comment("Enable controller gameplay input.")
    public static boolean enabled = true;

    @Config.Comment("Show the controller diagnostic overlay when a controller is connected.")
    public static boolean showDebugOverlay = false;

    @Config.RangeDouble(min = 0.0D, max = 0.95D)
    public static double movementDeadzone = 0.18D;

    @Config.RangeDouble(min = 0.0D, max = 0.95D)
    public static double cameraDeadzone = 0.15D;

    @Config.RangeDouble(min = 10.0D, max = 720.0D)
    public static double horizontalCameraSpeed = 180.0D;

    @Config.RangeDouble(min = 10.0D, max = 720.0D)
    public static double verticalCameraSpeed = 140.0D;

    @Config.Comment("The sneak button toggles crouch instead of hold-to-sneak.")
    public static boolean toggleSneak = true;

    @Config.Comment("Controller cursor speed in scaled GUI pixels per second.")
    @Config.RangeDouble(min = 50.0D, max = 2000.0D)
    public static double guiCursorSpeed = 450.0D;

    @Config.Comment("Snap between slots and buttons with the D-pad. When false the D-pad scrolls instead.")
    public static boolean guiDpadNavigation = true;

    @Config.Comment("Delay in milliseconds between repeated GUI scroll steps while a scroll input is held.")
    @Config.RangeInt(min = 30, max = 500)
    public static int guiScrollRepeatMs = 90;

    @Config.Comment("Vibrate the controller when you break a block (needs driver rumble support).")
    public static boolean rumbleEnabled = true;

    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public static double rumbleStrength = 0.8D;

    @Config.Comment("Reverse left-stick horizontal movement.")
    public static boolean invertMovementX = false;

    @Config.Comment("Reverse left-stick forward/backward movement.")
    public static boolean invertMovementY = false;

    @Config.Comment("Reverse right-stick horizontal camera movement.")
    public static boolean invertCameraX = false;

    @Config.Comment("Reverse right-stick vertical camera movement.")
    public static boolean invertCameraY = false;

    @Config.Comment("Xbox-style axis and button indices. Use F8 to inspect the active device.")
    public static final Mapping mapping = new Mapping();

    public static final class Mapping {
        @Config.RangeInt(min = -1)
        public int leftStickX = 1;
        @Config.RangeInt(min = -1)
        public int leftStickY = 0;
        @Config.RangeInt(min = -1)
        public int rightStickX = 3;
        @Config.RangeInt(min = -1)
        public int rightStickY = 2;

        @Config.Comment("Shared trigger axis: negative is LT and positive is RT. Set to -1 for buttons only.")
        @Config.RangeInt(min = -1)
        public int sharedTriggerAxis = 4;
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double triggerThreshold = 0.35D;

        @Config.RangeInt(min = -1)
        public int jumpButton = 0;
        @Config.Comment("Drop the held item (B by default).")
        @Config.RangeInt(min = -1)
        public int dropButton = 1;
        @Config.RangeInt(min = -1)
        public int inventoryButton = 2;
        @Config.Comment("Second inventory button so both X and Y open it.")
        @Config.RangeInt(min = -1)
        public int secondaryInventoryButton = 3;
        @Config.RangeInt(min = -1)
        public int previousHotbarButton = 4;
        @Config.RangeInt(min = -1)
        public int nextHotbarButton = 5;
        @Config.RangeInt(min = -1)
        public int backButton = 6;
        @Config.RangeInt(min = -1)
        public int pauseButton = 7;
        @Config.RangeInt(min = -1)
        public int sprintButton = 8;
        @Config.Comment("Toggles crouch (see toggleSneak).")
        @Config.RangeInt(min = -1)
        public int sneakButton = 9;
        @Config.Comment("Optional extra perspective button. D-pad Up always cycles first/third person.")
        @Config.RangeInt(min = -1)
        public int perspectiveButton = -1;

        @Config.Comment("GUI controls: A left-clicks, X right-clicks, and B closes the screen.")
        @Config.RangeInt(min = -1)
        public int guiClickButton = 0;
        @Config.RangeInt(min = -1)
        public int guiRightClickButton = 2;
        @Config.RangeInt(min = -1)
        public int guiBackButton = 1;
        @Config.Comment("GUI wheel controls. The D-pad also scrolls.")
        @Config.RangeInt(min = -1)
        public int guiScrollUpButton = 4;
        @Config.RangeInt(min = -1)
        public int guiScrollDownButton = 5;
        @Config.Comment("Press the right stick in a GUI to toggle drag-scroll mode.")
        @Config.RangeInt(min = -1)
        public int guiDragScrollButton = 9;

        @Config.Comment("Optional trigger buttons for devices that expose triggers as buttons.")
        @Config.RangeInt(min = -1)
        public int useButton = -1;
        @Config.RangeInt(min = -1)
        public int attackButton = -1;
    }

    private ControllerConfig() {
    }
}
