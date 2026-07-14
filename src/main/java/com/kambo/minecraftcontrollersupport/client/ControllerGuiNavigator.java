package com.kambo.minecraftcontrollersupport.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ControllerGuiNavigator {
    private static final long FIRST_MOVE_REPEAT_MS = 350L;
    private static final long MOVE_REPEAT_MS = 130L;
    private static final float DIRECTION_CONE_SLACK = 16.0F;

    private static final Field BUTTON_LIST = ReflectionHelper.findField(
        GuiScreen.class,
        "buttonList",
        "field_146292_n"
    );

    private int lastDirectionX;
    private int lastDirectionY;
    private long nextMoveTime;

    public float[] handleDpad(GuiScreen screen, ControllerState state, float cursorX, float cursorY) {
        int directionX = state.getPovX() > 0.5F ? 1 : state.getPovX() < -0.5F ? -1 : 0;
        int directionY = state.getPovY() > 0.5F ? 1 : state.getPovY() < -0.5F ? -1 : 0;
        if (directionX == 0 && directionY == 0) {
            lastDirectionX = 0;
            lastDirectionY = 0;
            return null;
        }

        long now = Minecraft.getSystemTime();
        boolean changed = directionX != lastDirectionX || directionY != lastDirectionY;
        if (!changed && now < nextMoveTime) {
            return null;
        }
        nextMoveTime = now + (changed ? FIRST_MOVE_REPEAT_MS : MOVE_REPEAT_MS);
        lastDirectionX = directionX;
        lastDirectionY = directionY;

        return findTarget(screen, cursorX, cursorY, directionX, directionY);
    }

    /** Whether a clickable target center lies within the given radius of the cursor. */
    public boolean isNearTarget(GuiScreen screen, float cursorX, float cursorY, float radius) {
        for (float[] target : collectTargets(screen)) {
            float deltaX = target[0] - cursorX;
            float deltaY = target[1] - cursorY;
            if (deltaX * deltaX + deltaY * deltaY <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    public void reset() {
        lastDirectionX = 0;
        lastDirectionY = 0;
    }

    private float[] findTarget(GuiScreen screen, float cursorX, float cursorY, int directionX, int directionY) {
        float length = (float) Math.sqrt(directionX * directionX + directionY * directionY);
        float unitX = directionX / length;
        float unitY = directionY / length;

        float[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (float[] target : collectTargets(screen)) {
            float deltaX = target[0] - cursorX;
            float deltaY = target[1] - cursorY;
            float along = deltaX * unitX + deltaY * unitY;
            if (along < 1.0F) {
                continue;
            }
            float across = Math.abs(deltaX * unitY - deltaY * unitX);
            if (across > along + DIRECTION_CONE_SLACK) {
                continue;
            }
            float score = along + across * 2.0F;
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private List<float[]> collectTargets(GuiScreen screen) {
        List<float[]> targets = new ArrayList<>();
        if (screen instanceof GuiContainer) {
            GuiContainer container = (GuiContainer) screen;
            for (Slot slot : container.inventorySlots.inventorySlots) {
                if (!slot.isEnabled()) {
                    continue;
                }
                targets.add(new float[] {
                    container.getGuiLeft() + slot.xPos + 8.5F,
                    container.getGuiTop() + slot.yPos + 8.5F
                });
            }
        }
        for (GuiButton button : getButtons(screen)) {
            if (button == null || !button.visible || !button.enabled) {
                continue;
            }
            targets.add(new float[] {
                button.x + button.width / 2.0F,
                button.y + button.height / 2.0F
            });
        }
        return targets;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtons(GuiScreen screen) {
        try {
            return (List<GuiButton>) BUTTON_LIST.get(screen);
        } catch (IllegalAccessException exception) {
            return Collections.emptyList();
        }
    }
}
