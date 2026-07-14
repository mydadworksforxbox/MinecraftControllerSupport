package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import com.kambo.minecraftcontrollersupport.config.ControllerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Drives vanilla screens and containers through their normal mouse handlers. */
public final class ControllerGuiInput {
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
    private static final Method MOUSE_DRAGGED = ReflectionHelper.findMethod(
        GuiScreen.class,
        "mouseClickMove",
        "func_146273_a",
        int.class,
        int.class,
        int.class,
        long.class
    );

    private GuiScreen activeScreen;
    private float cursorX;
    private float cursorY;
    private long leftClickStarted;
    private long rightClickStarted;
    private boolean leftHeld;
    private boolean rightHeld;

    public void handleTick(Minecraft minecraft, ControllerState state) {
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null) {
            reset();
            return;
        }

        if (screen != activeScreen) {
            activeScreen = screen;
            initializeCursor(minecraft);
            leftHeld = false;
            rightHeld = false;
        }

        if (state.wasPressed(ControllerConfig.mapping.guiBackButton)
            || state.wasPressed(ControllerConfig.mapping.backButton)) {
            minecraft.displayGuiScreen(null);
            reset();
            return;
        }

        handleMouseButton(screen, state, ControllerConfig.mapping.guiClickButton, 0);
        handleMouseButton(screen, state, ControllerConfig.mapping.guiRightClickButton, 1);

        if (leftHeld) {
            invoke(MOUSE_DRAGGED, screen, getCursorX(), getCursorY(), 0,
                Minecraft.getSystemTime() - leftClickStarted);
        }
        if (rightHeld) {
            invoke(MOUSE_DRAGGED, screen, getCursorX(), getCursorY(), 1,
                Minecraft.getSystemTime() - rightClickStarted);
        }
    }

    public void updateCursor(Minecraft minecraft, ControllerState state, float deltaSeconds) {
        if (minecraft.currentScreen == null || minecraft.currentScreen != activeScreen) {
            return;
        }

        float horizontal = ControllerBindings.cameraAxis(state, ControllerConfig.mapping.rightStickX);
        float vertical = ControllerBindings.cameraAxis(state, ControllerConfig.mapping.rightStickY);
        if (ControllerConfig.invertCameraX) {
            horizontal = -horizontal;
        }
        if (ControllerConfig.invertCameraY) {
            vertical = -vertical;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        float speed = (float) ControllerConfig.guiCursorSpeed;
        cursorX = Math.max(0.0F, Math.min(resolution.getScaledWidth() - 1.0F,
            cursorX + horizontal * speed * deltaSeconds));
        cursorY = Math.max(0.0F, Math.min(resolution.getScaledHeight() - 1.0F,
            cursorY + vertical * speed * deltaSeconds));
        positionNativeCursor(minecraft, resolution);
    }

    public void drawCursor(GuiScreen screen) {
        if (screen != activeScreen) {
            return;
        }

        int x = getCursorX();
        int y = getCursorY();
        Gui.drawRect(x - 5, y, x + 6, y + 1, 0xFFFFFFFF);
        Gui.drawRect(x, y - 5, x + 1, y + 6, 0xFFFFFFFF);
        Gui.drawRect(x - 2, y - 2, x + 3, y + 3, 0xFF202020);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    private void handleMouseButton(GuiScreen screen, ControllerState state, int controllerButton, int mouseButton) {
        if (state.wasPressed(controllerButton)) {
            invoke(MOUSE_CLICKED, screen, getCursorX(), getCursorY(), mouseButton);
            if (mouseButton == 0) {
                leftHeld = true;
                leftClickStarted = Minecraft.getSystemTime();
            } else {
                rightHeld = true;
                rightClickStarted = Minecraft.getSystemTime();
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

    private void initializeCursor(Minecraft minecraft) {
        ScaledResolution resolution = new ScaledResolution(minecraft);
        if (minecraft.displayWidth > 0 && minecraft.displayHeight > 0) {
            cursorX = Mouse.getX() * resolution.getScaledWidth() / (float) minecraft.displayWidth;
            cursorY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) minecraft.displayHeight - 1.0F;
        } else {
            cursorX = resolution.getScaledWidth() / 2.0F;
            cursorY = resolution.getScaledHeight() / 2.0F;
        }
        cursorX = Math.max(0.0F, Math.min(resolution.getScaledWidth() - 1.0F, cursorX));
        cursorY = Math.max(0.0F, Math.min(resolution.getScaledHeight() - 1.0F, cursorY));
    }

    private void positionNativeCursor(Minecraft minecraft, ScaledResolution resolution) {
        int nativeX = Math.round(cursorX * minecraft.displayWidth / resolution.getScaledWidth());
        int nativeY = Math.round((resolution.getScaledHeight() - cursorY - 1.0F)
            * minecraft.displayHeight / resolution.getScaledHeight());
        Mouse.setCursorPosition(nativeX, nativeY);
    }

    private static void invoke(Method method, GuiScreen screen, Object... arguments) {
        try {
            method.invoke(screen, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            MinecraftControllerSupport.logger.error("Unable to send controller input to GUI", exception);
        }
    }

    private int getCursorX() {
        return Math.round(cursorX);
    }

    private int getCursorY() {
        return Math.round(cursorY);
    }

    public void reset() {
        activeScreen = null;
        leftHeld = false;
        rightHeld = false;
    }
}
