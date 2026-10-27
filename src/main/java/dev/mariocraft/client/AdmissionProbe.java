package dev.mariocraft.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private admission fixtures only. Uses three disposable ordinary saves, never edits a user's save. */
final class AdmissionProbe {
    static final AdmissionProbe INSTANCE=Boolean.getBoolean("mariocraft.admissionProbe")?new AdmissionProbe():null;
    private int cycle,phase;
    private long started,at;
    private volatile boolean fixture;
    private volatile String failure;
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private int targetDimension() { return cycle==1?-1:cycle==2?1:0; }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(!(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p=(EntityPlayerMP)e.player;
        try {
            p.getServer().setDifficultyForAllWorlds(cycle==0?EnumDifficulty.PEACEFUL:EnumDifficulty.NORMAL);
            p.world.getWorldInfo().setDifficultyLocked(true);
            p.connection.sendPacket(new net.minecraft.network.play.server.SPacketServerDifficulty(p.world.getDifficulty(),true));
            int dim=targetDimension();
            if(dim!=0) {
                WorldServer w=p.getServer().getWorld(dim);BlockPos spawn=w.getSpawnPoint();
                p.getServer().getPlayerList().transferPlayerToDimension(p,dim,new Teleporter(w) {
                    @Override public void placeInPortal(net.minecraft.entity.Entity ent,float yaw) { ent.setLocationAndAngles(spawn.getX()+.5,Math.max(70,spawn.getY()),spawn.getZ()+.5,0,0); }
                });
            }
            fixture=true;
        } catch(Exception ex) { failure="admission fixture "+ex.getClass().getSimpleName(); }
    }
    @SubscribeEvent public void tick(TickEvent.RenderTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();org.lwjgl.opengl.Display.setLocation(-20000,-20000);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;
        if(started==0) { started=at=System.nanoTime(); }
        if(System.nanoTime()-started>240000000000L) failure="admission bounded timeout phase "+phase;
        if(failure!=null) { finish(mc,false);return; }
        if(phase==0 && mc.currentScreen instanceof Presentation.Title && System.nanoTime()-at>1000000000L) {
            mc.gameSettings.difficulty=cycle==0?EnumDifficulty.PEACEFUL:EnumDifficulty.NORMAL;
            fixture=false;phase=1;at=System.nanoTime();
            mc.launchIntegratedServer("mariocraft-admission-"+System.currentTimeMillis(),"MarioCraft admission fixture",new WorldSettings(20261026L,GameType.SURVIVAL,true,false,WorldType.DEFAULT));
        } else if(phase==1 && fixture && mc.currentScreen instanceof Presentation.UnsupportedWorld && mc.isGamePaused()) {
            if(System.nanoTime()-at<1200000000L) return;
            checks.put("rejected"+cycle,true);
            final UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                if(p.dimension!=targetDimension() || p.world.getDifficulty()!=(cycle==0?EnumDifficulty.PEACEFUL:EnumDifficulty.NORMAL) || p.interactionManager.getGameType()!=GameType.SURVIVAL || dev.mariocraft.health.HealthBridge.OWNERS.containsKey(id)) failure="unsupported world mutated or native owner admitted";
            });
            phase=2;at=System.nanoTime();
        } else if(phase==2 && System.nanoTime()-at>800000000L) {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"mariocraft-admission-"+cycle+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            mc.world.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());phase=3;at=System.nanoTime();
        } else if(phase==3 && mc.world==null && !mc.isIntegratedServerRunning() && System.nanoTime()-at>1000000000L) {
            checks.put("preservedAndClosed"+cycle,dev.mariocraft.PlayerBridge.POSES.isEmpty()&&dev.mariocraft.persistence.RestoreBridge.HOLDS.isEmpty());
            if(cycle++<2) { phase=0;at=System.nanoTime(); } else finish(mc,true);
        }
    }
    private boolean finished;
    private void finish(Minecraft mc,boolean pass) {
        if(finished) return;finished=true;checks.put("passed",pass&&failure==null);if(failure!=null) checks.put("failure",failure);
        mc.gameSettings.difficulty=EnumDifficulty.NORMAL;mc.gameSettings.saveOptions();
        try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks).getBytes(StandardCharsets.UTF_8)); } catch(Exception ex) { throw new IllegalStateException(ex); }
        mc.shutdown();
    }
}
