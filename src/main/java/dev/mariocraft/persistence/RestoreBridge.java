package dev.mariocraft.persistence;

import net.minecraft.entity.player.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.event.entity.living.*;
import java.util.*;
import java.util.concurrent.*;

/** Concrete-player admission hold while disk state is validated and native ownership is acquired. */
public final class RestoreBridge {
    public static final ConcurrentMap<UUID,Hold> HOLDS=new ConcurrentHashMap<>();
    public static final class Hold {
        public final EntityPlayerMP serverPlayer;
        public final EntityPlayer clientPlayer;
        public volatile boolean active=true;
        public Hold(EntityPlayerMP serverPlayer,EntityPlayer clientPlayer) { this.serverPlayer=serverPlayer;this.clientPlayer=clientPlayer; }
        boolean owns(net.minecraft.entity.Entity entity) { return active&&(entity==serverPlayer||entity==clientPlayer); }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void update(LivingEvent.LivingUpdateEvent event) {
        Hold h=HOLDS.get(event.getEntityLiving().getUniqueID());
        if(h!=null&&h.owns(event.getEntityLiving())) { event.setCanceled(true);event.getEntityLiving().motionX=event.getEntityLiving().motionY=event.getEntityLiving().motionZ=0; }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void attack(LivingAttackEvent event) {
        Hold h=HOLDS.get(event.getEntityLiving().getUniqueID());if(h!=null&&h.owns(event.getEntityLiving())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void heal(LivingHealEvent event) {
        Hold h=HOLDS.get(event.getEntityLiving().getUniqueID());if(h!=null&&h.owns(event.getEntityLiving())) event.setCanceled(true);
    }
}
