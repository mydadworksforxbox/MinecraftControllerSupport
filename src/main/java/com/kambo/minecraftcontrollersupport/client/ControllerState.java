package com.kambo.minecraftcontrollersupport.client;

import org.lwjgl.input.Controller;

public final class ControllerState {
    private float[] axes = new float[0];
    private boolean[] buttons = new boolean[0];
    private boolean[] previousButtons = new boolean[0];
    private boolean[] axisTrusted = new boolean[0];
    private float povX;
    private float povY;
    private float previousPovY;

    public void update(Controller controller) {
        ensureCapacity(controller.getAxisCount(), controller.getButtonCount());
        System.arraycopy(buttons, 0, previousButtons, 0, buttons.length);

        for (int i = 0; i < axes.length; i++) {
            float value = controller.getAxisValue(i);
            if (!axisTrusted[i]) {
                if (Math.abs(value) <= 0.35F) {
                    axisTrusted[i] = true;
                } else {
                    value = 0.0F;
                }
            }
            axes[i] = value;
        }
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = controller.isButtonPressed(i);
        }

        previousPovY = povY;
        povX = controller.getPovX();
        povY = controller.getPovY();
    }

    public void update(XInputDevice.XInputGamepad gamepad) {
        ensureCapacity(XInputDevice.AXIS_COUNT, XInputDevice.BUTTON_COUNT);
        System.arraycopy(buttons, 0, previousButtons, 0, buttons.length);

        axes[0] = -gamepad.thumbLY / 32768.0F;
        axes[1] = gamepad.thumbLX / 32768.0F;
        axes[2] = -gamepad.thumbRY / 32768.0F;
        axes[3] = gamepad.thumbRX / 32768.0F;
        axes[4] = ((gamepad.rightTrigger & 0xFF) - (gamepad.leftTrigger & 0xFF)) / 255.0F;

        int mask = gamepad.buttons & 0xFFFF;
        buttons[0] = (mask & XInputDevice.XInputGamepad.A) != 0;
        buttons[1] = (mask & XInputDevice.XInputGamepad.B) != 0;
        buttons[2] = (mask & XInputDevice.XInputGamepad.X) != 0;
        buttons[3] = (mask & XInputDevice.XInputGamepad.Y) != 0;
        buttons[4] = (mask & XInputDevice.XInputGamepad.LEFT_SHOULDER) != 0;
        buttons[5] = (mask & XInputDevice.XInputGamepad.RIGHT_SHOULDER) != 0;
        buttons[6] = (mask & XInputDevice.XInputGamepad.BACK) != 0;
        buttons[7] = (mask & XInputDevice.XInputGamepad.START) != 0;
        buttons[8] = (mask & XInputDevice.XInputGamepad.LEFT_THUMB) != 0;
        buttons[9] = (mask & XInputDevice.XInputGamepad.RIGHT_THUMB) != 0;

        previousPovY = povY;
        povX = ((mask & XInputDevice.XInputGamepad.DPAD_RIGHT) != 0 ? 1.0F : 0.0F)
            - ((mask & XInputDevice.XInputGamepad.DPAD_LEFT) != 0 ? 1.0F : 0.0F);
        povY = ((mask & XInputDevice.XInputGamepad.DPAD_DOWN) != 0 ? 1.0F : 0.0F)
            - ((mask & XInputDevice.XInputGamepad.DPAD_UP) != 0 ? 1.0F : 0.0F);
    }

    public void clear() {
        axes = new float[0];
        buttons = new boolean[0];
        previousButtons = new boolean[0];
        axisTrusted = new boolean[0];
        povX = 0.0F;
        povY = 0.0F;
        previousPovY = 0.0F;
    }

    public boolean povDownPressed() {
        return povY > 0.5F && previousPovY <= 0.5F;
    }

    public boolean povUpPressed() {
        return povY < -0.5F && previousPovY >= -0.5F;
    }

    public float getAxis(int index) {
        return index >= 0 && index < axes.length ? axes[index] : 0.0F;
    }

    public boolean isButtonDown(int index) {
        return index >= 0 && index < buttons.length && buttons[index];
    }

    public boolean wasPressed(int index) {
        return index >= 0 && index < buttons.length
            && buttons[index]
            && !previousButtons[index];
    }

    public boolean wasReleased(int index) {
        return index >= 0 && index < buttons.length
            && !buttons[index]
            && previousButtons[index];
    }

    public int getAxisCount() {
        return axes.length;
    }

    public int getButtonCount() {
        return buttons.length;
    }

    public float getPovX() {
        return povX;
    }

    public float getPovY() {
        return povY;
    }

    private void ensureCapacity(int axisCount, int buttonCount) {
        if (axes.length != axisCount) {
            axes = new float[axisCount];
            axisTrusted = new boolean[axisCount];
        }
        if (buttons.length != buttonCount) {
            buttons = new boolean[buttonCount];
            previousButtons = new boolean[buttonCount];
        }
    }
}
