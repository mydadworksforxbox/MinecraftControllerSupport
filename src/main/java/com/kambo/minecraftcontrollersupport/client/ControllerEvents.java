package com.kambo.minecraftcontrollersupport.client;

import com.kambo.minecraftcontrollersupport.MinecraftControllerSupport;
import com.kambo.minecraftcontrollersupport.config.ControllerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.block.material.Material;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.util.Locale;

public final class ControllerEvents {
    private static final int CONFIG_BUTTON_ID = 926070;

    private final ControllerManager manager;
    private final ControllerGuiInput guiInput = new ControllerGuiInput();
    private final KeyBinding overlayKey = new KeyBinding(
        "key.minecraftcontrollersupport.overlay",
        Keyboard.KEY_F8,
        "key.categories.misc"
    );

    private boolean overlayVisible = ControllerConfig.showDebugOverlay;
    private boolean suppressGameplayButtons;
    private boolean attackWasActive;
    private boolean useWasActive;
    private boolean sneakToggled;
    private float lastKnownHealth = -1.0F;
    private long previousFrameNanos;
    private BlockPos trackedBreakingBlock;
    // Exponential moving averages of the mod's own work, shown on the F8 overlay
    // so lag can be attributed (or ruled out) with real numbers.
    private double tickWorkMs;
    private double frameWorkMs;

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
        long started = System.nanoTime();
        try {
            runClientTick();
        } finally {
            tickWorkMs += ((System.nanoTime() - started) / 1_000_000.0D - tickWorkMs) * 0.05D;
        }
    }

    private void runClientTick() {
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
        if (!Display.isActive()) {
            releaseSimulatedKeys(minecraft);
            return;
        }

        ControllerState state = manager.getState();
        boolean hadScreen = minecraft.currentScreen != null;
        guiInput.handleTick(minecraft, state);
        if (hadScreen) {
            suppressGameplayButtons = true;
        }
        if (minecraft.player == null || minecraft.world == null) {
            releaseSimulatedKeys(minecraft);
            sneakToggled = false;
            lastKnownHealth = -1.0F;
            return;
        }
        updateDamageRumble(minecraft);
        if (minecraft.currentScreen != null) {
            releaseSimulatedKeys(minecraft);
            return;
        }
        if (suppressGameplayButtons) {
            if (hadScreen || anyButtonDown(state)) {
                releaseSimulatedKeys(minecraft);
                return;
            }
            suppressGameplayButtons = false;
        }

        updateBlockBreakRumble(minecraft, state);

        boolean attack = state.isButtonDown(ControllerConfig.mapping.attackButton)
            || ControllerBindings.rightTrigger(state) >= ControllerConfig.mapping.triggerThreshold;
        boolean use = state.isButtonDown(ControllerConfig.mapping.useButton)
            || ControllerBindings.leftTrigger(state) >= ControllerConfig.mapping.triggerThreshold;

        if (attack && !attackWasActive) {
            KeyBinding.onTick(minecraft.gameSettings.keyBindAttack.getKeyCode());
            RayTraceResult swingTarget = minecraft.objectMouseOver;
            if (swingTarget != null && swingTarget.typeOfHit == RayTraceResult.Type.ENTITY) {
                manager.rumble(0.45F, 70);
            }
        }
        if (use && !useWasActive) {
            KeyBinding.onTick(minecraft.gameSettings.keyBindUseItem.getKeyCode());
        }
        // Only touch the key state on controller transitions; forcing it every tick
        // would stomp a physical mouse button held at the same time.
        if (attack != attackWasActive) {
            setKeyState(minecraft.gameSettings.keyBindAttack, attack);
        }
        if (use != useWasActive) {
            setKeyState(minecraft.gameSettings.keyBindUseItem, use);
        }
        attackWasActive = attack;
        useWasActive = use;

        if (state.wasPressed(ControllerConfig.mapping.inventoryButton)
            || state.wasPressed(ControllerConfig.mapping.secondaryInventoryButton)) {
            minecraft.displayGuiScreen(new GuiInventory(minecraft.player));
        } else if (state.wasPressed(ControllerConfig.mapping.pauseButton)) {
            minecraft.displayGuiScreen(new GuiIngameMenu());
        }

        if (state.wasPressed(ControllerConfig.mapping.dropButton)) {
            minecraft.player.dropItem(false);
        }

        if (state.wasPressed(ControllerConfig.mapping.sneakButton) && ControllerConfig.toggleSneak) {
            sneakToggled = !sneakToggled;
        }

        if (state.wasPressed(ControllerConfig.mapping.perspectiveButton) || state.povUpPressed()) {
            minecraft.gameSettings.thirdPersonView = (minecraft.gameSettings.thirdPersonView + 1) % 3;
            minecraft.renderGlobal.setDisplayListEntitiesDirty();
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
        MovementInput input = event.getMovementInput();
        float strafe = -ControllerBindings.movementAxis(state, ControllerConfig.mapping.leftStickX);
        float forward = -ControllerBindings.movementAxis(state, ControllerConfig.mapping.leftStickY);
        if (ControllerConfig.invertMovementX) {
            strafe = -strafe;
        }
        if (ControllerConfig.invertMovementY) {
            forward = -forward;
        }

        boolean controllerSneak = ControllerConfig.toggleSneak
            ? sneakToggled
            : state.isButtonDown(ControllerConfig.mapping.sneakButton);
        boolean keyboardSneak = input.sneak;
        if (state.isButtonDown(ControllerConfig.mapping.jumpButton)) {
            input.jump = true;
        }
        if (controllerSneak) {
            input.sneak = true;
        }

        if (strafe != 0.0F || forward != 0.0F) {
            if (input.sneak) {
                strafe *= 0.3F;
                forward *= 0.3F;
            }
            input.moveStrafe = strafe;
            input.moveForward = forward;
        } else if (controllerSneak && !keyboardSneak) {
            input.moveStrafe *= 0.3F;
            input.moveForward *= 0.3F;
        }

        if (state.isButtonDown(ControllerConfig.mapping.sprintButton)
            && forward > 0.1F
            && !input.sneak
            && !minecraft.player.isSprinting()) {
            minecraft.player.setSprinting(true);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        long started = System.nanoTime();
        try {
            runRenderTick(started);
        } finally {
            frameWorkMs += ((System.nanoTime() - started) / 1_000_000.0D - frameWorkMs) * 0.05D;
        }
    }

    private void runRenderTick(long now) {
        float deltaSeconds = previousFrameNanos == 0L
            ? 0.0F
            : Math.min((now - previousFrameNanos) / 1_000_000_000.0F, 0.1F);
        previousFrameNanos = now;

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ControllerConfig.enabled
            || !manager.isConnected()
            || !Display.isActive()) {
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
        if (horizontal == 0.0F && vertical == 0.0F) {
            return;
        }

        float yawDelta = horizontal * (float) ControllerConfig.horizontalCameraSpeed * deltaSeconds;
        float pitchDelta = vertical * (float) ControllerConfig.verticalCameraSpeed * deltaSeconds;
        minecraft.player.turn(yawDelta / 0.15F, -pitchDelta / 0.15F);
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu) {
            event.getButtonList().add(new GuiButton(
                CONFIG_BUTTON_ID,
                event.getGui().width / 2 + 104,
                event.getGui().height / 4 + 8,
                98,
                20,
                "Controller..."
            ));
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getGui() instanceof GuiIngameMenu && event.getButton().id == CONFIG_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiConfig(
                event.getGui(),
                MinecraftControllerSupport.MOD_ID,
                MinecraftControllerSupport.NAME
            ));
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MinecraftControllerSupport.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(MinecraftControllerSupport.MOD_ID, Config.Type.INSTANCE);
        }
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
        if (!manager.isConnected()) {
            return;
        }

        Controller controller = manager.getController();
        if (controller != null) {
            event.getLeft().add(controller.getName());
        }
        event.getLeft().add("Rumble: " + manager.getRumbleStatus());
        event.getLeft().add(String.format(
            Locale.ROOT,
            "Mod work: %.3f ms/tick, %.3f ms/frame",
            tickWorkMs,
            frameWorkMs
        ));

        ControllerState state = manager.getState();
        for (int i = 0; i < state.getAxisCount(); i++) {
            event.getLeft().add(String.format(
                Locale.ROOT,
                "Axis %d (%s): %.3f",
                i,
                controller == null ? "XInput" : safeAxisName(controller, i),
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
                controller == null ? "XInput" : safeButtonName(controller, i),
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

    private static boolean anyButtonDown(ControllerState state) {
        for (int i = 0; i < state.getButtonCount(); i++) {
            if (state.isButtonDown(i)) {
                return true;
            }
        }
        return false;
    }

    private void updateBlockBreakRumble(Minecraft minecraft, ControllerState state) {
        if (trackedBreakingBlock != null
            && minecraft.world.getBlockState(trackedBreakingBlock).getMaterial() == Material.AIR) {
            manager.rumble(minecraft.player.capabilities.isCreativeMode ? 0.5F : 1.0F,
                minecraft.player.capabilities.isCreativeMode ? 70 : 140);
            trackedBreakingBlock = null;
        }

        boolean attackDown = state.isButtonDown(ControllerConfig.mapping.attackButton)
            || ControllerBindings.rightTrigger(state) >= ControllerConfig.mapping.triggerThreshold
            || minecraft.gameSettings.keyBindAttack.isKeyDown();
        RayTraceResult hit = minecraft.objectMouseOver;
        if (attackDown && hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos position = hit.getBlockPos();
            if (minecraft.world.getBlockState(position).getMaterial() != Material.AIR) {
                trackedBreakingBlock = position.toImmutable();
            }
        } else if (!attackDown) {
            trackedBreakingBlock = null;
        }
    }

    /**
     * Client-side damage rumble: health is synced to the client, so a drop in
     * health while the hurt animation plays covers falls, mobs, fire, and works
     * on servers too. Scales with how hard the hit was.
     */
    private void updateDamageRumble(Minecraft minecraft) {
        float health = minecraft.player.getHealth();
        if (lastKnownHealth > 0.0F && health < lastKnownHealth - 0.01F) {
            if (health <= 0.0F) {
                manager.rumble(1.0F, 900);
            } else if (minecraft.player.hurtTime > 0) {
                float damage = lastKnownHealth - health;
                float strength = Math.min(1.0F, 0.3F + damage * 0.07F);
                int durationMs = (int) Math.min(350.0F, 90.0F + damage * 20.0F);
                manager.rumble(strength, durationMs);
            }
        }
        lastKnownHealth = health;
    }

    private static void changeHotbarSlot(Minecraft minecraft, int direction) {
        int current = minecraft.player.inventory.currentItem;
        minecraft.player.inventory.currentItem = Math.floorMod(current + direction, 9);
    }

    private static void setKeyState(KeyBinding binding, boolean pressed) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
    }

    private void releaseSimulatedKeys(Minecraft minecraft) {
        if (attackWasActive) {
            setKeyState(minecraft.gameSettings.keyBindAttack, false);
        }
        if (useWasActive) {
            setKeyState(minecraft.gameSettings.keyBindUseItem, false);
        }
        attackWasActive = false;
        useWasActive = false;
    }
}
