package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import com.kambo.minecraftcontrollersupport.config.ControllerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.AWTException;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Converts controller input into normal mouse and screen input for vanilla GUIs. */
public final class ControllerGuiInput {
    private static final long FIRST_SCROLL_REPEAT_MS = 300L;
    private static final long SLOWEST_ANALOG_SCROLL_MS = 250L;
    /** Native pixels the OS cursor may drift from where we put it before we call it a real mouse move. */
    private static final int MOUSE_MOVE_TOLERANCE = 3;
    /** Radius around slots and buttons where the stick cursor slows down for precise picks. */
    private static final float STICKY_TARGET_RADIUS = 10.0F;
    private static final float STICKY_SPEED_FACTOR = 0.45F;

    private enum PointerSource {
        MOUSE,
        CONTROLLER
    }

    private static final Method KEY_TYPED = ReflectionHelper.findMethod(
        GuiScreen.class,
        "keyTyped",
        "func_73869_a",
        char.class,
        int.class
    );
    private static final Method MOUSE_CLICKED = ReflectionHelper.findMethod(
        GuiScreen.class,
        "mouseClicked",
        "func_73864_a",
        int.class,
        int.class,
        int.class
    );
    private static final Method MOUSE_RELEASED = ReflectionHelper.findMethod(
        GuiScreen.class,
        "mouseReleased",
        "func_146286_b",
        int.class,
        int.class,
        int.class
    );
    private static final Method SET_CREATIVE_TAB = ReflectionHelper.findMethod(
        GuiContainerCreative.class,
        "setCurrentCreativeTab",
        "func_147050_b",
        CreativeTabs.class
    );

    private final Robot robot;
    private final ControllerGuiNavigator navigator = new ControllerGuiNavigator();
    private GuiScreen activeScreen;
    private float cursorX;
    private float cursorY;
    private boolean leftHeld;
    private boolean rightHeld;
    private int lastScrollDirection;
    private long nextScrollTime;
    private boolean dragScrollActive;
    private int dragScrollDirection;
    private boolean leftTriggerWasDown;
    private boolean rightTriggerWasDown;
    private PointerSource pointerSource = PointerSource.MOUSE;
    private Cursor blankCursor;
    private boolean blankCursorFailed;
    private boolean nativeCursorHidden;
    private int expectedNativeX = Integer.MIN_VALUE;
    private int expectedNativeY = Integer.MIN_VALUE;

    public ControllerGuiInput() {
        Robot createdRobot = null;
        try {
            createdRobot = new Robot();
            createdRobot.setAutoDelay(0);
        } catch (AWTException | SecurityException exception) {
            MinecraftControllerSupport.logger.warn(
                "Native mouse wheel is unavailable; GUI scrolling with the controller is disabled",
                exception
            );
        }
        robot = createdRobot;
    }

    public void handleTick(Minecraft minecraft, ControllerState state) {
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null) {
            reset();
            return;
        }

        if (screen != activeScreen) {
            activeScreen = screen;
            initializeCursor(minecraft);
            releaseMouseButtons();
            lastScrollDirection = 0;
            navigator.reset();
            dragScrollActive = false;
            dragScrollDirection = 0;
            leftTriggerWasDown = true;
            rightTriggerWasDown = true;
        }

        if (state.wasPressed(ControllerConfig.mapping.guiBackButton)
            || state.wasPressed(ControllerConfig.mapping.backButton)) {
            invoke(KEY_TYPED, screen, '\0', Keyboard.KEY_ESCAPE);
            return;
        }

        if (state.wasPressed(ControllerConfig.mapping.guiDragScrollButton)) {
            dragScrollActive = !dragScrollActive;
            dragScrollDirection = 0;
            if (dragScrollActive) {
                setPointerSource(PointerSource.CONTROLLER);
            }
        }

        handleCreativeTabs(screen, state);

        if (ControllerConfig.guiDpadNavigation) {
            float[] snapTarget = navigator.handleDpad(screen, state, cursorX, cursorY);
            if (snapTarget != null) {
                ScaledResolution resolution = new ScaledResolution(minecraft);
                cursorX = clamp(snapTarget[0], 0.0F, resolution.getScaledWidth() - 1.0F);
                cursorY = clamp(snapTarget[1], 0.0F, resolution.getScaledHeight() - 1.0F);
                positionNativeCursor(minecraft, resolution);
                setPointerSource(PointerSource.CONTROLLER);
            }
        }

        handleMouseButton(screen, state, ControllerConfig.mapping.guiClickButton, 0);
        handleMouseButton(screen, state, ControllerConfig.mapping.guiRightClickButton, 1);
        handleScrolling(state);
    }

    public void updateCursor(Minecraft minecraft, ControllerState state, float deltaSeconds) {
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null || screen != activeScreen) {
            return;
        }

        float horizontal = ControllerBindings.pointerAxis(state, ControllerConfig.mapping.rightStickX);
        float vertical = ControllerBindings.pointerAxis(state, ControllerConfig.mapping.rightStickY);
        if (ControllerConfig.invertCameraX) {
            horizontal = -horizontal;
        }
        if (ControllerConfig.invertCameraY) {
            vertical = -vertical;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        if (dragScrollActive) {
            if (physicalMouseMoved()) {
                dragScrollActive = false;
                dragScrollDirection = 0;
                setPointerSource(PointerSource.MOUSE);
                readNativeCursor(minecraft, resolution);
            }
            return;
        }
        if (horizontal != 0.0F || vertical != 0.0F) {
            setPointerSource(PointerSource.CONTROLLER);
            float speed = (float) ControllerConfig.guiCursorSpeed;
            if (navigator.isNearTarget(screen, cursorX, cursorY, STICKY_TARGET_RADIUS)) {
                speed *= STICKY_SPEED_FACTOR;
            }
            cursorX = clamp(cursorX + horizontal * speed * deltaSeconds,
                0.0F, resolution.getScaledWidth() - 1.0F);
            cursorY = clamp(cursorY + vertical * speed * deltaSeconds,
                0.0F, resolution.getScaledHeight() - 1.0F);
            positionNativeCursor(minecraft, resolution);
        } else if (pointerSource == PointerSource.MOUSE || physicalMouseMoved()) {
            setPointerSource(PointerSource.MOUSE);
            readNativeCursor(minecraft, resolution);
        }
    }

    public void drawCursor(GuiScreen screen) {
        if (screen != activeScreen || pointerSource != PointerSource.CONTROLLER) {
            return;
        }

        int x = getCursorX();
        int y = getCursorY();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 500.0F);
        GlStateManager.disableDepth();
        if (dragScrollActive) {
            drawDragScrollCursor(x, y);
        } else {
            Gui.drawRect(x - 5, y, x + 6, y + 1, 0xFFFFFFFF);
            Gui.drawRect(x, y - 5, x + 1, y + 6, 0xFFFFFFFF);
            Gui.drawRect(x - 2, y - 2, x + 3, y + 3, 0xFF202020);
            Gui.drawRect(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
        }
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void drawDragScrollCursor(int x, int y) {
        int activeColor = 0xFFFFFFFF;
        int idleColor = 0xFF9E9E9E;
        int upColor = dragScrollDirection < 0 ? activeColor : idleColor;
        int downColor = dragScrollDirection > 0 ? activeColor : idleColor;
        Gui.drawRect(x - 2, y - 2, x + 3, y + 3, 0xFF202020);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, activeColor);
        for (int i = 0; i < 4; i++) {
            Gui.drawRect(x - i, y - 9 + i, x + i + 1, y - 8 + i, upColor);
            Gui.drawRect(x - i, y + 8 - i, x + i + 1, y + 9 - i, downColor);
        }
    }

    private void handleCreativeTabs(GuiScreen screen, ControllerState state) {
        boolean leftDown = ControllerBindings.leftTrigger(state) >= ControllerConfig.mapping.triggerThreshold;
        boolean rightDown = ControllerBindings.rightTrigger(state) >= ControllerConfig.mapping.triggerThreshold;
        if (screen instanceof GuiContainerCreative) {
            if (leftDown && !leftTriggerWasDown) {
                changeCreativeTab((GuiContainerCreative) screen, -1);
            }
            if (rightDown && !rightTriggerWasDown) {
                changeCreativeTab((GuiContainerCreative) screen, 1);
            }
        }
        leftTriggerWasDown = leftDown;
        rightTriggerWasDown = rightDown;
    }

    private static void changeCreativeTab(GuiContainerCreative screen, int direction) {
        CreativeTabs[] tabs = CreativeTabs.CREATIVE_TAB_ARRAY;
        int index = screen.getSelectedTabIndex();
        for (int step = 0; step < tabs.length; step++) {
            index = Math.floorMod(index + direction, tabs.length);
            if (tabs[index] != null) {
                invoke(SET_CREATIVE_TAB, screen, tabs[index]);
                return;
            }
        }
    }

    private void handleMouseButton(GuiScreen screen, ControllerState state, int controllerButton, int mouseButton) {
        if (state.wasPressed(controllerButton)) {
            invoke(MOUSE_CLICKED, screen, getCursorX(), getCursorY(), mouseButton);
            if (mouseButton == 0) {
                leftHeld = true;
            } else {
                rightHeld = true;
            }
        }

        if (state.wasReleased(controllerButton)) {
            invoke(MOUSE_RELEASED, screen, getCursorX(), getCursorY(), mouseButton);
            if (mouseButton == 0) {
                leftHeld = false;
            } else {
                rightHeld = false;
            }
        }
    }

    private void handleScrolling(ControllerState state) {
        boolean dpadScrolls = !ControllerConfig.guiDpadNavigation;
        int direction = 0;
        if (state.isButtonDown(ControllerConfig.mapping.guiScrollUpButton)
            || (dpadScrolls && state.getPovY() < -0.5F)) {
            direction = -1;
        } else if (state.isButtonDown(ControllerConfig.mapping.guiScrollDownButton)
            || (dpadScrolls && state.getPovY() > 0.5F)) {
            direction = 1;
        }

        long repeatDelay = ControllerConfig.guiScrollRepeatMs;
        if (direction == 0) {
            float stick = dragScrollActive
                ? ControllerBindings.movementAxis(state, ControllerConfig.mapping.rightStickY)
                : ControllerBindings.movementAxis(state, ControllerConfig.mapping.leftStickY);
            if (dragScrollActive && ControllerConfig.invertCameraY) {
                stick = -stick;
            }
            if (stick != 0.0F) {
                direction = stick > 0.0F ? 1 : -1;
                float strength = Math.min(1.0F, Math.abs(stick));
                repeatDelay = Math.round(SLOWEST_ANALOG_SCROLL_MS
                    - (SLOWEST_ANALOG_SCROLL_MS - repeatDelay) * strength);
            }
        }
        dragScrollDirection = dragScrollActive ? direction : 0;

        long now = Minecraft.getSystemTime();
        if (direction == 0) {
            lastScrollDirection = 0;
            return;
        }

        if (direction != lastScrollDirection || now >= nextScrollTime) {
            if (robot != null) {
                robot.mouseWheel(direction);
            }
            nextScrollTime = now + (direction == lastScrollDirection
                ? repeatDelay
                : Math.max(repeatDelay, FIRST_SCROLL_REPEAT_MS));
            lastScrollDirection = direction;
        }
    }

    private void initializeCursor(Minecraft minecraft) {
        ScaledResolution resolution = new ScaledResolution(minecraft);
        readNativeCursor(minecraft, resolution);
    }

    private void readNativeCursor(Minecraft minecraft, ScaledResolution resolution) {
        if (minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            cursorX = resolution.getScaledWidth() / 2.0F;
            cursorY = resolution.getScaledHeight() / 2.0F;
            return;
        }

        expectedNativeX = Mouse.getX();
        expectedNativeY = Mouse.getY();
        cursorX = clamp(Mouse.getX() * resolution.getScaledWidth() / (float) minecraft.displayWidth,
            0.0F, resolution.getScaledWidth() - 1.0F);
        cursorY = clamp(resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) minecraft.displayHeight - 1.0F,
            0.0F, resolution.getScaledHeight() - 1.0F);
    }

    private void positionNativeCursor(Minecraft minecraft, ScaledResolution resolution) {
        int nativeX = Math.round(cursorX * minecraft.displayWidth / resolution.getScaledWidth());
        int nativeY = Math.round((resolution.getScaledHeight() - cursorY - 1.0F)
            * minecraft.displayHeight / resolution.getScaledHeight());
        Mouse.setCursorPosition(nativeX, nativeY);
        expectedNativeX = nativeX;
        expectedNativeY = nativeY;
    }

    private boolean physicalMouseMoved() {
        if (expectedNativeX == Integer.MIN_VALUE) {
            return false;
        }
        return Math.abs(Mouse.getX() - expectedNativeX) > MOUSE_MOVE_TOLERANCE
            || Math.abs(Mouse.getY() - expectedNativeY) > MOUSE_MOVE_TOLERANCE;
    }

    private void setPointerSource(PointerSource source) {
        if (pointerSource != source) {
            pointerSource = source;
            setNativeCursorHidden(source == PointerSource.CONTROLLER);
        }
    }

    private void setNativeCursorHidden(boolean hidden) {
        if (nativeCursorHidden == hidden) {
            return;
        }
        try {
            if (hidden) {
                Cursor cursor = getBlankCursor();
                if (cursor == null) {
                    return;
                }
                Mouse.setNativeCursor(cursor);
            } else {
                Mouse.setNativeCursor(null);
            }
            nativeCursorHidden = hidden;
        } catch (LWJGLException exception) {
            MinecraftControllerSupport.logger.warn("Unable to change native cursor visibility", exception);
        }
    }

    private Cursor getBlankCursor() {
        if (blankCursor == null && !blankCursorFailed) {
            try {
                if ((Cursor.getCapabilities() & Cursor.CURSOR_ONE_BIT_TRANSPARENCY) != 0) {
                    int size = Math.max(1, Cursor.getMinCursorSize());
                    blankCursor = new Cursor(size, size, 0, 0, 1,
                        BufferUtils.createIntBuffer(size * size), null);
                }
            } catch (LWJGLException exception) {
                MinecraftControllerSupport.logger.warn(
                    "Unable to create a transparent cursor; the OS cursor stays visible",
                    exception
                );
            }
            if (blankCursor == null) {
                blankCursorFailed = true;
            }
        }
        return blankCursor;
    }

    private void releaseMouseButtons() {
        leftHeld = false;
        rightHeld = false;
    }

    private static void invoke(Method method, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            MinecraftControllerSupport.logger.error("Unable to send controller input to GUI", exception);
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int getCursorX() {
        return Math.round(cursorX);
    }

    private int getCursorY() {
        return Math.round(cursorY);
    }

    public void reset() {
        releaseMouseButtons();
        activeScreen = null;
        lastScrollDirection = 0;
        dragScrollActive = false;
        dragScrollDirection = 0;
        navigator.reset();
        setPointerSource(PointerSource.MOUSE);
        expectedNativeX = Integer.MIN_VALUE;
        expectedNativeY = Integer.MIN_VALUE;
    }
}
