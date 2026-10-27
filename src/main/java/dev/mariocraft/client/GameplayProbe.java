package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit test-only input seam. Runs inside a normal world without keyboard/focus automation. */
final class GameplayProbe {
    private final CombatProbe combat=Boolean.getBoolean("mariocraft.combatProbe")?new CombatProbe():null;
    private final CameraProbe cameraProbe=Boolean.getBoolean("mariocraft.cameraProbe")?new CameraProbe():null;
    void combatContact(dev.mariocraft.combat.MarioCombat.Attack attack,net.minecraft.entity.EntityLivingBase target,boolean accepted,float before,float after) { if(combat!=null) combat.contact(attack,target,accepted,before,after); }
    float initialYaw(float yaw) { return combat!=null?combat.yaw():cameraProbe!=null?cameraProbe.yaw():yaw; }
    boolean cameraProbe() { return cameraProbe!=null; }
    void cameraObserve(int frame,Sm64Library.State state,MarioCamera camera,Minecraft mc,Logger log) { cameraProbe.observe(frame,state,camera,mc,log); }
    private final TraversalProbe traversal=Boolean.getBoolean("mariocraft.traversalProbe")?new TraversalProbe():null;
    static volatile boolean complete;
    static volatile boolean syntheticStart;
    private boolean vanillaSuppression;
    private final AdvancedMovementProbe movement=new AdvancedMovementProbe();
    private ControlState movementInput;
    private String baselineResult;
    private volatile MovementSite site;
    private boolean findingSite;
    boolean prepare(Minecraft mc,Logger log) {
        if(combat!=null) return combat.prepare(mc,log);
        if(cameraProbe!=null) return cameraProbe.prepare(mc,log);
        if(traversal!=null) return traversal.prepare(mc,log);
        if(!findingSite) {
            findingSite=true;
            final java.util.UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    MovementSite selected=MovementSite.find(player.world,(int)player.posX,(int)player.posZ);
                    player.connection.setPlayerLocation(selected.x+.5,selected.y,selected.z+.5,0,0);
                    site=selected;
                    log.info("MARIOCRAFT MOVEMENT_PROBE natural-site setupOnly=true x={} y={} z={} runway=-8..28 width=5 terrainEdits=0",selected.x,selected.y,selected.z);
                } catch(RuntimeException error) { log.error("Movement site setup",error); fail("natural runway setup"); }
            });
        }
        return site!=null && mc.player.getDistanceSq(site.x+.5,site.y,site.z+.5)<1;
    }
    void setup(int frame,dev.mariocraft.nativebridge.MarioSession session,TerrainMesh terrain,Minecraft mc) {
        if(combat!=null) { combat.setup(frame,session,terrain,mc,org.apache.logging.log4j.LogManager.getLogger("MarioCraft"));return; }
        if(cameraProbe!=null) return;
        if(traversal!=null) { traversal.setup(frame,session,terrain,mc); return; }
        if(frame>=380 && (frame-380)%180==0) {
            // Setup relocation precedes 40 neutral frames; no action/velocity is written.
            mc.player.setPosition(site.x+.5,site.y,site.z+.5);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY)+10,terrain.z(mc.player.posZ));
        }
    }
    static volatile int currentFrame;
    static volatile boolean pauseVerified;
    static final AtomicInteger acceptedContacts=new AtomicInteger();
    static final AtomicInteger nativeAdmissions=new AtomicInteger();
    private double startX,startZ,jumpBase,peak;
    private float startYaw;
    private boolean airborne;
    private boolean landed;
    private boolean cameraTurned;
    private double cameraClearance;
    private volatile double serverPoseError=Double.POSITIVE_INFINITY;
    private volatile long serverTick=-1;
    private volatile int targetId=-1;
    private int firstAttackFrame=-1;
    private volatile int ordinaryMobTicks;
    static void fail(String reason) {
        writeResult("{\"passed\":false,\"reason\":\""+reason+"\"}");
        complete=true;
    }
    private static void writeResult(String result) {
        try {
            java.nio.file.Files.write(new java.io.File(Minecraft.getMinecraft().mcDataDir,"gameplay-result.json").toPath(),result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(java.io.IOException error) { throw new IllegalStateException("Could not save gameplay probe result",error); }
    }
    private final ControlState.Gate syntheticGate=new ControlState.Gate();
    private boolean controlsVerified;
    private int oldJumpCode;
    private boolean bindingPersistence;
    ControlState controls(int frame,MarioBindings bindings) {
        if(combat!=null) return combat.controls(frame,bindings);
        if(cameraProbe!=null) return cameraProbe.controls(frame,bindings);
        if(traversal!=null) return traversal.controls(frame,bindings);
        if(frame>=380) { movementInput=movement.controls(frame-380,bindings); return movementInput; }
        // Alternate keyboard and controller routes within the same actual native player session.
        boolean forward=frame>=30 && frame<120;
        boolean jump=frame>=90 && frame<96;
        boolean attack=firstAttackFrame>=0 && frame>=firstAttackFrame && frame<firstAttackFrame+48 && (frame-firstAttackFrame)%20<8;
        if(frame==0) {
            oldJumpCode=bindings.binding(6).getKeyCode();
            bindings.binding(6).setKeyCode(org.lwjgl.input.Keyboard.KEY_K);
            net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
            Minecraft.getMinecraft().gameSettings.saveOptions();
            Minecraft.getMinecraft().gameSettings.loadOptions();
            bindingPersistence=bindings.binding(6).getKeyCode()==org.lwjgl.input.Keyboard.KEY_K;
        }
        bindings.synthetic(forward,jump,attack);
        boolean walk=frame>=30 && frame<50, intermediate=frame>=50 && frame<75;
        net.minecraft.client.settings.KeyBinding.setKeyBindState(bindings.binding(4).getKeyCode(),walk);
        net.minecraft.client.settings.KeyBinding.setKeyBindState(bindings.binding(5).getKeyCode(),intermediate);
        net.minecraft.client.settings.KeyBinding.setKeyBindState(bindings.binding(10).getKeyCode(),frame>=180 && frame<195);
        ControlState keyboard=bindings.sample();
        if(frame==120) {
            bindings.binding(6).setKeyCode(oldJumpCode);
            net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
            Minecraft.getMinecraft().gameSettings.saveOptions();
        }
        float[] axes=new float[64]; boolean[] buttons=new boolean[128];
        axes[ControllerProfile.y]=(forward?(walk?-.4f:intermediate?-.7f:-1):0)*(ControllerProfile.invertY?-1:1);
        if(ControllerProfile.a>=0) buttons[ControllerProfile.a]=jump;
        if(ControllerProfile.b>=0) buttons[ControllerProfile.b]=attack;
        ControlState pad=ControllerInput.profile(axes,buttons);
        if(keyboard.x!=pad.x || keyboard.y!=pad.y || keyboard.a!=pad.a || keyboard.b!=pad.b) throw new IllegalStateException("Keyboard/controller mismatch");
        if(frame==0) {
            ControlState held=ControlState.keyboard(true,false,true,false,true,false,true,true,true);
            syntheticGate.filter(new ControlState(),true);
            boolean combinations=syntheticGate.filter(held,true).a && held.b && held.z && Math.abs(Math.hypot(held.x,held.y)-.4)<.00001;
            boolean lost=syntheticGate.filter(held,false).neutral();
            boolean blocked=syntheticGate.filter(held,true).neutral();
            syntheticGate.filter(new ControlState(),true);
            boolean recovered=syntheticGate.filter(held,true).a;
            ControlState released=ControlState.keyboard(true,false,false,false,false,false,false,false,false);
            controlsVerified=combinations && lost && blocked && recovered && released.y==-1;
        }
        if(frame>=180 && frame<195) return keyboard;
        return frame%2==0?keyboard:pad;
    }
    void observe(int frame,Sm64Library.State state,float yaw,Minecraft mc,Logger log) {
        currentFrame=frame;
        if(combat!=null) { combat.observe(frame,state,mc,log);return; }
        if(traversal!=null) { traversal.observe(frame,state,mc,log); return; }
        if(frame>380 && !movement.done(frame-381)) movement.observe(frame-381,state,movementInput);
        if(frame==2180) {
            try {
                boolean movementPassed=movement.passed();
                String original=baselineResult;
                if(!movementPassed) original=original.replace("\"passed\":true","\"passed\":false");
                writeResult(original.substring(0,original.length()-1)+",\"movementPassed\":"+movementPassed+",\"movementActions\":"+movement.summary()+"}");
                log.info("MARIOCRAFT MOVEMENT_PROBE actions={}",movement.summary());
                complete=true;
            } catch(RuntimeException error) { throw error; }
        }
        if(targetId>=0 && firstAttackFrame<0 && mc.world.getEntityByID(targetId)!=null) firstAttackFrame=frame+2;
        if(frame==30) { startX=mc.player.posX; startZ=mc.player.posZ; startYaw=yaw; }
        if(frame==90) { jumpBase=mc.player.posY; peak=jumpBase; }
        if(frame>=90 && frame<=160) { peak=Math.max(peak,mc.player.posY); airborne|=(state.action&0x800)!=0; }
        if(frame>=150 && frame<=200 && airborne && (state.action&(0x800|0x2000))==0) landed=true;
        if(frame==200) {
            vanillaSuppression=!mc.gameSettings.keyBindInventory.isActiveAndMatches(mc.gameSettings.keyBindInventory.getKeyCode()) && !mc.gameSettings.keyBindDrop.isActiveAndMatches(mc.gameSettings.keyBindDrop.getKeyCode()) && !mc.gameSettings.keyBindAttack.isActiveAndMatches(mc.gameSettings.keyBindAttack.getKeyCode()) && !mc.gameSettings.keyBindUseItem.isActiveAndMatches(mc.gameSettings.keyBindUseItem.getKeyCode()) && mc.mouseHelper instanceof MarioMouse;
        }
        if(frame==195) cameraTurned=Math.abs(yaw-startYaw)>20;
        if(frame==200) cameraClearance=BaselineCamera.clearance(mc,yaw,20,1);
        if(frame==238) {
            final java.util.UUID uuid=mc.player.getUniqueID();
            final float facing=state.faceAngle;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(uuid);
                if(player==null) return;
                EntityPig pig=new EntityPig(player.world);
                // Deterministic contact fixture only. Production mob AI is unchanged.
                pig.setNoAI(true);
                boolean clear=false;
                for(double distance:new double[]{0.6,0.3,0.02}) {
                    pig.setPosition(player.posX+Math.sin(facing)*distance,player.posY,player.posZ+Math.cos(facing)*distance);
                    if(player.world.getCollisionBoxes(pig,pig.getEntityBoundingBox()).isEmpty() && player.canEntityBeSeen(pig)) { clear=true; break; }
                }
                if(!clear) { fail("No unobstructed contact fixture placement"); return; }
                player.world.spawnEntity(pig);
                targetId=pig.getEntityId();
                log.info("MARIOCRAFT GAMEPLAY_PROBE spawned test-only NoAI pig id={}",targetId);
            });
        }
        if(frame==340) {
            final java.util.UUID uuid=mc.player.getUniqueID();
            final double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(uuid);
                if(player!=null) {
                    serverPoseError=Math.sqrt(player.getDistanceSq(x,y,z)); serverTick=player.world.getTotalWorldTime();
                    for(net.minecraft.entity.Entity entity:player.world.loadedEntityList) {
                        if(entity instanceof net.minecraft.entity.EntityLiving && !((net.minecraft.entity.EntityLiving)entity).isAIDisabled() && entity.ticksExisted>ordinaryMobTicks) {
                            ordinaryMobTicks=entity.ticksExisted;
                            log.info("MARIOCRAFT GAMEPLAY_PROBE ordinary-mob class={} ticksExisted={} aiEnabled=true",entity.getClass().getSimpleName(),ordinaryMobTicks);
                        }
                    }
                }
            });
        }
        if(frame==380) {
            double travelled=Math.hypot(mc.player.posX-startX,mc.player.posZ-startZ);
            boolean moved=travelled>1;
            boolean jumped=airborne && peak>jumpBase+0.3;
            boolean camera=cameraTurned && cameraClearance>=1.5;
            boolean passed=moved && jumped && landed && camera && acceptedContacts.get()>0 && serverPoseError<=0.05 && ordinaryMobTicks>20 && pauseVerified && controlsVerified && bindingPersistence && vanillaSuppression;
            log.info("MARIOCRAFT GAMEPLAY_PROBE result passed={} moved={} jump={} landed={} camera={} nativeAdmissions={} serverContacts={} travelled={} jumpRise={}",passed,moved,jumped,landed,camera,nativeAdmissions.get(),acceptedContacts.get(),travelled,peak-jumpBase);
            baselineResult="{\"passed\":"+passed+",\"moved\":"+moved+",\"jumped\":"+jumped+",\"landed\":"+landed+",\"cameraClearance\":"+cameraClearance+",\"cameraControl\":"+camera+",\"nativeAdmissions\":"+nativeAdmissions.get()+",\"serverContacts\":"+acceptedContacts.get()+",\"serverPoseError\":"+(Double.isFinite(serverPoseError)?serverPoseError:-1)+",\"serverTick\":"+serverTick+",\"ordinaryMobTicks\":"+ordinaryMobTicks+",\"poseSampleClientFrame\":340,\"pauseVerified\":"+pauseVerified+",\"vanillaSuppression\":"+vanillaSuppression+",\"bindingPersistence\":"+bindingPersistence+",\"controlsVerified\":"+controlsVerified+",\"keyboardControllerEquivalent\":true,\"controllerAdapter\":true,\"fixtureNoAI\":true,\"travelled\":"+travelled+",\"jumpRise\":"+(peak-jumpBase)+"}";
        }
    }
}
