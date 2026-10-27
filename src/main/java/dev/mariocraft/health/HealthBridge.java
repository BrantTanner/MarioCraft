package dev.mariocraft.health;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Integrated-server event capture. Native calls belong exclusively to the client simulation. */
public final class HealthBridge {
    public static final ConcurrentMap<UUID,Channel> OWNERS=new ConcurrentHashMap<>();
    public static final class Hit {
        public final int damage;
        public final String kind;
        public final UUID immediateSource,trueSource;
        public final double x,y,z;
        public final long tick;
        public final float minecraftDamage;
        public final double bodyHeight,bodyWidth;
        Hit(int damage,String kind,UUID immediateSource,UUID trueSource,double x,double y,double z,long tick,float amount,double bodyHeight,double bodyWidth) {
            this.damage=damage;this.kind=kind;this.immediateSource=immediateSource;this.trueSource=trueSource;
            this.x=x;this.y=y;this.z=z;this.tick=tick;
            minecraftDamage=amount;this.bodyHeight=bodyHeight;this.bodyWidth=bodyWidth;
        }
    }
    public static final class Channel {
        public final Object token=new Object();
        public final World serverWorld;
        public final World clientWorld;
        public final Object server;
        public volatile boolean active=true;
        public volatile MarioHealth.Snapshot snapshot;
        public volatile MarioLives.Snapshot lifeState;
        public volatile SwimRestoration swimRestoration;
        public volatile Hit lastCapturedHit;
        private Vec3d explosionOrigin;
        private long explosionTick=Long.MIN_VALUE;
        public final AtomicInteger received=new AtomicInteger(),coalesced=new AtomicInteger();
        private final ArrayBlockingQueue<Hit> pending=new ArrayBlockingQueue<>(1);
        // Bounded history of immutable native events; readers may retain a sequence cursor.
        private final ArrayDeque<MarioHealth.Snapshot> events=new ArrayDeque<>();
        public Channel(World world,Object server,World clientWorld) { serverWorld=world;this.server=server;this.clientWorld=clientWorld; }
        public Hit poll() { return active?pending.poll():null; }
        public void clear() { active=false;pending.clear(); }
        public void publish(MarioHealth.Snapshot state) {
            snapshot=state;
            if(state.changed) synchronized(events) { if(events.size()==64) events.removeFirst();events.addLast(state); }
        }
        public List<MarioHealth.Snapshot> eventsAfter(long sequence) {
            synchronized(events) { List<MarioHealth.Snapshot> result=new ArrayList<>();for(MarioHealth.Snapshot s:events) if(s.sequence>sequence) result.add(s);return Collections.unmodifiableList(result); }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void explosion(net.minecraftforge.event.world.ExplosionEvent.Detonate event) {
        if(event.getWorld().isRemote) return;
        for(Entity entity:event.getAffectedEntities()) {
            Channel channel=OWNERS.get(entity.getUniqueID());
            if(channel!=null && channel.active && entity.world==channel.serverWorld) {
                channel.explosionOrigin=event.getExplosion().getPosition();channel.explosionTick=entity.world.getTotalWorldTime();
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void attack(LivingAttackEvent event) {
        if(!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player=(EntityPlayer)event.getEntityLiving();Channel channel=OWNERS.get(player.getUniqueID());
        if(channel==null || !channel.active || player.world!=(player.world.isRemote?channel.clientWorld:channel.serverWorld)) return;
        // Cancel before vanilla armor, resistance, hunger, hurt timers and knockback.
        event.setCanceled(true);
        if(player.world.isRemote || !player.isEntityAlive() || player.world.getMinecraftServer()!=channel.server) return;
        DamageSource source=event.getSource();
        // Native landing owns falls. Dedicated environment handlers are the following slices.
        if(source==DamageSource.FALL || source==DamageSource.DROWN || source==DamageSource.STARVE || source.isFireDamage()) return;
        int damage=MarioHealth.damage(event.getAmount());if(damage==0) return;
        Entity immediate=source.getImmediateSource(),attacker=source.getTrueSource();
        Vec3d location=source.getDamageLocation();
        if(source.isExplosion() && channel.explosionTick==player.world.getTotalWorldTime()) location=channel.explosionOrigin;
        if(location==null && immediate!=null) location=immediate.getPositionVector();
        if(location==null) location=player.getPositionVector().addVector(0,0,1);
        if(!Double.isFinite(location.x)||!Double.isFinite(location.y)||!Double.isFinite(location.z)) return;
        channel.received.incrementAndGet();
        net.minecraft.util.math.AxisAlignedBB body=player.getEntityBoundingBox();
        Hit hit=new Hit(damage,source.damageType,immediate==null?null:immediate.getUniqueID(),attacker==null?null:attacker.getUniqueID(),location.x,location.y,location.z,player.world.getTotalWorldTime(),event.getAmount(),body.maxY-body.minY,body.maxX-body.minX);
        channel.lastCapturedHit=hit;
        if(!channel.pending.offer(hit)) channel.coalesced.incrementAndGet();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void heal(LivingHealEvent event) {
        Channel channel=OWNERS.get(event.getEntityLiving().getUniqueID());
        if(channel!=null && channel.active && event.getEntityLiving().world==(event.getEntityLiving().world.isRemote?channel.clientWorld:channel.serverWorld)) event.setCanceled(true);
    }
}
