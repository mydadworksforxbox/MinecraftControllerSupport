package com.kambo.minecraftcontrollersupport;

import com.kambo.minecraftcontrollersupport.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

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

    @SidedProxy(
        clientSide = "com.kambo.minecraftcontrollersupport.proxy.ClientProxy",
        serverSide = "com.kambo.minecraftcontrollersupport.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
