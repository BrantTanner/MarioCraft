package dev.mariocraft.client;

import dev.mariocraft.health.*;
import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.*;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opt-in real-world damage recipes. Relocation/health resets are explicitly test setup. */
final class HealthProbe {
    private static final String[] RECIPES={"zombie-ai-repeat","skeleton-ai-projectile","explosion","fall-small","fall-medium","fall-large","lethal-explosion"};
    private static final int LENGTH=300,TOTAL=LENGTH*7;
    private volatile MovementSite site;
    private boolean finding;
    private volatile UUID target;
    private volatile int phase;
    private final int[] minHealth={0x880,0x880,0x880,0x880,0x880,0x880,0x880};
    private final int[] maxTimer=new int[7],receipts=new int[7];
    private final boolean[] hurtAction=new boolean[7],air=new boolean[7],landed=new boolean[7];
    private volatile boolean zombieTarget,skeletonTarget,arrowSeen,vanillaHealth=true;
    private volatile int zombieTicks,skeletonTicks;
    private volatile double serverMotion;
    private int receivedBase;
    private ControlState input;
    boolean prepare(Minecraft mc,Logger log) {
        if(!finding) {
            finding=true;UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    MovementSite selected=MovementSite.find(p.world,(int)p.posX,(int)p.posZ);
                    p.world.setWorldTime(13000);p.world.getWorldInfo().setDifficulty(net.minecraft.world.EnumDifficulty.NORMAL);
                    p.connection.setPlayerLocation(selected.x+.5,selected.y,selected.z+.5,180,0);site=selected;
                    log.info("MARIOCRAFT HEALTH natural-site x={} y={} z={} terrainEdits=0",site.x,site.y,site.z);
                } catch(RuntimeException e) { log.error("Health setup",e);GameplayProbe.fail("health site setup"); }
            });
        }
        return site!=null && mc.player.getDistanceSq(site.x+.5,site.y,site.z+.5)<1;
    }
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc,Logger log) {
        if(frame>=TOTAL) return;
        int p=frame/LENGTH,t=frame%LENGTH;
        HealthBridge.Channel channel=HealthBridge.OWNERS.get(mc.player.getUniqueID());
        if(t==0) {
            phase=p;target=null;receivedBase=channel.received.get();
            session.resetHealthForProbe();
            if(p==6) session.configureHealthForProbe(0x100); // explicit last-section edge-of-death setup
            mc.player.setPosition(site.x+.5,site.y,site.z+.5);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY)+10,terrain.z(mc.player.posZ));
            UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                for(Entity e:new ArrayList<Entity>(player.world.loadedEntityList)) if(e.getTags().contains("mariocraft-health-fixture")) e.setDead();
                // Armor and hunger are deliberately varied to prove they cannot own Mario damage.
                player.setItemStackToSlot(EntityEquipmentSlot.HEAD,new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND_HELMET));
                player.getFoodStats().setFoodLevel(p%2==0?0:20);
            });
            log.info("MARIOCRAFT HEALTH recipe={} testOnlyHealthReset=true setupRelocation=true",RECIPES[p]);
        }
        if(t==30 && p<2) {
            UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                EntityMob mob=p==0?new EntityZombie(player.world):new EntitySkeleton(player.world);
                mob.setPosition(site.x+.5,site.y,site.z+(p==0?2:5));
                mob.onInitialSpawn(player.world.getDifficultyForLocation(new BlockPos(mob)),null);
                if(mob instanceof EntityZombie) ((EntityZombie)mob).setChild(false);
                mob.addTag("mariocraft-health-fixture");mob.getRNG().setSeed(25+p);player.world.spawnEntity(mob);target=mob.getUniqueID();
            });
        }
        if(t==60 && (p==2 || p==6)) {
            UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                // Real vanilla explosion, explicitly suppressing terrain edits in this fixture.
                player.world.newExplosion(null,player.posX+2,player.posY+.5,player.posZ,p==2?2:10,false,false);
            });
        }
        if(t==44 && p>=3 && p<=5) {
            double rise=p==3?2:p==4?12:31;
            if((session.state.action&0x800)==0) throw new IllegalStateException("Fall setup requires real A-input airborne action");
            mc.player.setPosition(mc.player.posX,mc.player.posY+rise,mc.player.posZ);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY),terrain.z(mc.player.posZ));
        }
    }
    ControlState controls(int frame,MarioBindings bindings) {
        int p=Math.min(frame/LENGTH,6),t=frame%LENGTH;
        for(int i=0;i<16;i++) KeyBinding.setKeyBindState(bindings.binding(i).getKeyCode(),false);
        KeyBinding.setKeyBindState(bindings.binding(6).getKeyCode(),p>=3 && p<=5 && t>=40 && t<44);
        KeyBinding.setKeyBindState(bindings.binding(0).getKeyCode(),p==3 && t>=60 && t<70);
        input=bindings.sample();return input;
    }
    void observe(int frame,Sm64Library.State state,Minecraft mc,Logger log) {
        GameplayProbe.currentFrame=frame;
        int p=Math.min((frame-1)/LENGTH,6),t=(frame-1)%LENGTH;
        HealthBridge.Channel channel=HealthBridge.OWNERS.get(mc.player.getUniqueID());
        if(t>=30) minHealth[p]=Math.min(minHealth[p],Short.toUnsignedInt(state.health));
        maxTimer[p]=Math.max(maxTimer[p],state.invincTimer);hurtAction[p]|=(state.action&0x20000)!=0;
        air[p]|=(state.action&0x800)!=0;
        if(t>80) landed[p]|=air[p] && (state.action&(0x800|0x2000))==0 && Math.abs(mc.player.posY-site.y)<.02;
        receipts[p]=channel.received.get()-receivedBase;
        String row="{\"frame\":"+frame+",\"recipe\":\""+RECIPES[p]+"\",\"health\":"+Short.toUnsignedInt(state.health)+",\"sections\":"+MarioHealth.sections(Short.toUnsignedInt(state.health))+",\"action\":\""+Integer.toHexString(state.action)+"\",\"invinc\":"+state.invincTimer+",\"x\":"+mc.player.posX+",\"y\":"+mc.player.posY+",\"z\":"+mc.player.posZ+",\"vy\":"+state.velocity[1]+",\"a\":"+input.a+",\"stickY\":"+input.y+",\"receipts\":"+receipts[p]+"}\n";
        write(mc,"health-trace.jsonl",row,frame==1);
        if(p==6 && Short.toUnsignedInt(state.health)<0x100) { finish(mc,log,frame);return; }
        if(frame%5==0) {
            UUID id=mc.player.getUniqueID(),mobId=target;int observedPhase=phase;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                if(player==null) return;
                vanillaHealth &= player.getHealth()==player.getMaxHealth();
                serverMotion=Math.max(serverMotion,Math.abs(player.motionX)+Math.abs(player.motionY)+Math.abs(player.motionZ));
                Entity entity=mobId==null?null:((net.minecraft.world.WorldServer)player.world).getEntityFromUuid(mobId);
                if(entity instanceof EntityMob && observedPhase==phase) {
                    EntityMob mob=(EntityMob)entity;
                    if(observedPhase==0) { zombieTarget|=mob.getAttackTarget()==player;zombieTicks=mob.ticksExisted; }
                    if(observedPhase==1) { skeletonTarget|=mob.getAttackTarget()==player;skeletonTicks=mob.ticksExisted; }
                    // Stop this recipe before a further ordinary AI hit could kill Mario.
                    if(channel.snapshot!=null && channel.snapshot.health<=0x480) mob.setDead();
                }
                for(Entity e:player.world.loadedEntityList) if(e instanceof EntityArrow && ((EntityArrow)e).shootingEntity!=null && ((EntityArrow)e).shootingEntity.getUniqueID().equals(mobId)) arrowSeen=true;
                if(frame==TOTAL) finish(mc,log,frame);
            });
        }
    }
    private void finish(Minecraft mc,Logger log,int terminalFrame) {
        boolean passed=minHealth[0]<0x880 && receipts[0]>=2 && maxTimer[0]>0 && zombieTarget && zombieTicks>20
            && minHealth[1]<0x880 && skeletonTarget && skeletonTicks>20 && arrowSeen && minHealth[2]<0x880
            && minHealth[3]==0x880 && minHealth[4]==0x680 && minHealth[5]==0x480
            && landed[3] && landed[4] && landed[5] && minHealth[6]==0xFF && vanillaHealth;
        String result="{\"passed\":"+passed+",\"health\":true,\"frames\":"+terminalFrame+",\"minimumNativeHealth\":"+Arrays.toString(minHealth)+",\"maximumInvincibility\":"+Arrays.toString(maxTimer)+",\"serverDamageReceipts\":"+Arrays.toString(receipts)+",\"nativeHurtActions\":"+Arrays.toString(hurtAction)+",\"landed\":"+Arrays.toString(landed)+",\"zombieTargetsRealPlayer\":"+zombieTarget+",\"skeletonTargetsRealPlayer\":"+skeletonTarget+",\"skeletonArrow\":"+arrowSeen+",\"zombieTicks\":"+zombieTicks+",\"skeletonTicks\":"+skeletonTicks+",\"vanillaHealthUnchanged\":"+vanillaHealth+",\"maximumObservedServerMotion\":"+serverMotion+",\"terrainEdits\":0,\"testOnlyHealthResets\":7,\"nativeDeathEndpoint\":true,\"respawnClaimed\":false}";
        write(mc,"gameplay-result.json",result,true);log.info("MARIOCRAFT HEALTH result={}",result);GameplayProbe.complete=true;
    }
    private static void write(Minecraft mc,String name,String text,boolean replace) {
        try { Files.write(new java.io.File(mc.mcDataDir,name).toPath(),text.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,replace?StandardOpenOption.TRUNCATE_EXISTING:StandardOpenOption.APPEND); }
        catch(java.io.IOException e) { throw new IllegalStateException("Health evidence write",e); }
    }
}
