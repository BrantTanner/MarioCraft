package dev.mariocraft;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;

/** Lifecycle evidence only: this does not yet own player physics or claim playable movement. */
public final class LifecycleProbe {
    private final Logger log;
    private int ticks;
    LifecycleProbe(Logger log) { this.log = log; }
    @SubscribeEvent public void loaded(WorldEvent.Load event) {
        log.info("MARIOCRAFT world-load dim={} remote={}", event.getWorld().provider.getDimension(), event.getWorld().isRemote);
    }
    @SubscribeEvent public void saved(WorldEvent.Save event) {
        log.info("MARIOCRAFT world-save dim={}", event.getWorld().provider.getDimension());
    }
    @SubscribeEvent public void unloaded(WorldEvent.Unload event) {
        log.info("MARIOCRAFT world-unload dim={}", event.getWorld().provider.getDimension());
    }
    @SubscribeEvent public void playerSaved(PlayerEvent.SaveToFile event) {
        log.info("MARIOCRAFT player-save");
    }
    @SubscribeEvent public void playerLoaded(PlayerEvent.LoadFromFile event) {
        log.info("MARIOCRAFT player-load");
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Boolean.getBoolean("mariocraft.lifecycleProbe") && ++ticks == 120) {
            log.info("MARIOCRAFT lifecycle-probe stopping-after-ticks={}", ticks);
            FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown();
        }
    }
}
