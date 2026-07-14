package com.kambo.minecraftcontrollersupport.proxy;

import com.kambo.minecraftcontrollersupport.client.ControllerEvents;
import com.kambo.minecraftcontrollersupport.client.ControllerManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ControllerManager controllerManager = new ControllerManager();
        ControllerEvents events = new ControllerEvents(controllerManager);

        ClientRegistry.registerKeyBinding(events.getOverlayKey());
        MinecraftForge.EVENT_BUS.register(events);
        controllerManager.initialize();
    }
}
