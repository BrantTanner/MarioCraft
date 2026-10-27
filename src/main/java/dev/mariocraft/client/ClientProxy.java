package dev.mariocraft.client;
import dev.mariocraft.CommonProxy;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
public final class ClientProxy extends CommonProxy {
    @Override public void initialize(Logger log) {
        ControllerProfile.initialize();
        MinecraftForge.EVENT_BUS.register(new ControllerProfile());
        if (Boolean.getBoolean("mariocraft.clientProbe")) MinecraftForge.EVENT_BUS.register(new ClientLifecycleProbe(log));
        if (Boolean.getBoolean("mariocraft.gameplay")) MinecraftForge.EVENT_BUS.register(new MarioPlayer(log));
    }
}
