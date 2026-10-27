package dev.mariocraft.health;

import dev.mariocraft.PlayerBridge;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import java.util.*;
import java.util.concurrent.*;

/** Server-only Minecraft clone/position handoff; never calls the native runtime. */
public final class RespawnBridge {
    public static final ConcurrentMap<UUID,Pending> PENDING=new ConcurrentHashMap<>();
    public static final class Pending {
        public final EntityPlayerMP original;
        public final Object server;
        public final MarioLives.Snapshot lifeState;
        public volatile EntityPlayerMP replacement;
        public volatile BlockPos spawn;
        public volatile String failure;
        public Pending(EntityPlayerMP original,MarioLives.Snapshot lifeState) { this.original=original;server=original.getServer();this.lifeState=lifeState; }
    }
    /** Surface candidates on a four-block X/Z lattice, in ring order about the world's spawn. */
    public static BlockPos safeSpawn(WorldServer world) {
        BlockPos center=world.getSpawnPoint();
        for(int radius=0;radius<=128;radius+=4) for(int dx=-radius;dx<=radius;dx+=4) for(int dz=-radius;dz<=radius;dz+=4) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int x=center.getX()+dx,z=center.getZ()+dz;
            world.getChunkFromBlockCoords(new BlockPos(x,64,z));
            BlockPos feet=new BlockPos(x,Math.min(254,world.getHeight(x,z)),z);
            if(safe(world,feet)) return feet;
        }
        throw new IllegalStateException("No safe designated spawn within 128 blocks; world was not modified");
    }
    public static boolean safe(WorldServer world,BlockPos feet) {
        if(!world.getWorldBorder().contains(feet)) return false;
        double x=feet.getX()+.5,y=feet.getY(),z=feet.getZ()+.5;
        AxisAlignedBB body=new AxisAlignedBB(x-.37,y,z-.37,x+.37,y+1.6,z+.37);
        if(!world.getCollisionBoxes(null,body).isEmpty()) return false;
        if(world.getCollisionBoxes(null,new AxisAlignedBB(x-.3,y-.05,z-.3,x+.3,y,z+.3)).isEmpty()) return false;
        for(BlockPos p:BlockPos.getAllInBox(feet.add(-1,-1,-1),feet.add(1,2,1))) {
            net.minecraft.block.state.IBlockState s=world.getBlockState(p);Material m=s.getMaterial();
            if(m==Material.FIRE || m==Material.LAVA || m==Material.WATER || s.getBlock()==net.minecraft.init.Blocks.CACTUS || s.getBlock()==net.minecraft.init.Blocks.MAGMA) return false;
        }
        return true;
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void pendingDamage(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        Pending pending=PENDING.get(event.getEntityLiving().getUniqueID());
        if(pending!=null && (event.getEntityLiving()==pending.original || event.getEntityLiving()==pending.replacement)) event.setCanceled(true);
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void pendingHealing(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        Pending pending=PENDING.get(event.getEntityLiving().getUniqueID());
        if(pending!=null && (event.getEntityLiving()==pending.original || event.getEntityLiving()==pending.replacement)) event.setCanceled(true);
    }
    @SubscribeEvent public void waiting(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        Pending pending=PENDING.get(event.getEntityLiving().getUniqueID());
        if(pending!=null && event.getEntityLiving()==pending.replacement && event.getEntityLiving().isEntityAlive()) {
            event.setCanceled(true);event.getEntityLiving().motionX=event.getEntityLiving().motionY=event.getEntityLiving().motionZ=0;
        }
    }
    @SubscribeEvent public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        Pending pending=PENDING.get(event.getEntityPlayer().getUniqueID());
        if(pending==null || pending.original!=event.getOriginal() || !event.isWasDeath()) return;
        net.minecraft.entity.player.EntityPlayer original=event.getOriginal(),player=event.getEntityPlayer();
        if(player instanceof EntityPlayerMP) pending.replacement=(EntityPlayerMP)player;
        player.inventory.copyInventory(original.inventory);
        player.experienceLevel=original.experienceLevel;player.experienceTotal=original.experienceTotal;player.experience=original.experience;
        net.minecraft.nbt.NBTTagCompound food=new net.minecraft.nbt.NBTTagCompound();
        original.getFoodStats().writeNBT(food);player.getFoodStats().readNBT(food);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if(!(event.player instanceof EntityPlayerMP) || event.isEndConquered()) return;
        EntityPlayerMP player=(EntityPlayerMP)event.player;Pending pending=PENDING.get(player.getUniqueID());
        if(pending==null || pending.replacement!=player || pending.server!=player.getServer()) return;
        try {
            WorldServer world=(WorldServer)player.world;
            BlockPos spawn=safeSpawn(world);
            player.motionX=player.motionY=player.motionZ=0;player.fallDistance=0;player.extinguish();
            player.setHealth(player.getMaxHealth());
            player.connection.setPlayerLocation(spawn.getX()+.5,spawn.getY()+.1,spawn.getZ()+.5,0,0);
            pending.spawn=spawn;pending.replacement=player;
        } catch(RuntimeException e) { pending.failure=e.toString(); }
    }
    public static void begin(Pending pending) {
        EntityPlayerMP player=pending.original;
        if(player.getServer()!=pending.server || player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID())!=player || !player.isEntityAlive()) return;
        // Set the death state directly: native Mario already resolved the lethal damage.
        // Calling vanilla onDeath would add an inventory/XP/score consequence path to the native death.
        PlayerBridge.POSES.remove(player.getUniqueID());
        player.motionX=player.motionY=player.motionZ=0;player.fallDistance=0;player.extinguish();
        player.setHealth(0);
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketUpdateHealth(0,player.getFoodStats().getFoodLevel(),player.getFoodStats().getSaturationLevel()));
    }
}
