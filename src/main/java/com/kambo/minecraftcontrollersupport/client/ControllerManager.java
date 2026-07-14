package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import com.kambo.minecraftcontrollersupport.config.ControllerConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Controllers;

public final class ControllerManager {
    private static final int RESCAN_INTERVAL_TICKS = 200;

    private final ControllerState state = new ControllerState();
    private final XInputDevice xInput = new XInputDevice();
    private boolean xInputActive;
    private Controller controller;
    private int ticksUntilRescan;
    private String status = "Not initialized";
    private boolean rumbling;
    private boolean rumbleUnavailableLogged;
    private long rumbleEndTime;

    public void initialize() {
        scanForController();
    }

    public void poll() {
        if (xInputActive) {
            XInputDevice.XInputGamepad gamepad = xInput.poll();
            if (gamepad == null) {
                xInputActive = false;
                rumbling = false;
                state.clear();
                status = "Disconnected; rescanning";
                ticksUntilRescan = 0;
                return;
            }
            state.update(gamepad);
            updateRumbleTimeout();
            return;
        }

        if (controller == null) {
            if (ticksUntilRescan-- <= 0) {
                scanForController();
            }
            return;
        }

        try {
            Controllers.poll();
            state.update(controller);
            status = "Connected";
            updateRumbleTimeout();
        } catch (RuntimeException exception) {
            MinecraftControllerSupport.logger.warn("Controller disconnected while polling", exception);
            xInput.stop();
            controller = null;
            rumbling = false;
            state.clear();
            status = "Disconnected; rescanning";
            ticksUntilRescan = 0;
        }
    }

    public void rumble(float strength, int durationMs) {
        if (!ControllerConfig.rumbleEnabled || !isConnected()) {
            return;
        }
        if (!xInputActive && controller.getRumblerCount() == 0 && !xInput.isConnected()) {
            if (!rumbleUnavailableLogged) {
                rumbleUnavailableLogged = true;
                MinecraftControllerSupport.logger.warn(
                    "Controller '{}' exposes no rumble motors through LWJGL/JInput",
                    controller.getName()
                );
            }
            return;
        }
        try {
            applyRumble(strength * (float) ControllerConfig.rumbleStrength);
            rumbleEndTime = Minecraft.getSystemTime() + durationMs;
            rumbling = true;
        } catch (RuntimeException exception) {
            MinecraftControllerSupport.logger.warn("Unable to start controller rumble", exception);
        }
    }

    private void updateRumbleTimeout() {
        if (rumbling && Minecraft.getSystemTime() >= rumbleEndTime) {
            applyRumble(0.0F);
            rumbling = false;
        }
    }

    private void applyRumble(float strength) {
        if (xInputActive || controller == null || controller.getRumblerCount() == 0) {
            xInput.setStrength(strength);
            return;
        }
        for (int i = 0; i < controller.getRumblerCount(); i++) {
            controller.setRumblerStrength(i, strength);
        }
    }

    public Controller getController() {
        return controller;
    }

    public ControllerState getState() {
        return state;
    }

    public boolean isConnected() {
        return xInputActive || controller != null;
    }

    public String getStatus() {
        return status;
    }

    public String getRumbleStatus() {
        if (!isConnected()) {
            return "Disconnected";
        }
        if (xInputActive) {
            return "Windows XInput";
        }
        if (controller.getRumblerCount() > 0) {
            return "JInput motors: " + controller.getRumblerCount();
        }
        return xInput.isConnected() ? "Windows XInput fallback" : "Unavailable";
    }

    private void scanForController() {
        ticksUntilRescan = RESCAN_INTERVAL_TICKS;

        if (xInput.connectFirstAvailable()) {
            xInputActive = true;
            controller = null;
            rumbleUnavailableLogged = false;
            destroyLwjglControllers();
            state.clear();
            status = "Connected (XInput)";
            MinecraftControllerSupport.logger.info("Using native XInput for controller input and rumble");
            return;
        }
        xInputActive = false;

        try {
            destroyLwjglControllers();
            Controllers.create();

            controller = null;
            rumbleUnavailableLogged = false;
            for (int i = 0; i < Controllers.getControllerCount(); i++) {
                Controller candidate = Controllers.getController(i);
                if (controller == null && candidate.getAxisCount() >= 2) {
                    controller = candidate;
                    MinecraftControllerSupport.logger.info(
                        "Controller found: {} ({} axes, {} buttons)",
                        candidate.getName(),
                        candidate.getAxisCount(),
                        candidate.getButtonCount()
                    );
                    for (int axis = 0; axis < candidate.getAxisCount(); axis++) {
                        MinecraftControllerSupport.logger.info(
                            "Controller axis {}: {}",
                            axis,
                            candidate.getAxisName(axis)
                        );
                    }
                }
            }

            if (controller == null) {
                state.clear();
                status = "No controller detected";
            } else {
                controller.poll();
                state.update(controller);
                status = "Connected";
                MinecraftControllerSupport.logger.info("Using controller: {}", controller.getName());
                MinecraftControllerSupport.logger.info(
                    "Controller rumble motors: {}",
                    controller.getRumblerCount()
                );
            }
        } catch (LWJGLException | RuntimeException exception) {
            controller = null;
            state.clear();
            status = "Initialization failed: " + exception.getMessage();
            MinecraftControllerSupport.logger.error("Unable to initialize LWJGL controllers", exception);
        }
    }

    private void destroyLwjglControllers() {
        if (Controllers.isCreated()) {
            Controllers.destroy();
        }
    }
}
