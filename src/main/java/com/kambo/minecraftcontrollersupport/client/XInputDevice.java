package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Native Windows XInput pad access. Preferred over LWJGL/JInput for both input
 * and rumble: XInputGetState returns in microseconds, while JInput polls can
 * block for milliseconds per call on Bluetooth pads and report garbage axis
 * values until the pad sends its first input.
 */
final class XInputDevice {
    private static final int ERROR_SUCCESS = 0;
    private static final int MAX_CONTROLLERS = 4;

    static final int BUTTON_COUNT = 10;
    static final int AXIS_COUNT = 5;

    private final XInputState pollBuffer = new XInputState();
    private XInputLibrary library;
    private int controllerIndex = -1;

    boolean connectFirstAvailable() {
        stop();
        controllerIndex = -1;
        library = loadLibrary();
        if (library == null) {
            return false;
        }

        for (int index = 0; index < MAX_CONTROLLERS; index++) {
            XInputState state = new XInputState();
            if (library.XInputGetState(index, state) == ERROR_SUCCESS) {
                controllerIndex = index;
                MinecraftControllerSupport.logger.info(
                    "Using Windows XInput controller index {}",
                    index
                );
                return true;
            }
        }
        return false;
    }

    boolean isConnected() {
        return library != null && controllerIndex >= 0;
    }

    /** Reads the current pad state, or null when the pad is gone. */
    XInputGamepad poll() {
        if (!isConnected()) {
            return null;
        }
        if (library.XInputGetState(controllerIndex, pollBuffer) != ERROR_SUCCESS) {
            controllerIndex = -1;
            return null;
        }
        return pollBuffer.gamepad;
    }

    void setStrength(float strength) {
        if (!isConnected()) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, strength));
        XInputVibration vibration = new XInputVibration();
        vibration.leftMotorSpeed = (short) Math.round(clamped * 65535.0F);
        vibration.rightMotorSpeed = (short) Math.round(clamped * 0.65F * 65535.0F);
        int result = library.XInputSetState(controllerIndex, vibration);
        if (result != ERROR_SUCCESS) {
            controllerIndex = -1;
            MinecraftControllerSupport.logger.warn(
                "Windows XInput rumble stopped responding (error {})",
                result
            );
        }
    }

    void stop() {
        if (isConnected()) {
            setStrength(0.0F);
        }
    }

    private static XInputLibrary loadLibrary() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            return null;
        }
        String[] names = {"xinput1_4", "xinput1_3", "xinput9_1_0"};
        for (String name : names) {
            try {
                return Native.loadLibrary(name, XInputLibrary.class, W32APIOptions.DEFAULT_OPTIONS);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError exception) {
                // Try the next Windows XInput version.
            }
        }
        MinecraftControllerSupport.logger.warn("No compatible Windows XInput library was found");
        return null;
    }

    private interface XInputLibrary extends StdCallLibrary {
        int XInputGetState(int userIndex, XInputState state);

        int XInputSetState(int userIndex, XInputVibration vibration);
    }

    public static final class XInputState extends Structure {
        public int packetNumber;
        public XInputGamepad gamepad = new XInputGamepad();

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("packetNumber", "gamepad");
        }
    }

    public static final class XInputGamepad extends Structure {
        public static final int DPAD_UP = 0x0001;
        public static final int DPAD_DOWN = 0x0002;
        public static final int DPAD_LEFT = 0x0004;
        public static final int DPAD_RIGHT = 0x0008;
        public static final int START = 0x0010;
        public static final int BACK = 0x0020;
        public static final int LEFT_THUMB = 0x0040;
        public static final int RIGHT_THUMB = 0x0080;
        public static final int LEFT_SHOULDER = 0x0100;
        public static final int RIGHT_SHOULDER = 0x0200;
        public static final int A = 0x1000;
        public static final int B = 0x2000;
        public static final int X = 0x4000;
        public static final int Y = 0x8000;

        public short buttons;
        public byte leftTrigger;
        public byte rightTrigger;
        public short thumbLX;
        public short thumbLY;
        public short thumbRX;
        public short thumbRY;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                "buttons",
                "leftTrigger",
                "rightTrigger",
                "thumbLX",
                "thumbLY",
                "thumbRX",
                "thumbRY"
            );
        }
    }

    public static final class XInputVibration extends Structure {
        public short leftMotorSpeed;
        public short rightMotorSpeed;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("leftMotorSpeed", "rightMotorSpeed");
        }
    }
}
