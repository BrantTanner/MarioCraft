package dev.mariocraft.client;

import dev.mariocraft.PlayerBridge;
import dev.mariocraft.combat.MarioCombat;
import dev.mariocraft.health.*;
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

/** Owns native Mario simulation and the separate client camera lifecycle. */
public final class MarioPlayer {
    private final Logger log;
    private final FixedStepClock clock=new FixedStepClock();
    private final ControllerInput controller=new ControllerInput();
    private double previousX,previousY,previousZ,currentX,currentY,currentZ;
    private boolean poseReady;
    private MarioSession session;
    private TerrainMesh terrain;
    private final TerrainChanges terrainChanges=new TerrainChanges();
    private net.minecraft.client.multiplayer.WorldClient world;
    private ResourceLocation texture;
    private UUID playerId;
    private float yaw;
    private final MarioCamera cameraModel=new MarioCamera();
    private CameraEntity cameraEntity;
    private net.minecraft.entity.Entity previousView;
    private VoxelCamera cameraCollision;
    private int frames;
    private boolean failed;
    private net.minecraft.client.entity.EntityPlayerSP controlledPlayer;
    private int previousPerspective;
    private boolean previousAutoJump,previousDebugCamera;
    private float previousStep;
    private double meshX,meshY,meshZ;
    private long loadedMask;
    private Sm64Library.Surface[] submittedSurfaces;
    private boolean collisionFrozen;
    private io.netty.channel.Channel movementChannel;
    private final GameplayProbe probe=Boolean.getBoolean("mariocraft.gameplayProbe")?new GameplayProbe():null;
    private final MarioCombat.Ledger contacts=new MarioCombat.Ledger();
    private volatile Object combatSession;
    private HealthBridge.Channel healthChannel;
    private long healthSequence;
    private String lastRequestedExternalSource;
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
                terrainChanges.region(mc.player.posX,mc.player.posY,mc.player.posZ);
                world.addEventListener(terrainChanges);
                session=new MarioSession(Paths.get(System.getProperty("mariocraft.native")),MarioSession.discoverRom());
                session.surfaces(initialSurfaces); submittedSurfaces=initialSurfaces;
                session.create(terrain.x(mc.player.posX),terrain.y(mc.player.posY)+10,terrain.z(mc.player.posZ));
                combatSession=new Object();
                playerId=mc.player.getUniqueID(); yaw=probe==null?mc.player.rotationYaw:probe.initialYaw(mc.player.rotationYaw);
                healthChannel=new HealthBridge.Channel(mc.getIntegratedServer().getWorld(world.provider.getDimension()),mc.getIntegratedServer(),world);
                HealthBridge.OWNERS.put(playerId,healthChannel);
                cameraModel.reset(mc.player.posX,mc.player.posY,mc.player.posZ,yaw);
                cameraCollision=new VoxelCamera(world);
                previousView=mc.getRenderViewEntity(); cameraEntity=new CameraEntity(world);
                movementChannel=mc.getConnection().getNetworkManager().channel();
                movementChannel.pipeline().addBefore("packet_handler","mariocraft_movement",new MovementPacketGate(playerId));
                controlledPlayer=mc.player;
                previousMouse=mc.mouseHelper; mc.mouseHelper=new MarioMouse();
                ownVanillaBindings(mc);
                previousPerspective=mc.gameSettings.thirdPersonView;
                previousAutoJump=mc.gameSettings.autoJump;
                previousDebugCamera=mc.gameSettings.debugCamEnable;
                mc.gameSettings.debugCamEnable=false;
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
                mc.gameSettings.thirdPersonView=0;
                mc.setRenderViewEntity(cameraEntity);
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
        if(mc.isGamePaused()) { previousX=currentX; previousY=currentY; previousZ=currentZ; cameraModel.pause(); }
        try { for(int i=0;i<count;i++) step(mc); }
        catch(RuntimeException|LinkageError error) { log.error("MARIOCRAFT native step failed; releasing player control",error); close(); failed=true; if(probe!=null) GameplayProbe.fail("native step"); return; }
        MarioCamera.View view=cameraModel.view(clock.renderFraction(mc.isGamePaused()),cameraCollision);
        cameraEntity.place(view);
        mc.setRenderViewEntity(cameraEntity); mc.gameSettings.thirdPersonView=0;
        // Real player orientation is the control basis, independent of the rendered view.
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=0;
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
        cameraModel.controls(state,session.state.action);
        yaw=cameraModel.controlYaw;
        if(cameraModel.inspecting()) { input.stickX=0;input.stickY=0; }
        input.camLookX=-(float)Math.sin(Math.toRadians(yaw));
        input.camLookZ=(float)Math.cos(Math.toRadians(yaw));
        if(Math.abs(terrain.x(mc.player.posX))>5000 || Math.abs(terrain.z(mc.player.posZ))>5000 || Math.abs(terrain.y(mc.player.posY))>5000) {
            poseReady=false;
            terrain=new TerrainMesh(mc.player.posX,mc.player.posY,mc.player.posZ);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY),terrain.z(mc.player.posZ));
            meshX=Double.POSITIVE_INFINITY;
            log.info("MARIOCRAFT rebased origin={},{},{}",terrain.ox,terrain.oy,terrain.oz);
        }
        long nextLoadedMask=TerrainMesh.loadedMask(world,mc.player.posX,mc.player.posZ);
        if(nextLoadedMask!=loadedMask || terrainChanges.dirty || Math.abs(mc.player.posX-meshX)>=2 || Math.abs(mc.player.posY-meshY)>=2 || Math.abs(mc.player.posZ-meshZ)>=2) {
            long meshStarted=System.nanoTime();
            Sm64Library.Surface[] updated=terrain.build(world,mc.player.posX,mc.player.posY,mc.player.posZ);
            float[] recovered=terrain.recovery(mc.player.posX,mc.player.posY,mc.player.posZ);
            collisionFrozen=recovered==null;
            if(recovered!=null && (Math.abs(recovered[0]-terrain.x(mc.player.posX))>.01 || Math.abs(recovered[1]-terrain.y(mc.player.posY))>.01 || Math.abs(recovered[2]-terrain.z(mc.player.posZ))>.01)) {
                session.position(recovered[0],recovered[1],recovered[2]);
                mc.player.setPosition(terrain.ox+recovered[0]/100.0,terrain.oy+recovered[1]/100.0,terrain.oz+recovered[2]/100.0);
                poseReady=false;
                log.info("MARIOCRAFT collision update depenetration");
            }
            session.surfaces(updated); submittedSurfaces=updated;
            if(probe!=null) log.info("MARIOCRAFT collision-mesh frame={} triangles={} milliseconds={}",frames,updated.length,(System.nanoTime()-meshStarted)/1000000.0);
            loadedMask=nextLoadedMask;
            terrainChanges.region(mc.player.posX,mc.player.posY,mc.player.posZ);
            meshX=mc.player.posX; meshY=mc.player.posY; meshZ=mc.player.posZ;
            terrainChanges.dirty=false;
        }
        if(collisionFrozen) return;
        frames++;
        Sm64Library.Surface[] visible=terrain.at(mc.player.posX,mc.player.posY,mc.player.posZ);
        if(visible!=submittedSurfaces) { session.surfaces(visible); submittedSurfaces=visible; }
        // Resolve descending feet before the native floor step can erase the airborne action.
        // Sweep only one native velocity step, and only toward a top contact; side attacks stay post-step.
        if(poseReady && session.state.velocity[1]<0 && (session.state.action&0x800)!=0) {
            double projectedY=currentY+Math.max(-.8,session.state.velocity[1]/100.0);
            MarioCombat.Sample falling=new MarioCombat.Sample(session.state.action,session.state.flags,session.state.animID,session.state.animFrame,
                currentX,projectedY,currentZ,currentY,session.state.faceAngle,session.state.velocity[1],session.state.forwardVelocity);
            resolveCombat(mc,falling,true);
        }
        HealthBridge.Hit hit=healthChannel.poll();
        if(hit!=null) {
            boolean fresh=mc.getIntegratedServer()==healthChannel.server && mc.getIntegratedServer().getWorld(world.provider.getDimension())==healthChannel.serverWorld
                && world.getTotalWorldTime()-hit.tick>=-5 && world.getTotalWorldTime()-hit.tick<=20;
            boolean admitted=fresh && healthChannel.active && poseReady && MarioHealth.vulnerable(Short.toUnsignedInt(session.state.health),session.state.action,session.state.invincTimer);
            if(admitted) { session.damage(hit.damage,terrain.x(hit.x),terrain.y(hit.y),terrain.z(hit.z));lastRequestedExternalSource=hit.kind; }
            log.info("MARIOCRAFT incoming-hit frame={} source={} immediate={} attacker={} damage={} admitted={} health={} action={} invinc={} minecraftDamage={} bodyHeight={} bodyWidth={}",frames,hit.kind,hit.immediateSource,hit.trueSource,hit.damage,admitted,Short.toUnsignedInt(session.state.health),Integer.toHexString(session.state.action),session.state.invincTimer,hit.minecraftDamage,hit.bodyHeight,hit.bodyWidth);
        }
        session.tick(input);
        MarioHealth.Snapshot health=new MarioHealth.Snapshot(++healthSequence,Short.toUnsignedInt(session.state.health),session.state.action,session.state.invincTimer,healthChannel.snapshot,lastRequestedExternalSource);
        healthChannel.publish(health);
        double x=terrain.ox+session.state.position[0]/100.0;
        double y=terrain.oy+session.state.position[1]/100.0;
        double z=terrain.oz+session.state.position[2]/100.0;
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalStateException("Nonfinite native player pose");
        // ACT_FLAG_AIR / ACT_FLAG_SWIMMING distinguish an apex from a grounded action.
        PlayerBridge.Pose pose=new PlayerBridge.Pose(x,y,z,(session.state.action & (0x800|0x2000))==0,(session.state.action&0x8000)!=0?1:1.6);
        previousX=currentX; previousY=currentY; previousZ=currentZ;
        currentX=x; currentY=y; currentZ=z;
        if(!poseReady) { previousX=x; previousY=y; previousZ=z; poseReady=true; }
        PlayerBridge.POSES.put(playerId,pose);
        mc.player.setPosition(x,y,z);
        PlayerBridge.body(mc.player,pose);
        cameraModel.follow(x,y,z,session.state.faceAngle,session.state.forwardVelocity,cameraCollision);
        if(probe!=null) {
            if(probe.cameraProbe()) probe.cameraObserve(frames,session.state,cameraModel,mc,log);
            else probe.observe(frames,session.state,yaw,mc,log);
        }
        if(frames%30==0) log.info("MARIOCRAFT player-step frame={} action={} pos={},{},{}",frames,Integer.toHexString(session.state.action),x,y,z);
        MarioCombat.Sample sample=new MarioCombat.Sample(session.state.action,session.state.flags,session.state.animID,session.state.animFrame,
            x,y,z,previousY,session.state.faceAngle,session.state.velocity[1],session.state.forwardVelocity);
        resolveCombat(mc,sample,false);
    }
    private void resolveCombat(Minecraft mc,MarioCombat.Sample sample,boolean topOnly) {
        double x=sample.x,y=sample.y,z=sample.z;
        long epoch=contacts.advance(sample);
        for(EntityLivingBase mob:world.getEntitiesWithinAABB(EntityLivingBase.class,new AxisAlignedBB(x-.37,y-.25,z-.37,x+.37,Math.max(y+sample.height(),sample.previousY),z+.37))) {
            if(mob==mc.player || mob instanceof net.minecraft.entity.player.EntityPlayer || !mob.isEntityAlive() || contacts.contains(mob.getUniqueID())) continue;
            MarioCombat.Target target=combatTarget(mob);
            MarioCombat.Attack attack=MarioCombat.classify(sample,target);
            if(attack==null || topOnly && attack!=MarioCombat.Attack.STOMP && attack!=MarioCombat.Attack.GROUND_POUND || !clearContact(world,sample,target)) continue;
            // The native helper applies recoil/bounce. All local admission gates precede it.
            if(!session.attack(terrain.x(mob.posX),terrain.y(mob.posY),terrain.z(mob.posZ),mob.height*100)) continue;
            contacts.consume(mob.getUniqueID());
            GameplayProbe.nativeAdmissions.incrementAndGet();
            if(probe!=null) log.info("MARIOCRAFT combat-admitted frame={} epoch={} attack={} x={} y={} z={} targetX={} targetY={} targetZ={}",frames,epoch,attack,sample.x,sample.y,sample.z,target.x,target.y,target.z);
            final net.minecraft.server.integrated.IntegratedServer server=mc.getIntegratedServer();
            final net.minecraft.world.WorldServer serverWorld=server==null?null:server.getWorld(world.provider.getDimension());
            final Object token=combatSession;
            final UUID uuid=playerId,targetId=mob.getUniqueID();
            final PlayerBridge.Pose activePose=new PlayerBridge.Pose(sample.x,sample.y,sample.z,false);
            if(server==null || serverWorld==null) continue;
            server.addScheduledTask(()->{
                if(combatSession!=token || !server.isServerRunning() || server.getWorld(serverWorld.provider.getDimension())!=serverWorld || !PlayerBridge.POSES.containsKey(uuid)) return;
                EntityPlayerMP player=server.getPlayerList().getPlayerByUUID(uuid);
                if(player==null || !player.isEntityAlive() || player.world!=serverWorld || player.getDistanceSq(activePose.x,activePose.y,activePose.z)>9) return;
                net.minecraft.entity.Entity entity=serverWorld.getEntityFromUuid(targetId);
                if(!(entity instanceof EntityLivingBase) || entity instanceof net.minecraft.entity.player.EntityPlayer || !entity.isEntityAlive()) return;
                EntityLivingBase living=(EntityLivingBase)entity;
                MarioCombat.Target actual=combatTarget(living);
                if(MarioCombat.classify(sample,actual)!=attack || !clearContact(serverWorld,sample,actual)) { if(probe!=null) log.info("MARIOCRAFT combat-server-rejected attack={} targetX={} targetY={} targetZ={}",attack,actual.x,actual.y,actual.z);return; }
                float before=living.getHealth();
                // Direct attributed living damage, without the vanilla inventory/weapon click route.
                boolean accepted=living.attackEntityFrom(new net.minecraft.util.EntityDamageSource("mario."+attack.name().toLowerCase(java.util.Locale.ROOT),player),attack.damage);
                if(accepted) GameplayProbe.acceptedContacts.incrementAndGet();
                log.info("MARIOCRAFT mob-contact target={} uuid={} attack={} accepted={} damage={} healthBefore={} healthAfter={} aiDisabled={}",entity.getEntityId(),targetId,attack,accepted,attack.damage,before,living.getHealth(),living instanceof net.minecraft.entity.EntityLiving && ((net.minecraft.entity.EntityLiving)living).isAIDisabled());
                if(probe!=null) probe.combatContact(attack,living,accepted,before,living.getHealth());
            });
        }
    }
    private static MarioCombat.Target combatTarget(EntityLivingBase mob) {
        AxisAlignedBB box=mob.getEntityBoundingBox();
        return new MarioCombat.Target((box.minX+box.maxX)/2,box.minY,(box.minZ+box.maxZ)/2,Math.max(box.maxX-box.minX,box.maxZ-box.minZ)/2,box.maxY-box.minY);
    }
    private static boolean clearContact(net.minecraft.world.World world,MarioCombat.Sample sample,MarioCombat.Target target) {
        // A narrow contact corridor uses real collision boxes, including partial blocks.
        // Top attacks originate at the feet; side attacks at the shared body height.
        MarioCombat.Attack attack=MarioCombat.classify(sample,target);
        boolean top=attack==MarioCombat.Attack.STOMP || attack==MarioCombat.Attack.GROUND_POUND;
        double height=top?Math.max(sample.previousY,sample.y):Math.max(sample.y+.1,Math.min(sample.y+.8,target.y+target.height*.5));
        Vec3d from=new Vec3d(sample.x,height,sample.z),to=new Vec3d(target.x,Math.min(height,target.y+target.height-.01),target.z);
        AxisAlignedBB corridor=new AxisAlignedBB(from.x,from.y,from.z,to.x,to.y,to.z).grow(.03);
        if(!world.isAreaLoaded(new BlockPos(corridor.minX,corridor.minY,corridor.minZ),new BlockPos(corridor.maxX,corridor.maxY,corridor.maxZ))) return false;
        return CombatGeometry.clear(world.getCollisionBoxes(null,corridor),from,to);
    }

    @SubscribeEvent public void hide(RenderPlayerEvent.Pre event) {
        if(session!=null && event.getEntityPlayer().getUniqueID().equals(playerId)) event.setCanceled(true);
    }
    @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if(event.getWorld().isRemote && event.getWorld()==world) { close(); world=null; }
    }
    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        // orientCamera applies a 0.05 first-person offset before this event; cancel it.
        if(session!=null) GlStateManager.translate(0,0,-.05f);
    }
    @SubscribeEvent public void hand(RenderHandEvent event) { if(session!=null) event.setCanceled(true); }
    @SubscribeEvent public void vanillaHud(RenderGameOverlayEvent.Pre event) {
        if(session==null) return;
        switch(event.getType()) {
            case HEALTH: case ARMOR: case FOOD: case AIR: event.setCanceled(true);break;
            default: break;
        }
    }
    @SubscribeEvent public void healthHud(RenderGameOverlayEvent.Post event) {
        if(session==null || healthChannel.snapshot==null || event.getType()!=RenderGameOverlayEvent.ElementType.ALL) return;
        MarioHealth.Snapshot state=healthChannel.snapshot;
        int x=event.getResolution().getScaledWidth()/2-48,y=12;
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(state.dead?"POWER - fallen":"POWER",x,y,0xFFFFFF);
        for(int i=0;i<8;i++) {
            net.minecraft.client.gui.Gui.drawRect(x+i*12,y+12,x+i*12+10,y+23,0xFF202020);
            net.minecraft.client.gui.Gui.drawRect(x+i*12+1,y+13,x+i*12+9,y+22,i<state.sections?(state.sections<=2?0xFFE04438:0xFF39B968):0xFF555555);
        }
        GlStateManager.color(1,1,1,1);
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if(session==null || cameraModel.inspecting()) return;
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
        combatSession=null;
        if(healthChannel!=null) { healthChannel.clear();if(playerId!=null) HealthBridge.OWNERS.remove(playerId,healthChannel);healthChannel=null; }
        healthSequence=0;lastRequestedExternalSource=null;
        if(world!=null) world.removeEventListener(terrainChanges);
        terrainChanges.dirty=true; collisionFrozen=false;
        if(playerId!=null) PlayerBridge.POSES.remove(playerId);
        if(movementChannel!=null) {
            if(movementChannel.pipeline().get("mariocraft_movement")!=null) movementChannel.pipeline().remove("mariocraft_movement");
            movementChannel=null;
        }
        if(session!=null) { session.close(); session=null; log.info("MARIOCRAFT native-player-closed"); }
        if(texture!=null) { Minecraft.getMinecraft().getTextureManager().deleteTexture(texture); texture=null; }
        if(cameraEntity!=null) {
            Minecraft mc=Minecraft.getMinecraft();
            if(mc.getRenderViewEntity()==cameraEntity) mc.setRenderViewEntity(previousView==null?mc.player:previousView);
            cameraEntity=null;previousView=null;cameraCollision=null;
        }
        if(controlledPlayer!=null) {
            controlledPlayer.stepHeight=previousStep;
            controlledPlayer.setPosition(controlledPlayer.posX,controlledPlayer.posY,controlledPlayer.posZ);
            Minecraft.getMinecraft().gameSettings.thirdPersonView=previousPerspective;
            Minecraft.getMinecraft().gameSettings.autoJump=previousAutoJump;
            Minecraft.getMinecraft().gameSettings.debugCamEnable=previousDebugCamera;
            controlledPlayer=null;
        }
        if(previousMouse!=null) { Minecraft.getMinecraft().mouseHelper=previousMouse; previousMouse=null; }
        for(Map.Entry<net.minecraft.client.settings.KeyBinding,net.minecraftforge.client.settings.IKeyConflictContext> entry:vanillaContexts.entrySet()) entry.getKey().setKeyConflictContext(entry.getValue());
        vanillaContexts.clear();
        keyboardGate.reset(); controller.close(); startHeld=false; gameplayControls=false;
        playerId=null; contacts.clear(); frames=0; poseReady=false; clock.advance(System.nanoTime(),true);
    }
}
