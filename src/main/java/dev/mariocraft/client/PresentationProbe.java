package dev.mariocraft.client;

import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.*;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded opt-in integrated checks; generated normal saves and private captures only. */
final class PresentationProbe {
    static final PresentationProbe INSTANCE=Boolean.getBoolean("mariocraft.presentationProbe")?new PresentationProbe():null;
    private final String save="mariocraft-presentation-"+System.currentTimeMillis();
    private final Map<String,Object> checks=Collections.synchronizedMap(new LinkedHashMap<>());
    private long deadline,phaseAt;
    private int phase,cycle,frame,closed;
    private boolean started,fixtureRequested,pauseSampled,gameCaptured;
    private volatile boolean fixtureReady;
    private volatile String failure;
    private volatile EntityItem item;
    private volatile EntityXPOrb orb;
    private volatile EntityPig pig;
    private volatile MovementSite site;
    private boolean siteSearching;
    private MarioPlayer owner;
    private MarioSession session;
    private MarioAudio audio;
    private int renders,startRenders,oldLimit;
    private long sampleAt;
    private final Map<Integer,double[]> timingReference=new HashMap<>();
    private double startX,startZ;
    private float musicVolume,playerVolume,masterVolume;
    private int pauseFrame;
    private String admission="waiting for world";
    void admission(String stage) { admission=stage; }
    private long pauseTick;
    private long packetStart;
    private net.minecraft.client.audio.ISound music;
    private org.lwjgl.openal.ALCcontext oldContext;
    private final java.util.List<MarioAudio> closedAudio=new java.util.ArrayList<>();
    boolean prepare(Minecraft mc) {
        if(site!=null && mc.player.getDistanceSq(site.x+.5,site.y+.1,site.z+.5)<1) return true;
        if(!siteSearching) {
            siteSearching=true;final UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()-> {
                try { EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    if(site==null) site=MovementSite.find(p.world,(int)p.posX,(int)p.posZ);
                    p.connection.setPlayerLocation(site.x+.5,site.y+.1,site.z+.5,0,0);
                    p.getServer().getPlayerList().serverUpdateMovingPlayer(p);
                } catch(Exception ex) { failure="natural movement site "+ex.getClass().getSimpleName(); }
            });
        }
        return false;
    }
    private void check(String key,boolean pass) { checks.put(key,pass);if(!pass && failure==null) failure=key; }
    ControlState controls() {
        ControlState c=new ControlState();c.a=frame>=45 && frame<49 || frame>=105 && frame<109;
        c.y=frame>=70 && frame<90?-1:0;return c;
    }
    void step(MarioPlayer p,MarioSession s,MarioAudio a) {
        owner=p;session=s;audio=a;frame++;
        if(frame==60) { sampleAt=System.nanoTime();startRenders=renders; }
        if(frame==160) {
            double seconds=(System.nanoTime()-sampleAt)/1e9;
            checks.put("displayCap"+cycle,cycle==0?30:144);checks.put("achievedRenderHz"+cycle,(renders-startRenders)/seconds);checks.put("achievedNativeHz"+cycle,100/seconds);
            check("nativeTiming"+cycle,100/seconds>=26 && 100/seconds<=34);
        }
        if(frame==60 || frame==100 || frame==130 || frame==160) {
            double[] value={s.state.action,s.state.velocity[1],s.state.forwardVelocity,s.state.position[1]};
            if(cycle==0) timingReference.put(frame,value);
            else { double[] ref=timingReference.get(frame);boolean same=ref!=null;for(int i=0;same && i<value.length;i++) same &= Math.abs(value[i]-ref[i])<=.001f;check("matchedNativeFrame"+frame,same); }
        }
    }
    void closed(MarioAudio a) { closedAudio.add(a);closed++; }
    @SubscribeEvent public void tick(TickEvent.RenderTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();renders++;if(deadline!=0) mc.gameSettings.limitFramerate=cycle==0?30:144;
        org.lwjgl.opengl.Display.setLocation(-20000,-20000);
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;
        if(deadline==0) { oldLimit=mc.gameSettings.limitFramerate;deadline=System.nanoTime()+360000000000L;phaseAt=System.nanoTime(); }
        if(System.nanoTime()>deadline) failure="bounded presentation timeout phase "+phase;
        if(phase==1 && mc.world!=null && frame==0 && System.nanoTime()-phaseAt>45000000000L) failure="native admission timeout: "+admission;
        if(failure!=null) { finish(mc,false);return; }
        if(phase==0 && mc.currentScreen instanceof Presentation.Title) {
            if(System.nanoTime()-phaseAt<1000000000L) return;
            check("title",true);
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"mariocraft-title.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            net.minecraft.network.NetworkManager remote=new net.minecraft.network.NetworkManager(net.minecraft.network.EnumPacketDirection.CLIENTBOUND);
            io.netty.channel.embedded.EmbeddedChannel channel=new io.netty.channel.embedded.EmbeddedChannel(remote);
            new Presentation().connection(new net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent(remote,"VANILLA"));
            check("nonlocalConnectionRejected",!channel.isOpen());channel.finishAndReleaseAll();
            GuiScreen title=mc.currentScreen;
            mc.displayGuiScreen(new GuiMultiplayer(title));check("multiplayerBlocked",mc.currentScreen==title);
            mc.displayGuiScreen(new GuiOptions(title,mc.gameSettings));check("settings",mc.currentScreen instanceof GuiOptions);
            mc.displayGuiScreen(new GuiScreenOptionsSounds(title,mc.gameSettings));check("audioSettings",mc.currentScreen instanceof GuiScreenOptionsSounds);
            mc.displayGuiScreen(new GuiControls(title,mc.gameSettings));check("keyboardSettings",mc.currentScreen instanceof GuiControls);
            mc.displayGuiScreen(new ControlsGuiFactory().createConfigGui(title));check("controllerSettings",mc.currentScreen instanceof net.minecraftforge.fml.client.config.GuiConfig);
            ControllerProfile.config.get("presentation","marioSounds",true).set(false);ControllerProfile.config.save();ControllerProfile.config.load();ControllerProfile.reload();check("settingsPersistOff",!ControllerProfile.marioSounds);
            ControllerProfile.config.get("presentation","marioSounds",true).set(true);ControllerProfile.config.save();ControllerProfile.config.load();ControllerProfile.reload();check("settingsPersistOn",ControllerProfile.marioSounds);
            mc.displayGuiScreen(new GuiWorldSelection(title));check("worldSelection",mc.currentScreen instanceof GuiWorldSelection);
            mc.gameSettings.difficulty=EnumDifficulty.HARD;mc.displayGuiScreen(new GuiCreateWorld(title));check("newWorldNormal",mc.gameSettings.difficulty==EnumDifficulty.NORMAL);
            mc.displayGuiScreen(title);
            musicVolume=mc.gameSettings.getSoundLevel(SoundCategory.MUSIC);playerVolume=mc.gameSettings.getSoundLevel(SoundCategory.PLAYERS);masterVolume=mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
            mc.getSoundHandler().addListener((sound,accessor)-> { if(sound.getCategory()==SoundCategory.MUSIC) music=sound; });
            mc.gameSettings.setSoundLevel(SoundCategory.PLAYERS,1);mc.gameSettings.setSoundLevel(SoundCategory.MASTER,1);
            phase=1;phaseAt=System.nanoTime();mc.launchIntegratedServer(save,"MarioCraft presentation probe",new WorldSettings(20261026L,GameType.SURVIVAL,true,false,WorldType.DEFAULT));return;
        }
        if(phase==1 && owner!=null && owner.persistenceOwnership()) {
            if(frame>=165 && !gameCaptured) {
                gameCaptured=true;check("tutorialDisabled",mc.gameSettings.tutorialStep==net.minecraft.client.tutorial.TutorialSteps.NONE);
                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"mariocraft-final-hud.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                if(Boolean.getBoolean("mariocraft.hudProbe")) { mc.displayGuiScreen(new GuiIngameMenu());phase=6;phaseAt=System.nanoTime();return; }
            }
            mc.setIngameFocus();
            if(!fixtureRequested) {
                fixtureRequested=true;startX=mc.player.posX;startZ=mc.player.posZ;
                final UUID id=mc.player.getUniqueID();
                mc.getIntegratedServer().addScheduledTask(()->fixtures(mc,id));
                packetStart=MovementPacketGate.denied.get();
                net.minecraft.network.Packet<?>[] packets={new net.minecraft.network.play.client.CPacketClickWindow(),new net.minecraft.network.play.client.CPacketCreativeInventoryAction(),new net.minecraft.network.play.client.CPacketHeldItemChange(4),new net.minecraft.network.play.client.CPacketEnchantItem(),new net.minecraft.network.play.client.CPacketRecipeInfo(true,false),new net.minecraft.network.play.client.CPacketPlaceRecipe(),new net.minecraft.network.play.client.CPacketPlayerDigging(),new net.minecraft.network.play.client.CPacketUseEntity(),new net.minecraft.network.play.client.CPacketPlayerTryUseItem(),new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock()};
                for(net.minecraft.network.Packet<?> packet:packets) mc.getConnection().sendPacket(packet);
                mc.getMusicTicker().playMusic(net.minecraft.client.audio.MusicTicker.MusicType.GAME);
            }
            if(frame>180 && fixtureReady) {
                check("nativeVoice"+cycle,session.voiceEvents>0);check("nativeAction"+cycle,session.actionEvents>0);
                check("pcm"+cycle,audio.nonzero>0 && audio.peak>0);checks.put("pcmPeak"+cycle,audio.peak);checks.put("voiceEvents"+cycle,session.voiceEvents);checks.put("actionEvents"+cycle,session.actionEvents);
                check("managedPlayback"+cycle,!audio.silent());
                check("musicVolumeRetained"+cycle,mc.gameSettings.getSoundLevel(SoundCategory.MUSIC)==musicVolume);
                check("minecraftMusic"+cycle,music!=null && (musicVolume==0 || mc.getSoundHandler().isSoundPlaying(music)));
                check("alternatePackets"+cycle,MovementPacketGate.denied.get()-packetStart>=10);
                check("movement"+cycle,Math.hypot(mc.player.posX-startX,mc.player.posZ-startZ)>.1);
                GuiScreen before=mc.currentScreen;
                mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.player));check("inventoryBlocked"+cycle,mc.currentScreen==before);
                mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiContainerCreative(mc.player));check("creativeInventoryBlocked"+cycle,mc.currentScreen==before);
                mc.displayGuiScreen(new GuiShareToLan(new GuiIngameMenu()));check("lanBlocked"+cycle,mc.currentScreen==before);
                mc.displayGuiScreen(new GuiIngameMenu());phase=2;phaseAt=System.nanoTime();pauseSampled=false;
            }
        } else if(phase==2) {
            if(!mc.isGamePaused()) return;
            if(!pauseSampled) { pauseSampled=true;phaseAt=System.nanoTime();pauseFrame=frame;pauseTick=mc.world.getTotalWorldTime();return; }
            if(System.nanoTime()-phaseAt<700000000L) return;
            check("pause"+cycle,mc.isGamePaused() && frame==pauseFrame && mc.world.getTotalWorldTime()==pauseTick);
            check("pauseFlush"+cycle,audio.silent());
            mc.gameSettings.setSoundLevel(SoundCategory.PLAYERS,0);mc.displayGuiScreen(null);mc.setIngameFocus();phase=3;phaseAt=System.nanoTime();
        } else if(phase==3 && System.nanoTime()-phaseAt>200000000L) {
            check("playersMute"+cycle,audio.silent());mc.gameSettings.setSoundLevel(SoundCategory.PLAYERS,1);
            mc.gameSettings.setSoundLevel(SoundCategory.MASTER,0);phase=4;phaseAt=System.nanoTime();
        } else if(phase==4 && System.nanoTime()-phaseAt>200000000L) {
            check("masterMute"+cycle,audio.silent());check("masterGain"+cycle,audio.masterVolume()==0);mc.gameSettings.setSoundLevel(SoundCategory.MASTER,1);
            mc.displayGuiScreen(null);mc.inGameHasFocus=false;phase=5;phaseAt=System.nanoTime();
        } else if(phase==5 && System.nanoTime()-phaseAt>300000000L) {
            check("focusFlush"+cycle,audio.silent());mc.setIngameFocus();
            if(cycle==0) { oldContext=org.lwjgl.openal.AL.getContext();mc.getSoundHandler().onResourceManagerReload(mc.getResourceManager());phase=8;phaseAt=System.nanoTime();return; }
            finalChecks(mc);
        } else if(phase==8 && System.nanoTime()-phaseAt>1000000000L && org.lwjgl.openal.AL.isCreated() && org.lwjgl.openal.AL.getContext()!=oldContext && audio.creates>=2) {
            check("audioContextRecreated",true);checks.put("audioSourceCreates",audio.creates);finalChecks(mc);
        } else if(phase==6 && mc.isGamePaused() && System.nanoTime()-phaseAt>700000000L) {
            mc.world.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());phase=7;phaseAt=System.nanoTime();
        } else if(phase==7 && mc.world==null && !mc.isIntegratedServerRunning()) {
            check("cleanup"+cycle,owner.persistenceClosed() && dev.mariocraft.PlayerBridge.POSES.isEmpty() && dev.mariocraft.health.HealthBridge.OWNERS.isEmpty());
            check("audioClosed"+cycle,closedAudio.stream().allMatch(MarioAudio::silent));
            if(Boolean.getBoolean("mariocraft.hudProbe")) { finish(mc,true);return; }
            if(cycle++==0) { frame=0;fixtureRequested=fixtureReady=false;siteSearching=false;phase=1;phaseAt=System.nanoTime();mc.launchIntegratedServer(save,"MarioCraft presentation probe",null); }
            else { check("twoAudioCloses",closed==2);finish(mc,true); }
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true)
    public void overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre e) {
        if(e.getType()==net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CROSSHAIRS && phase==1 && owner!=null && owner.persistenceOwnership()) check("crosshairHidden",e.isCanceled());
    }
    private void finalChecks(Minecraft mc) {
            mc.displayGuiScreen(new GuiIngameMenu());phase=6;phaseAt=System.nanoTime();
            mc.getIntegratedServer().addScheduledTask(()-> {
                try { EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    check("dropsRemain"+cycle,item!=null&&!item.isDead&&orb!=null&&!orb.isDead);
                    check("mobSimulation"+cycle,pig!=null&&pig.ticksExisted>10);
                    check("inventoryPreserved"+cycle,p.inventory.getStackInSlot(0).getItem()==Items.DIAMOND && p.inventory.armorInventory.get(0).getItem()==Items.DIAMOND_BOOTS);
                } catch(Exception ex) { failure="fixture final "+ex.getClass().getSimpleName(); }
            });
    }
    private void fixtures(Minecraft mc,UUID id) {
        try {
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
            for(net.minecraft.entity.Entity old:new ArrayList<net.minecraft.entity.Entity>(p.world.loadedEntityList)) if(old.getTags().contains("mariocraft-presentation-fixture")) old.setDead();
            BlockPos floor=new BlockPos(p.posX,p.posY-1,p.posZ);
            net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem use=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(p,EnumHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(use);check("useBlocked"+cycle,use.isCanceled());
            net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock place=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,EnumHand.MAIN_HAND,floor,EnumFacing.UP,new Vec3d(p.posX,p.posY,p.posZ));MinecraftForge.EVENT_BUS.post(place);check("placeBlocked"+cycle,place.isCanceled());
            net.minecraftforge.event.world.BlockEvent.BreakEvent mine=new net.minecraftforge.event.world.BlockEvent.BreakEvent(p.world,floor,p.world.getBlockState(floor),p);MinecraftForge.EVENT_BUS.post(mine);check("mineBlocked"+cycle,mine.isCanceled());
            item=new EntityItem(p.world,p.posX,p.posY,p.posZ,new ItemStack(Items.EMERALD));item.addTag("mariocraft-presentation-fixture");item.setNoPickupDelay();p.world.spawnEntity(item);item.onCollideWithPlayer(p);check("itemPickupBlocked"+cycle,!item.isDead && item.getItem().getCount()==1);
            orb=new EntityXPOrb(p.world,p.posX,p.posY,p.posZ,7);orb.addTag("mariocraft-presentation-fixture");p.world.spawnEntity(orb);int xp=p.experienceTotal;orb.onCollideWithPlayer(p);check("xpPickupBlocked"+cycle,!orb.isDead&&p.experienceTotal==xp);
            p.inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND));p.inventory.armorInventory.set(0,new ItemStack(Items.DIAMOND_BOOTS));
            int dimension=p.dimension;p.changeDimension(-1);check("portalBlocked"+cycle,p.dimension==dimension);
            p.getServer().getCommandManager().executeCommand(p,"gamemode creative");check("commandModeBlocked"+cycle,p.interactionManager.getGameType()==GameType.SURVIVAL);
            net.minecraftforge.event.CommandEvent command=new net.minecraftforge.event.CommandEvent(new net.minecraft.command.CommandGameMode(),p,new String[]{"creative"});MinecraftForge.EVENT_BUS.post(command);check("commandGuard"+cycle,command.isCanceled());
            pig=new EntityPig(p.world);pig.setPosition(p.posX+3,p.posY,p.posZ+3);p.world.spawnEntity(pig);
            fixtureReady=true;
        } catch(Exception ex) { failure="fixture "+ex.getClass().getSimpleName(); }
    }
    private void finish(Minecraft mc,boolean pass) {
        if(started) return;started=true;checks.put("passed",pass&&failure==null);if(failure!=null) checks.put("failure",failure);
        mc.gameSettings.limitFramerate=oldLimit;mc.gameSettings.setSoundLevel(SoundCategory.MUSIC,musicVolume);mc.gameSettings.setSoundLevel(SoundCategory.PLAYERS,playerVolume);mc.gameSettings.setSoundLevel(SoundCategory.MASTER,masterVolume);mc.gameSettings.saveOptions();
        try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks).getBytes(StandardCharsets.UTF_8)); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
        mc.shutdown();
    }
}
