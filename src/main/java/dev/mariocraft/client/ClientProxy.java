package dev.mariocraft.client;
import dev.mariocraft.CommonProxy;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
public final class ClientProxy extends CommonProxy {
    @Override public void initialize(Logger log) {
        ControllerProfile.initialize();
        MinecraftForge.EVENT_BUS.register(new Presentation());
        if(PresentationProbe.INSTANCE!=null) MinecraftForge.EVENT_BUS.register(PresentationProbe.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new ControllerProfile());
        if(PersistenceProbe.INSTANCE!=null) MinecraftForge.EVENT_BUS.register(PersistenceProbe.INSTANCE);
        if (PresentationProbe.INSTANCE==null && PersistenceProbe.INSTANCE==null && Boolean.getBoolean("mariocraft.clientProbe")) MinecraftForge.EVENT_BUS.register(new ClientLifecycleProbe(log));
        if (dev.mariocraft.ScopeRules.enabled()) MinecraftForge.EVENT_BUS.register(new MarioPlayer(log));
    }
}
