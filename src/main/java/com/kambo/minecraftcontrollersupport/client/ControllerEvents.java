package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.config.ControllerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

public final class ControllerEvents {
    private final ControllerManager manager;
    private final ControllerGuiInput guiInput = new ControllerGuiInput();
    private final KeyBinding overlayKey = new KeyBinding(
        "key.minecraftcontrollersupport.overlay",
        Keyboard.KEY_F8,
        "key.categories.misc"
    );

    private boolean overlayVisible = ControllerConfig.showDebugOverlay;
    private long previousFrameNanos;

    public ControllerEvents(ControllerManager manager) {
        this.manager = manager;
    }

    public KeyBinding getOverlayKey() {
        return overlayKey;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        manager.poll();
        while (overlayKey.isPressed()) {
            overlayVisible = !overlayVisible;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ControllerConfig.enabled || !manager.isConnected()) {
            releaseSimulatedKeys(minecraft);
            guiInput.reset();
            return;
        }

        ControllerState state = manager.getState();
        guiInput.handleTick(minecraft, state);
        if (minecraft.player == null || minecraft.world == null || minecraft.currentScreen != null) {
            releaseSimulatedKeys(minecraft);
            return;
        }

        boolean attack = state.isButtonDown(ControllerConfig.mapping.attackButton)
            || ControllerBindings.rightTrigger(state) >= ControllerConfig.mapping.triggerThreshold;
        boolean use = state.isButtonDown(ControllerConfig.mapping.useButton)
            || ControllerBindings.leftTrigger(state) >= ControllerConfig.mapping.triggerThreshold;

        setKeyState(minecraft.gameSettings.keyBindAttack, attack);
        setKeyState(minecraft.gameSettings.keyBindUseItem, use);

        if (state.wasPressed(ControllerConfig.mapping.inventoryButton)) {
            minecraft.displayGuiScreen(new GuiInventory(minecraft.player));
        } else if (state.wasPressed(ControllerConfig.mapping.dropButton)) {
            minecraft.player.dropItem(false);
        } else if (state.wasPressed(ControllerConfig.mapping.pauseButton)) {
            minecraft.displayGuiScreen(new GuiIngameMenu());
        }

        if (state.wasPressed(ControllerConfig.mapping.previousHotbarButton)) {
            changeHotbarSlot(minecraft, -1);
        }
        if (state.wasPressed(ControllerConfig.mapping.nextHotbarButton)) {
            changeHotbarSlot(minecraft, 1);
        }
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ControllerConfig.enabled
            || !manager.isConnected()
            || minecraft.currentScreen != null
            || event.getEntityPlayer() != minecraft.player) {
            return;
        }

        ControllerState state = manager.getState();
        // Minecraft uses positive strafe for left, while controller X is positive for right.
        float strafe = -ControllerBindings.movementAxis(state, ControllerConfig.mapping.leftStickX);
        float forward = -ControllerBindings.movementAxis(state, ControllerConfig.mapping.leftStickY);
        if (ControllerConfig.invertMovementX) {
            strafe = -strafe;
        }
        if (ControllerConfig.invertMovementY) {
            forward = -forward;
        }

        event.getMovementInput().moveStrafe = strafe;
        event.getMovementInput().moveForward = forward;
        event.getMovementInput().jump = state.isButtonDown(ControllerConfig.mapping.jumpButton);
        event.getMovementInput().sneak = state.isButtonDown(ControllerConfig.mapping.sneakButton);

        if (state.isButtonDown(ControllerConfig.mapping.sprintButton) && forward > 0.1F) {
            minecraft.player.setSprinting(true);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        long now = System.nanoTime();
        float deltaSeconds = previousFrameNanos == 0L
            ? 0.0F
            : Math.min((now - previousFrameNanos) / 1_000_000_000.0F, 0.1F);
        previousFrameNanos = now;

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ControllerConfig.enabled
            || !manager.isConnected()) {
            return;
        }

        ControllerState state = manager.getState();
        if (minecraft.currentScreen != null) {
            guiInput.updateCursor(minecraft, state, deltaSeconds);
            return;
        }
        if (minecraft.player == null || minecraft.world == null) {
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

        minecraft.player.rotationYaw += horizontal
            * (float) ControllerConfig.horizontalCameraSpeed
            * deltaSeconds;
        minecraft.player.rotationPitch = MathHelper.clamp(
            minecraft.player.rotationPitch
                + vertical * (float) ControllerConfig.verticalCameraSpeed * deltaSeconds,
            -90.0F,
            90.0F
        );
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (ControllerConfig.enabled && manager.isConnected()) {
            guiInput.drawCursor(event.getGui());
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (!overlayVisible) {
            return;
        }

        event.getLeft().add("");
        event.getLeft().add("Controller: " + manager.getStatus());

        Controller controller = manager.getController();
        if (controller == null) {
            return;
        }

        ControllerState state = manager.getState();
        event.getLeft().add(controller.getName());
        for (int i = 0; i < state.getAxisCount(); i++) {
            event.getLeft().add(String.format(
                Locale.ROOT,
                "Axis %d (%s): %.3f",
                i,
                safeAxisName(controller, i),
                state.getAxis(i)
            ));
        }
        event.getLeft().add(String.format(
            Locale.ROOT,
            "POV: %.1f, %.1f",
            state.getPovX(),
            state.getPovY()
        ));
        for (int i = 0; i < state.getButtonCount(); i++) {
            event.getLeft().add(String.format(
                Locale.ROOT,
                "Button %d (%s): %s",
                i,
                safeButtonName(controller, i),
                state.isButtonDown(i) ? "DOWN" : "UP"
            ));
        }
    }

    private static String safeAxisName(Controller controller, int index) {
        String name = controller.getAxisName(index);
        return name == null || name.isEmpty() ? "unknown" : name;
    }

    private static String safeButtonName(Controller controller, int index) {
        String name = controller.getButtonName(index);
        return name == null || name.isEmpty() ? "unknown" : name;
    }

    private static void changeHotbarSlot(Minecraft minecraft, int direction) {
        int current = minecraft.player.inventory.currentItem;
        minecraft.player.inventory.currentItem = Math.floorMod(current + direction, 9);
    }

    private static void setKeyState(KeyBinding binding, boolean pressed) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
    }

    private static void releaseSimulatedKeys(Minecraft minecraft) {
        setKeyState(minecraft.gameSettings.keyBindAttack, false);
        setKeyState(minecraft.gameSettings.keyBindUseItem, false);
    }
}
