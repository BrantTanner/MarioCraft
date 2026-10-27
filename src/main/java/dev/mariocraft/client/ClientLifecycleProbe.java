package dev.mariocraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

/** Opt-in, bounded real integrated-player lifecycle test using one isolated normal world. */
public final class ClientLifecycleProbe {
    private final Logger log;
    private int phase, ticks, waits;
    private long deadline;
    private boolean captured;
    ClientLifecycleProbe(Logger log) { this.log = log; }
    @SubscribeEvent public void tick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (deadline == 0) deadline = System.nanoTime() + 240000000000L;
        if (System.nanoTime() > deadline) { log.error("MARIOCRAFT CLIENT_PROBE timeout phase={}", phase); if(Boolean.getBoolean("mariocraft.gameplayProbe")) GameplayProbe.fail("timeout"); mc.shutdown(); return; }
        // Keep the test window away from the user's desktop; no synthetic input or focus request.
        Display.setLocation(-20000, -20000);
        mc.gameSettings.pauseOnLostFocus = false;
        mc.gameSettings.renderDistanceChunks = 4;
        if(Boolean.getBoolean("mariocraft.gameplayProbe") && GameplayProbe.currentFrame>=200 && !captured && mc.world!=null) {
            captured=true;
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"mariocraft-gameplay.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            log.info("MARIOCRAFT GAMEPLAY_PROBE screenshot-local");
        }
        if (phase == 0 && mc.currentScreen instanceof GuiMainMenu && ++waits > 20) {
            log.info("MARIOCRAFT CLIENT_PROBE opening normal-world");
            phase = 1;
            mc.launchIntegratedServer("mariocraft-client-probe", "MarioCraft lifecycle probe", new WorldSettings(20261026L, GameType.SURVIVAL, true, false, WorldType.DEFAULT));
        } else if ((phase == 1 || phase == 3) && mc.player != null && mc.world != null &&
                (Boolean.getBoolean("mariocraft.gameplayProbe") ? GameplayProbe.complete : ++ticks == 120)) {
            log.info("MARIOCRAFT CLIENT_PROBE player-present phase={} dim={}", phase, mc.player.dimension);
            if(Boolean.getBoolean("mariocraft.gameplayProbe")) { phase=5; mc.shutdown(); return; }
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
            ticks = 0;
            phase=Boolean.getBoolean("mariocraft.gameplayProbe")?4:phase+1;
        } else if (phase == 2 && mc.currentScreen instanceof GuiMainMenu && !mc.isIntegratedServerRunning()) {
            log.info("MARIOCRAFT CLIENT_PROBE reloading same-world");
            phase = 3;
            mc.launchIntegratedServer("mariocraft-client-probe", "MarioCraft lifecycle probe", null);
        } else if (phase == 4 && !mc.isIntegratedServerRunning()) {
            log.info("MARIOCRAFT CLIENT_PROBE PASS create-save-reload-save");
            phase = 5;
            mc.shutdown();
        }
    }
}
