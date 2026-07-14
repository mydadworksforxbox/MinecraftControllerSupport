package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Controllers;

public final class ControllerManager {
    private static final int RESCAN_INTERVAL_TICKS = 200;

    private final ControllerState state = new ControllerState();
    private Controller controller;
    private int ticksUntilRescan;
    private String status = "Not initialized";

    public void initialize() {
        scanForController();
    }

    public void poll() {
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
        } catch (RuntimeException exception) {
            MinecraftControllerSupport.logger.warn("Controller disconnected while polling", exception);
            controller = null;
            state.clear();
            status = "Disconnected; rescanning";
            ticksUntilRescan = 0;
        }
    }

    public Controller getController() {
        return controller;
    }

    public ControllerState getState() {
        return state;
    }

    public boolean isConnected() {
        return controller != null;
    }

    public String getStatus() {
        return status;
    }

    private void scanForController() {
        ticksUntilRescan = RESCAN_INTERVAL_TICKS;

        try {
            if (Controllers.isCreated()) {
                Controllers.destroy();
            }
            Controllers.create();

            controller = null;
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
            }
        } catch (LWJGLException | RuntimeException exception) {
            controller = null;
            state.clear();
            status = "Initialization failed: " + exception.getMessage();
            MinecraftControllerSupport.logger.error("Unable to initialize LWJGL controllers", exception);
        }
    }
}
