package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.config.ControllerConfig;

public final class ControllerBindings {
    private ControllerBindings() {
    }

    public static float movementAxis(ControllerState state, int index) {
        return applyDeadzone(state.getAxis(index), (float) ControllerConfig.movementDeadzone);
    }

    public static float cameraAxis(ControllerState state, int index) {
        float value = applyDeadzone(state.getAxis(index), (float) ControllerConfig.cameraDeadzone);
        return Math.copySign(value * value, value);
    }

    public static float leftTrigger(ControllerState state) {
        int index = ControllerConfig.mapping.sharedTriggerAxis;
        return index < 0 ? 0.0F : Math.max(0.0F, -state.getAxis(index));
    }

    public static float rightTrigger(ControllerState state) {
        int index = ControllerConfig.mapping.sharedTriggerAxis;
        return index < 0 ? 0.0F : Math.max(0.0F, state.getAxis(index));
    }

    public static float applyDeadzone(float value, float deadzone) {
        float absolute = Math.abs(value);
        if (absolute <= deadzone) {
            return 0.0F;
        }

        float normalized = (absolute - deadzone) / (1.0F - deadzone);
        return Math.copySign(normalized, value);
    }
}
