package dev.mariocraft;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import org.apache.logging.log4j.Logger;

@Mod(modid = MarioCraft.ID, name = "MarioCraft", version = "0.1.0", acceptedMinecraftVersions = "[1.12.2]", guiFactory = "dev.mariocraft.client.ControlsGuiFactory")
public final class MarioCraft {
    public static final String ID = "mariocraft";
    @SidedProxy(clientSide = "dev.mariocraft.client.ClientProxy", serverSide = "dev.mariocraft.CommonProxy")
    public static CommonProxy proxy;
    private Logger log;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        MinecraftForge.EVENT_BUS.register(new LifecycleProbe(log));
        MinecraftForge.EVENT_BUS.register(new PlayerBridge());
        log.info("MARIOCRAFT bootstrap version=0.1.0 forge=14.23.5.2864");
        proxy.initialize(log);
    }
    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        log.info("MARIOCRAFT server-starting dedicated={}", event.getServer().isDedicatedServer());
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) {
        PlayerBridge.POSES.clear();
        log.info("MARIOCRAFT server-stopped");
    }
}
