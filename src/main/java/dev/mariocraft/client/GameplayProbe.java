package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit test-only input seam. Runs inside a normal world without keyboard/focus automation. */
final class GameplayProbe {
    static volatile boolean complete;
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
    void input(int frame,Sm64Library.Inputs input) {
        // Synthetic device state enters the same mapping as physical LWJGL samples.
        ControllerInput.map(input,0,frame>=30 && frame<120?-1:0,
            frame>=90 && frame<96,
            firstAttackFrame>=0 && frame>=firstAttackFrame && frame<firstAttackFrame+48 && (frame-firstAttackFrame)%20<8,false);
    }
    float camera(int frame,float yaw,Minecraft mc,Logger log) {
        if(frame>=180 && frame<195) return yaw+3;
        if(frame==195) {
            cameraTurned=Math.abs(yaw-startYaw)>20;
            // A private visual QA view, separate from gameplay camera acceptance.
            // Select an unobstructed angle in the existing terrain; do not alter the world.
            double best=-1;
            float selected=yaw;
            for(int angle=0;angle<360;angle+=30) {
                double distance=BaselineCamera.clearance(mc,angle,20,1);
                if(distance>best) { best=distance; selected=angle; }
            }
            log.info("MARIOCRAFT GAMEPLAY_PROBE visual-QA-view yaw={} clearance={}",selected,best);
            return selected;
        }
        return yaw;
    }
    void observe(int frame,Sm64Library.State state,float yaw,Minecraft mc,Logger log) {
        currentFrame=frame;
        if(targetId>=0 && firstAttackFrame<0 && mc.world.getEntityByID(targetId)!=null) firstAttackFrame=frame+2;
        if(frame==30) { startX=mc.player.posX; startZ=mc.player.posZ; startYaw=yaw; }
        if(frame==90) { jumpBase=mc.player.posY; peak=jumpBase; }
        if(frame>=90 && frame<=160) { peak=Math.max(peak,mc.player.posY); airborne|=(state.action&0x800)!=0; }
        if(frame>=150 && frame<=200 && airborne && (state.action&(0x800|0x2000))==0) landed=true;
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
            boolean passed=moved && jumped && landed && camera && acceptedContacts.get()>0 && serverPoseError<=0.05 && ordinaryMobTicks>20 && pauseVerified;
            log.info("MARIOCRAFT GAMEPLAY_PROBE result passed={} moved={} jump={} landed={} camera={} nativeAdmissions={} serverContacts={} travelled={} jumpRise={}",passed,moved,jumped,landed,camera,nativeAdmissions.get(),acceptedContacts.get(),travelled,peak-jumpBase);
            writeResult("{\"passed\":"+passed+",\"moved\":"+moved+",\"jumped\":"+jumped+",\"landed\":"+landed+",\"cameraClearance\":"+cameraClearance+",\"cameraControl\":"+camera+",\"nativeAdmissions\":"+nativeAdmissions.get()+",\"serverContacts\":"+acceptedContacts.get()+",\"serverPoseError\":"+(Double.isFinite(serverPoseError)?serverPoseError:-1)+",\"serverTick\":"+serverTick+",\"ordinaryMobTicks\":"+ordinaryMobTicks+",\"poseSampleClientFrame\":340,\"pauseVerified\":"+pauseVerified+",\"controllerAdapter\":true,\"fixtureNoAI\":true,\"travelled\":"+travelled+",\"jumpRise\":"+(peak-jumpBase)+"}");
            complete=true;
        }
    }
}
