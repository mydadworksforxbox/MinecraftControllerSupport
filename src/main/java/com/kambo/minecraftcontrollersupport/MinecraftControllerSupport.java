package com.kambo.minecraftcontrollersupport;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = MinecraftControllerSupport.MOD_ID,
    name = MinecraftControllerSupport.NAME,
    version = MinecraftControllerSupport.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class MinecraftControllerSupport {
    public static final String MOD_ID = "minecraftcontrollersupport";
    public static final String NAME = "Minecraft Controller Support";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MOD_ID)
    public static MinecraftControllerSupport instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
    }
}
