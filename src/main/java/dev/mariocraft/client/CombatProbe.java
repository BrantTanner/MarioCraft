package dev.mariocraft.client;

import dev.mariocraft.combat.MarioCombat;
import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.*;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual bindings and ordinary generated terrain. No action or velocity writes. */
final class CombatProbe {
    private static final String[] RECIPES={"punch-fixture","air-kick-fixture","stomp-fixture","dive-fixture","ground-pound-fixture","ground-zombie-ai","airborne-bat-ai","projectile-skeleton-ai"};
    private static final int LENGTH=180,TOTAL=1440;
    private volatile MovementSite site;
    private boolean finding;
    private volatile UUID targetId;
    private volatile int phase;
    private ControlState submitted;
    private final Map<String,Integer> hits=new ConcurrentHashMap<>();
    private final Map<String,Integer> attempts=new ConcurrentHashMap<>();
    private volatile boolean zombieTarget,skeletonTarget,projectile,batMoved,death,drops,ai=true,zombiePath,skeletonPath,zombieAttack;
    private volatile int zombieTicks,batTicks,skeletonTicks;
    private volatile double zombieTravel,batTravel,skeletonTravel;
    private volatile double spawnX,spawnY,spawnZ;
    private double x,y,z;
    private int spawnAt=-1;
    boolean prepare(Minecraft mc,Logger log) {
        if(!finding) {
            finding=true;UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    MovementSite selected=MovementSite.find(player.world,(int)player.posX,(int)player.posZ);
                    player.world.setWorldTime(13000);player.world.getWorldInfo().setDifficulty(net.minecraft.world.EnumDifficulty.NORMAL);
                    player.connection.setPlayerLocation(selected.x+.5,selected.y,selected.z+.5,0,0);player.getServer().getPlayerList().serverUpdateMovingPlayer(player);site=selected;
                    log.info("MARIOCRAFT COMBAT natural-site x={} y={} z={} terrainEdits=0",site.x,site.y,site.z);
                } catch(RuntimeException e) { log.error("Combat site",e);GameplayProbe.fail("combat site setup"); }
            });
        }
        return site!=null && mc.player.getDistanceSq(site.x+.5,site.y,site.z+.5)<1;
    }
    float yaw() { return 180; } // native camera look -Z makes forward input travel +Z
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc,Logger log) {
        if(frame>=TOTAL) return;
        int t=frame%LENGTH,p=Math.min(frame/LENGTH,7);
        if(t==0 && frame<1440) {
            session.resetHealthForProbe(); // native-health test setup; no production healing
            phase=p;spawnAt=-1;targetId=null;
            mc.player.setPosition(site.x+.5,site.y,site.z+.5);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY)+10,terrain.z(mc.player.posZ));
            UUID playerId=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(playerId);
                if(player==null) return;
                player.setHealth(player.getMaxHealth());player.getFoodStats().setFoodLevel(20);
                for(Entity e:new ArrayList<Entity>(player.world.loadedEntityList)) if(e.getTags().contains("mariocraft-combat-fixture")) e.setDead();
            });
        }
        // Dive needs a running approach. Spawn before the B edge, on unedited runway.
        int spawnStep=p==3?78:p==6?108:p>=5?38:20;
        if(t==spawnStep && spawnAt!=frame && frame<1440) {
            spawnAt=frame;
            double distance=p==3?3:p==2 || p==4?0:p==6?.75:.5;
            x=mc.player.posX+Math.sin(session.state.faceAngle)*distance;y=site.y+(p==6?.5:0);z=mc.player.posZ+Math.cos(session.state.faceAngle)*distance;
            final double px=x,py=y,pz=z;
            UUID playerId=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(playerId);
                if(player==null) return;
                EntityLiving mob=p==1 || p==7?new EntitySkeleton(player.world):p==2?new EntityChicken(player.world):p==5?new EntityZombie(player.world):p==6?new EntityBat(player.world):new EntityPig(player.world);
                mob.setPosition(px,py,pz);
                if(!player.world.getCollisionBoxes(mob,mob.getEntityBoundingBox()).isEmpty()) { GameplayProbe.fail("combat target obstructed setup");return; }
                mob.getRNG().setSeed(24+p);
                if(p>=5) mob.onInitialSpawn(player.world.getDifficultyForLocation(new BlockPos(mob)),null);
                if(mob instanceof EntityZombie) ((EntityZombie)mob).setChild(false);
                if(mob instanceof EntityBat) ((EntityBat)mob).setIsBatHanging(false);
                mob.setNoAI(p<5);mob.addTag("mariocraft-combat-fixture");
                // Stable test seed; unchanged ordinary AI consumes its normal RNG.
                mob.getRNG().setSeed(24+p);
                player.world.spawnEntity(mob);targetId=mob.getUniqueID();spawnX=px;spawnY=py;spawnZ=pz;
                log.info("MARIOCRAFT COMBAT spawn recipe={} class={} noAI={}",RECIPES[p],mob.getClass().getSimpleName(),mob.isAIDisabled());
            });
        }
    }
    ControlState controls(int frame,MarioBindings bindings) {
        int p=Math.min(frame/LENGTH,7),t=frame%LENGTH;
        if(frame>=1440) t+=180;
        for(int i=0;i<16;i++) KeyBinding.setKeyBindState(bindings.binding(i).getKeyCode(),false);
        boolean forward=p==3 && t>=40 && t<110;
        boolean a=(p==1 || p==2 || p==4) && t>=40 && t<48;
        boolean b=p==0 && t>=40 && t<44 || p==1 && t>=43 && t<47 || p==3 && t>=85 && t<89 || p>=5 && p!=6 && t>=40 && t<44 || p==6 && t>=110 && t<170 && t%20<14 && t%20>=10 && !hits.containsKey("airborne-bat-ai:PUNCH");
        boolean crouch=p==4 && t>=49 && t<52;
        KeyBinding.setKeyBindState(bindings.binding(0).getKeyCode(),forward);
        KeyBinding.setKeyBindState(bindings.binding(1).getKeyCode(),p==5 && t>=85 && t<100);
        KeyBinding.setKeyBindState(bindings.binding(6).getKeyCode(),a);
        KeyBinding.setKeyBindState(bindings.binding(7).getKeyCode(),b);
        KeyBinding.setKeyBindState(bindings.binding(8).getKeyCode(),crouch);
        submitted=bindings.sample();return submitted;
    }
    void contact(MarioCombat.Attack attack,EntityLivingBase target,boolean accepted,float before,float after) {
        String key=RECIPES[phase]+":"+attack.name();
        attempts.merge(key,1,Integer::sum);
        if(accepted && after<before) hits.merge(key,1,Integer::sum);
        if(after<=0) death=true;
    }
    void observe(int frame,Sm64Library.State s,Minecraft mc,Logger log) {
        GameplayProbe.currentFrame=frame;
        if(phase>=5 && (s.flags&0x700000)!=0) {
            for(Entity entity:mc.world.loadedEntityList) if(entity.getUniqueID().equals(targetId))
                log.info("MARIOCRAFT COMBAT active-candidate frame={} face={} x={} z={} targetX={} targetY={} targetZ={}",frame,s.faceAngle,mc.player.posX,mc.player.posZ,entity.posX,entity.posY,entity.posZ);
        }
        String row="{\"frame\":"+frame+",\"recipe\":\""+RECIPES[Math.min((frame-1)/LENGTH,7)]+"\",\"action\":\""+Integer.toHexString(s.action)+"\",\"flags\":"+s.flags+",\"face\":"+s.faceAngle+",\"x\":"+mc.player.posX+",\"y\":"+mc.player.posY+",\"z\":"+mc.player.posZ+",\"vy\":"+s.velocity[1]+",\"speed\":"+s.forwardVelocity+",\"anim\":"+s.animID+",\"animFrame\":"+s.animFrame+",\"stickY\":"+submitted.y+",\"a\":"+submitted.a+",\"b\":"+submitted.b+",\"zButton\":"+submitted.z+"}\n";
        write(mc,"combat-trace.jsonl",row,frame==1);
        if(frame%5==0) {
            UUID id=targetId,playerId=mc.player.getUniqueID();final int observedPhase=phase;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(playerId);
                if(player==null) return;
                Entity e=id==null?null:((net.minecraft.world.WorldServer)player.world).getEntityFromUuid(id);
                if(e instanceof EntityLiving && observedPhase==phase && id.equals(targetId)) {
                    EntityLiving mob=(EntityLiving)e;
                    double travel=Math.sqrt(mob.getDistanceSq(spawnX,spawnY,spawnZ));
                    if(observedPhase>=5) ai &= !mob.isAIDisabled();
                    if(phase==5) { zombieTarget|=mob.getAttackTarget()==player;zombiePath|=!mob.getNavigator().noPath();
                        dev.mariocraft.health.HealthBridge.Channel incoming=dev.mariocraft.health.HealthBridge.OWNERS.get(playerId);
                        zombieAttack|=incoming!=null && incoming.lastCapturedHit!=null && mob.getUniqueID().equals(incoming.lastCapturedHit.trueSource);zombieTicks=mob.ticksExisted;zombieTravel=Math.max(zombieTravel,travel); }
                    if(phase==6) { batMoved|=Math.abs(mob.posY-spawnY)>.05;batTicks=mob.ticksExisted;batTravel=Math.max(batTravel,travel); }
                    if(phase==7) { skeletonTarget|=mob.getAttackTarget()==player;skeletonPath|=!mob.getNavigator().noPath();skeletonTicks=mob.ticksExisted;skeletonTravel=Math.max(skeletonTravel,travel); }
                }
                for(Entity entity:player.world.loadedEntityList) {
                    if(entity instanceof EntityArrow && ((EntityArrow)entity).shootingEntity!=null && ((EntityArrow)entity).shootingEntity.getUniqueID().equals(id)) projectile=true;
                    if(entity instanceof EntityItem && entity.getDistanceSq(site.x+.5,site.y,site.z+.5)<9) drops=true;
                }
                if(frame==TOTAL) finish(mc,log);
            });
        }
    }
    private void finish(Minecraft mc,Logger log) {
        String[] required={"punch-fixture:PUNCH","air-kick-fixture:KICK","stomp-fixture:STOMP","dive-fixture:DIVE","ground-pound-fixture:GROUND_POUND","ground-zombie-ai:PUNCH","airborne-bat-ai:PUNCH","projectile-skeleton-ai:PUNCH"};
        boolean attacks=true;for(String key:required) attacks &= hits.getOrDefault(key,0)>0;
        boolean passed=attacks && ai && zombieTarget && zombiePath && zombieAttack && skeletonTarget && projectile && batMoved && death && drops;
        String result="{\"passed\":"+passed+",\"combat\":true,\"frames\":"+TOTAL+",\"hits\":"+map(hits)+",\"attempts\":"+map(attempts)+",\"ordinaryAiEnabled\":"+ai+",\"zombieTargetsRealPlayer\":"+zombieTarget+",\"zombieNavigation\":"+zombiePath+",\"zombieVanillaAttack\":"+zombieAttack+",\"skeletonNavigation\":"+skeletonPath+",\"skeletonTargetsRealPlayer\":"+skeletonTarget+",\"skeletonProjectile\":"+projectile+",\"batAirborneMotion\":"+batMoved+",\"zombieTicks\":"+zombieTicks+",\"batTicks\":"+batTicks+",\"skeletonTicks\":"+skeletonTicks+",\"zombieTravel\":"+zombieTravel+",\"batTravel\":"+batTravel+",\"skeletonTravel\":"+skeletonTravel+",\"fixtureDeath\":"+death+",\"ordinaryItemDrops\":"+drops+",\"terrainEdits\":0}";
        write(mc,"gameplay-result.json",result,true);log.info("MARIOCRAFT COMBAT result {}",result);GameplayProbe.complete=true;
    }
    private static String map(Map<String,Integer> values) {
        StringBuilder out=new StringBuilder("{");for(Map.Entry<String,Integer> e:new TreeMap<>(values).entrySet()) { if(out.length()>1) out.append(',');out.append('"').append(e.getKey()).append("\":").append(e.getValue()); }return out.append('}').toString();
    }
    private static void write(Minecraft mc,String name,String text,boolean truncate) {
        try { Files.write(new java.io.File(mc.mcDataDir,name).toPath(),text.getBytes(StandardCharsets.UTF_8),truncate?new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}:new OpenOption[]{StandardOpenOption.APPEND}); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
