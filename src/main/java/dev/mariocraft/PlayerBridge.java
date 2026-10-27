package dev.mariocraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import java.util.*;
import java.util.concurrent.*;

/** In-process single-player handoff. Existing vanilla player identity remains the mob target. */
public final class PlayerBridge {
    public static final ConcurrentMap<UUID, Pose> POSES = new ConcurrentHashMap<>();
    private final Map<EntityPlayer,Float> previousSteps = Collections.synchronizedMap(new WeakHashMap<>());
    public static final class Pose {
        public final double x,y,z;
        public final boolean ground;
        public Pose(double x,double y,double z,boolean ground) { this.x=x; this.y=y; this.z=z; this.ground=ground; }
    }
    @SubscribeEvent public void living(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer)event.getEntityLiving();
        Pose pose = POSES.get(player.getUniqueID());
        if (pose == null || !player.isEntityAlive()) {
            Float step = previousSteps.remove(player);
            if(step!=null) player.stepHeight=step;
            return;
        }
        previousSteps.putIfAbsent(player,player.stepHeight);
        // Native actions already resolve collision/gravity. Vanilla living motion must not run too.
        event.setCanceled(true);
        player.stepHeight=0;
        player.motionX=player.motionY=player.motionZ=0;
        player.fallDistance=0;
        player.onGround=pose.ground;
        if(player.world.isRemote) player.setPosition(pose.x,pose.y,pose.z);
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || POSES.isEmpty()) return;
        net.minecraft.server.MinecraftServer server=FMLCommonHandler.instance().getMinecraftServerInstance();
        if(server==null || server.isDedicatedServer()) return;
        // NetHandlerPlayServer.update restores its captured tick position after onUpdateEntity.
        // Apply native authority after that network tick, so it survives into the next world tick.
        POSES.forEach((id,pose)->{
            net.minecraft.entity.player.EntityPlayerMP player=server.getPlayerList().getPlayerByUUID(id);
            if(player!=null && player.isEntityAlive()) { player.setPosition(pose.x,pose.y,pose.z); player.onGround=pose.ground; }
        });
    }
}
