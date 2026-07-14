package com.kambo.minecraftcontrollersupport.client;

import org.lwjgl.input.Controller;

public final class ControllerState {
    private float[] axes = new float[0];
    private boolean[] buttons = new boolean[0];
    private boolean[] previousButtons = new boolean[0];
    private float povX;
    private float povY;

    public void update(Controller controller) {
        ensureCapacity(controller.getAxisCount(), controller.getButtonCount());
        System.arraycopy(buttons, 0, previousButtons, 0, buttons.length);

        for (int i = 0; i < axes.length; i++) {
            axes[i] = controller.getAxisValue(i);
        }
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = controller.isButtonPressed(i);
        }

        povX = controller.getPovX();
        povY = controller.getPovY();
    }

    public void clear() {
        axes = new float[0];
        buttons = new boolean[0];
        previousButtons = new boolean[0];
        povX = 0.0F;
        povY = 0.0F;
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
        }
        if (buttons.length != buttonCount) {
            buttons = new boolean[buttonCount];
            previousButtons = new boolean[buttonCount];
        }
    }
}
