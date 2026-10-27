package dev.mariocraft.persistence;

import dev.mariocraft.health.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.block.material.Material;

/** Server-side bounded geometry validation. Actions resume with a newly created native context. */
public final class MarioRestoration {
    private MarioRestoration() {}
    public static final class Result {
        public final MarioSaveData data;
        public final MarioSaveState state;
        public Result(MarioSaveData data,MarioSaveState state) { this.data=data;this.state=state; }
    }
    public static Result prepare(EntityPlayerMP player) {
        MarioSaveData data=MarioSaveData.load(player.getServer());MarioSaveState saved=data.get(player.getUniqueID());
        MarioLives lives=saved==null?new MarioLives():MarioLives.restore(saved.lives);
        boolean pending=lives.snapshot().phase==MarioLives.Phase.AWAITING_RESPAWN;
        if(pending) {
            // Saved pending death already paid one life. Complete respawn once, never call die().
            lives.respawn();player.setHealth(player.getMaxHealth());
            if(player.dimension!=0) {
                WorldServer target=player.getServer().getWorld(0);BlockPos spawn=RespawnBridge.safeSpawn(target);
                player.getServer().getPlayerList().transferPlayerToDimension(player,0,new net.minecraft.world.Teleporter(target) {
                    @Override public void placeInPortal(net.minecraft.entity.Entity entity,float yaw) { entity.setLocationAndAngles(spawn.getX()+.5,spawn.getY()+.1,spawn.getZ()+.5,0,0); }
                });
            }
        }
        WorldServer world=(WorldServer)player.world;
        double[] anchor=saved!=null && !pending && saved.dimension==player.dimension?anchor(world,saved.x,saved.y,saved.z):null;
        if(anchor==null && saved==null && !pending) anchor=anchor(world,player.posX,player.posY,player.posZ);
        if(anchor==null) { BlockPos spawn=RespawnBridge.safeSpawn(world);anchor=new double[]{spawn.getX()+.5,spawn.getY()+.1,spawn.getZ()+.5}; }
        player.motionX=player.motionY=player.motionZ=0;player.fallDistance=0;player.extinguish();
        player.setHealth(player.getMaxHealth());
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketUpdateHealth(player.getHealth(),player.getFoodStats().getFoodLevel(),player.getFoodStats().getSaturationLevel()));
        player.connection.setPlayerLocation(anchor[0],anchor[1],anchor[2],0,0);
        // Held movement packets cannot perform vanilla's watched-chunk handoff.
        player.getServer().getPlayerList().serverUpdateMovingPlayer(player);
        MarioSaveState result=new MarioSaveState(player.dimension,anchor[0],anchor[1],anchor[2],saved==null||pending?MarioLives.FULL_HEALTH:saved.health,lives.snapshot());
        data.publish(player.getUniqueID(),result);return new Result(data,result);
    }
    private static boolean clear(WorldServer world,double x,double y,double z) {
        BlockPos pos=new BlockPos(x,y,z);
        if(y<0||y>253.4||!world.getWorldBorder().contains(pos)||!world.getCollisionBoxes(null,new AxisAlignedBB(x-.37,y,z-.37,x+.37,y+1.6,z+.37)).isEmpty()) return false;
        for(BlockPos p:BlockPos.getAllInBox(pos.add(-1,-1,-1),pos.add(1,2,1))) {
            net.minecraft.block.state.IBlockState s=world.getBlockState(p);Material m=s.getMaterial();
            if(m==Material.LAVA||m==Material.FIRE||s.getBlock()==net.minecraft.init.Blocks.CACTUS||s.getBlock()==net.minecraft.init.Blocks.MAGMA) return false;
        }
        return true;
    }
    /** Connected vanilla water may resume at its clear anchor; dry air projects downward at most 160 blocks. */
    public static double[] anchor(WorldServer world,double x,double y,double z) {
        if(y<0 || y>253.4) return null;
        world.getChunkFromBlockCoords(new BlockPos(x,y,z));
        if(!clear(world,x,y,z)) return null;
        if(world.getBlockState(new BlockPos(x,y,z)).getMaterial()==Material.WATER && world.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof net.minecraft.block.BlockLiquid) return new double[]{x,y,z};
        RayTraceResult hit=world.rayTraceBlocks(new Vec3d(x,y+.05,z),new Vec3d(x,Math.max(0,y-160),z),false,true,false);
        if(hit==null||hit.sideHit!=net.minecraft.util.EnumFacing.UP) return null;
        double ground=hit.hitVec.y;
        return clear(world,x,ground+.01,z)?new double[]{x,ground+.01,z}:null;
    }
}
