package dev.mariocraft.client;

import dev.mariocraft.PlayerBridge;
import dev.mariocraft.nativebridge.*;
import dev.mariocraft.simulation.FixedStepClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.nio.file.Paths;
import java.util.*;

/** First integrated slice; camera remains a baseline pending mode-specific fidelity work. */
public final class MarioPlayer {
    private final Logger log;
    private final FixedStepClock clock=new FixedStepClock();
    private final ControllerInput controller=new ControllerInput();
    private double previousX,previousY,previousZ,currentX,currentY,currentZ;
    private boolean poseReady;
    private MarioSession session;
    private TerrainMesh terrain;
    private net.minecraft.client.multiplayer.WorldClient world;
    private ResourceLocation texture;
    private UUID playerId;
    private float yaw, pitch=20;
    private int frames;
    private boolean failed;
    private net.minecraft.client.entity.EntityPlayerSP controlledPlayer;
    private int previousPerspective;
    private boolean previousAutoJump;
    private float previousStep;
    private double meshX,meshY,meshZ;
    private io.netty.channel.Channel movementChannel;
    private final GameplayProbe probe=Boolean.getBoolean("mariocraft.gameplayProbe")?new GameplayProbe():null;
    private final Map<Integer,Integer> contacts=new HashMap<>();
    MarioPlayer(Logger log) { this.log=log; }
    private final MarioBindings bindings=new MarioBindings();
    private final ControlState.Gate keyboardGate=new ControlState.Gate();
    private net.minecraft.util.MouseHelper previousMouse;
    private boolean startHeld,gameplayControls;
    private final Map<net.minecraft.client.settings.KeyBinding,net.minecraftforge.client.settings.IKeyConflictContext> vanillaContexts=new HashMap<>();
    private void ownVanillaBindings(Minecraft mc) {
        net.minecraft.client.settings.GameSettings g=mc.gameSettings;
        net.minecraft.client.settings.KeyBinding[] keys={g.keyBindForward,g.keyBindBack,g.keyBindLeft,g.keyBindRight,g.keyBindJump,g.keyBindSneak,g.keyBindSprint,g.keyBindAttack,g.keyBindUseItem,g.keyBindPickBlock,g.keyBindDrop,g.keyBindInventory,g.keyBindSwapHands};
        for(net.minecraft.client.settings.KeyBinding key:keys) {
            final net.minecraftforge.client.settings.IKeyConflictContext original=key.getKeyConflictContext();
            vanillaContexts.put(key,original);
            key.setKeyConflictContext(new net.minecraftforge.client.settings.IKeyConflictContext() {
                public boolean isActive() { return mc.currentScreen!=null && original.isActive(); }
                public boolean conflicts(net.minecraftforge.client.settings.IKeyConflictContext other) { return original.conflicts(other); }
            });
        }
    }
    @SubscribeEvent public void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if(session!=null && event.getEntityPlayer()==Minecraft.getMinecraft().player && event.isCancelable()) event.setCanceled(true);
    }
    @SubscribeEvent public void attack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if(session!=null && event.getEntityPlayer()==Minecraft.getMinecraft().player) event.setCanceled(true);
    }
    @SubscribeEvent public void update(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world!=world) { close(); world=mc.world; failed=false; }
        if(controlledPlayer!=null && (mc.player!=controlledPlayer || !controlledPlayer.isEntityAlive())) { close(); failed=false; }
        if(mc.player==null || !mc.player.isEntityAlive() || mc.world==null || !mc.isSingleplayer() || failed) { clock.advance(System.nanoTime(),true); return; }
        if(session==null) {
            if(probe!=null && !probe.prepare(mc,log)) return;
            if(mc.player.ticksExisted<20 || !world.isBlockLoaded(new BlockPos(mc.player.posX,mc.player.posY,mc.player.posZ))) return;
            try {
                terrain=new TerrainMesh(mc.player.posX,mc.player.posY,mc.player.posZ);
                Sm64Library.Surface[] initialSurfaces=terrain.build(world,mc.player.posX,mc.player.posY,mc.player.posZ);
                if(initialSurfaces.length==0) return;
                session=new MarioSession(Paths.get(System.getProperty("mariocraft.native")),MarioSession.discoverRom());
                session.surfaces(initialSurfaces);
                session.create(terrain.x(mc.player.posX),terrain.y(mc.player.posY)+10,terrain.z(mc.player.posZ));
                playerId=mc.player.getUniqueID(); yaw=mc.player.rotationYaw;
                movementChannel=mc.getConnection().getNetworkManager().channel();
                movementChannel.pipeline().addBefore("packet_handler","mariocraft_movement",new MovementPacketGate(playerId));
                controlledPlayer=mc.player;
                previousMouse=mc.mouseHelper; mc.mouseHelper=new MarioMouse();
                ownVanillaBindings(mc);
                previousPerspective=mc.gameSettings.thirdPersonView;
                previousAutoJump=mc.gameSettings.autoJump;
                previousStep=mc.player.stepHeight;
                meshX=mc.player.posX; meshY=mc.player.posY; meshZ=mc.player.posZ;
                DynamicTexture image=new DynamicTexture(704,64);
                int[] pixels=image.getTextureData();
                for(int i=0;i<pixels.length;i++) {
                    int p=i*4;
                    pixels[i]=((session.texture[p+3]&255)<<24)|((session.texture[p]&255)<<16)|((session.texture[p+1]&255)<<8)|(session.texture[p+2]&255);
                }
                image.updateDynamicTexture();
                texture=mc.getTextureManager().getDynamicTextureLocation("mariocraft",image);
                mc.gameSettings.thirdPersonView=1;
                mc.gameSettings.autoJump=false;
                log.info("MARIOCRAFT native-player-created origin={},{},{}",terrain.ox,terrain.oy,terrain.oz);
            } catch(Exception|LinkageError error) { log.error("MARIOCRAFT native player initialization failed",error); close(); failed=true; if(probe!=null) GameplayProbe.fail("native initialization"); return; }
        }
        boolean gameplay=mc.currentScreen==null && mc.inGameHasFocus;
        if(!gameplay) keyboardGate.reset();
        if(gameplayControls && !gameplay) controller.suspend();
        gameplayControls=gameplay;
        // Start must be sampled even while the fixed-step clock is paused.
        boolean pauseScreen=mc.currentScreen instanceof net.minecraft.client.gui.GuiIngameMenu;
        ControlState pausePad=controller.sample(mc.inGameHasFocus || (pauseScreen && org.lwjgl.opengl.Display.isActive()),log);
        boolean start=(org.lwjgl.opengl.Display.isActive() && bindings.startDown()) || pausePad.start || (probe!=null && GameplayProbe.syntheticStart);
        if(start && !startHeld && (mc.currentScreen==null || pauseScreen)) {
            mc.displayGuiScreen(pauseScreen?null:new net.minecraft.client.gui.GuiIngameMenu());
        }
        startHeld=start;
        int count=clock.advance(System.nanoTime(),mc.isGamePaused());
        if(mc.isGamePaused()) { previousX=currentX; previousY=currentY; previousZ=currentZ; }
        try { for(int i=0;i<count;i++) step(mc); }
        catch(RuntimeException|LinkageError error) { log.error("MARIOCRAFT native step failed; releasing player control",error); close(); failed=true; if(probe!=null) GameplayProbe.fail("native step"); return; }
        // Keep a usable baseline when terrain would collapse vanilla's camera into the cap.
        if(!mc.isGamePaused() && BaselineCamera.clearance(mc,yaw,pitch,event.renderTickTime)<1.5) {
            float selected=yaw; double best=0;
            for(int offset=-180;offset<=180;offset+=30) {
                double distance=BaselineCamera.clearance(mc,yaw+offset,pitch,event.renderTickTime);
                if(distance>best) { best=distance; selected=yaw+offset; }
            }
            if(best>=1.5) yaw=selected;
        }
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
        mc.player.setVelocity(0,0,0);
    }
    private void step(Minecraft mc) {
        if(probe!=null) probe.setup(frames,session,terrain,mc);
        Sm64Library.Inputs input=new Sm64Library.Inputs();
        boolean controls=mc.currentScreen==null && mc.inGameHasFocus;
        ControlState keyboard=keyboardGate.filter(bindings.sample(),controls);
        ControlState pad=controller.sample(controls,log);
        ControlState state=pad.neutral()?keyboard:pad;
        if(probe!=null) state=probe.controls(frames,bindings);
        state.apply(input);
        if(state.left) yaw-=5; if(state.right) yaw+=5;
        if(state.up) pitch=Math.max(-60,pitch-3); if(state.down) pitch=Math.min(70,pitch+3);
        // Semantic shoulders are intentionally exposed; full camera mode behavior belongs to #23.
        if(state.l) yaw-=5; if(state.r) yaw+=5;
        input.camLookX=-(float)Math.sin(Math.toRadians(yaw));
        input.camLookZ=(float)Math.cos(Math.toRadians(yaw));
        if(probe!=null) { yaw=probe.camera(frames,yaw,mc,log); input.camLookX=-(float)Math.sin(Math.toRadians(yaw)); input.camLookZ=(float)Math.cos(Math.toRadians(yaw)); }
        if(Math.abs(terrain.x(mc.player.posX))>5000 || Math.abs(terrain.z(mc.player.posZ))>5000 || Math.abs(terrain.y(mc.player.posY))>5000) {
            poseReady=false;
            terrain=new TerrainMesh(mc.player.posX,mc.player.posY,mc.player.posZ);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY),terrain.z(mc.player.posZ));
            meshX=Double.POSITIVE_INFINITY;
            log.info("MARIOCRAFT rebased origin={},{},{}",terrain.ox,terrain.oy,terrain.oz);
        }
        if(frames++%10==0 || Math.abs(mc.player.posX-meshX)>=2 || Math.abs(mc.player.posY-meshY)>=2 || Math.abs(mc.player.posZ-meshZ)>=2) {
            session.surfaces(terrain.build(world,mc.player.posX,mc.player.posY,mc.player.posZ));
            meshX=mc.player.posX; meshY=mc.player.posY; meshZ=mc.player.posZ;
        }
        session.tick(input);
        double x=terrain.ox+session.state.position[0]/100.0;
        double y=terrain.oy+session.state.position[1]/100.0;
        double z=terrain.oz+session.state.position[2]/100.0;
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalStateException("Nonfinite native player pose");
        // ACT_FLAG_AIR / ACT_FLAG_SWIMMING distinguish an apex from a grounded action.
        PlayerBridge.Pose pose=new PlayerBridge.Pose(x,y,z,(session.state.action & (0x800|0x2000))==0);
        previousX=currentX; previousY=currentY; previousZ=currentZ;
        currentX=x; currentY=y; currentZ=z;
        if(!poseReady) { previousX=x; previousY=y; previousZ=z; poseReady=true; }
        PlayerBridge.POSES.put(playerId,pose);
        mc.player.setPosition(x,y,z);
        if(probe!=null) probe.observe(frames,session.state,yaw,mc,log);
        if(frames%30==0) log.info("MARIOCRAFT player-step frame={} action={} pos={},{},{}",frames,Integer.toHexString(session.state.action),x,y,z);
        // First crossover path: nearby attackable living mobs. Native classifier decides action admission.
        for(EntityLivingBase mob:world.getEntitiesWithinAABB(EntityLivingBase.class,mc.player.getEntityBoundingBox().grow(0.35))) {
            if(mob==mc.player || !mob.isEntityAlive() || contacts.getOrDefault(mob.getEntityId(),-100)>frames-20) continue;
            Vec3d target=new Vec3d(mob.posX,mob.posY+mob.height*0.5,mob.posZ);
            boolean blocked=world.rayTraceBlocks(mc.player.getPositionEyes(1),target,false,true,false)!=null;
            if(probe!=null && frames>=225 && frames<=280 && frames%5==0) log.info("MARIOCRAFT GAMEPLAY_PROBE candidate frame={} blocked={} action={} flags={} target={},{},{}",frames,blocked,Integer.toHexString(session.state.action),Integer.toHexString(session.state.flags),mob.posX,mob.posY,mob.posZ);
            if(blocked) continue;
            if(session.attack(terrain.x(mob.posX),terrain.y(mob.posY),terrain.z(mob.posZ),mob.height*100)) {
                GameplayProbe.nativeAdmissions.incrementAndGet();
                contacts.put(mob.getEntityId(),frames);
                final int id=mob.getEntityId();
                final UUID uuid=playerId;
                mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(uuid);
                    if(player==null) return;
                    net.minecraft.entity.Entity entity=player.world.getEntityByID(id);
                    if(entity instanceof EntityLivingBase && entity.getDistanceSq(player)<9 && player.canEntityBeSeen(entity)) {
                        float before=((EntityLivingBase)entity).getHealth();
                        boolean accepted=entity.attackEntityFrom(DamageSource.causePlayerDamage(player),4);
                        if(accepted) GameplayProbe.acceptedContacts.incrementAndGet();
                        log.info("MARIOCRAFT mob-contact target={} accepted={} damage=4 healthBefore={} healthAfter={}",id,accepted,before,((EntityLivingBase)entity).getHealth());
                    }
                });
            }
        }
    }
    @SubscribeEvent public void hide(RenderPlayerEvent.Pre event) {
        if(session!=null && event.getEntityPlayer().getUniqueID().equals(playerId)) event.setCanceled(true);
    }
    @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if(event.getWorld().isRemote && event.getWorld()==world) { close(); world=null; }
    }
    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        if(session!=null) { event.setYaw(yaw+180); event.setPitch(pitch); }
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if(session==null) return;
        Minecraft mc=Minecraft.getMinecraft();
        net.minecraft.entity.Entity view=mc.getRenderViewEntity();
        double px=view.lastTickPosX+(view.posX-view.lastTickPosX)*event.getPartialTicks();
        double py=view.lastTickPosY+(view.posY-view.lastTickPosY)*event.getPartialTicks();
        double pz=view.lastTickPosZ+(view.posZ-view.lastTickPosZ)*event.getPartialTicks();
        int vertices=Short.toUnsignedInt(session.geometry.numTrianglesUsed)*3;
        int previousTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        // Interpolate translation only: native animation may change mesh topology between steps.
        float alpha=clock.renderFraction(mc.isGamePaused());
        double dx=(previousX-currentX)*(1-alpha),dy=(previousY-currentY)*(1-alpha),dz=(previousZ-currentZ)*(1-alpha);
        GlStateManager.translate(terrain.ox-px+dx,terrain.oy-py+dy,terrain.oz-pz+dz);
        GlStateManager.scale(0.01,0.01,0.01);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // libsm64's atlas is an alpha overlay. Its untextured triangles use vertex colors;
        // multiplying those colors by the atlas's transparent black texels loses the body.
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for(int i=0;i<vertices;i++) {
            GL11.glColor4f(session.geometry.color.getFloat(i*12L),session.geometry.color.getFloat(i*12L+4),session.geometry.color.getFloat(i*12L+8),1);
            GL11.glTexCoord2f(session.geometry.uv.getFloat(i*8L),session.geometry.uv.getFloat(i*8L+4));
            GL11.glVertex3f(session.geometry.position.getFloat(i*12L),session.geometry.position.getFloat(i*12L+4),session.geometry.position.getFloat(i*12L+8));
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        mc.getTextureManager().bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glColor4f(1,1,1,1);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for(int i=0;i<vertices;i++) {
            GL11.glTexCoord2f(session.geometry.uv.getFloat(i*8L),session.geometry.uv.getFloat(i*8L+4));
            GL11.glVertex3f(session.geometry.position.getFloat(i*12L),session.geometry.position.getFloat(i*12L+4),session.geometry.position.getFloat(i*12L+8));
        }
        GL11.glEnd();
        GlStateManager.popMatrix();
        GL11.glPopAttrib();
        GlStateManager.bindTexture(previousTexture);
        GlStateManager.color(1,1,1,1);
    }
    private void close() {
        if(playerId!=null) PlayerBridge.POSES.remove(playerId);
        if(movementChannel!=null) {
            if(movementChannel.pipeline().get("mariocraft_movement")!=null) movementChannel.pipeline().remove("mariocraft_movement");
            movementChannel=null;
        }
        if(session!=null) { session.close(); session=null; log.info("MARIOCRAFT native-player-closed"); }
        if(texture!=null) { Minecraft.getMinecraft().getTextureManager().deleteTexture(texture); texture=null; }
        if(controlledPlayer!=null) {
            controlledPlayer.stepHeight=previousStep;
            Minecraft.getMinecraft().gameSettings.thirdPersonView=previousPerspective;
            Minecraft.getMinecraft().gameSettings.autoJump=previousAutoJump;
            controlledPlayer=null;
        }
        if(previousMouse!=null) { Minecraft.getMinecraft().mouseHelper=previousMouse; previousMouse=null; }
        for(Map.Entry<net.minecraft.client.settings.KeyBinding,net.minecraftforge.client.settings.IKeyConflictContext> entry:vanillaContexts.entrySet()) entry.getKey().setKeyConflictContext(entry.getValue());
        vanillaContexts.clear();
        keyboardGate.reset(); controller.close(); startHeld=false; gameplayControls=false;
        playerId=null; contacts.clear(); frames=0; poseReady=false; clock.advance(System.nanoTime(),true);
    }
}
