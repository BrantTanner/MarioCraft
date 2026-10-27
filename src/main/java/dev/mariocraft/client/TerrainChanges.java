package dev.mariocraft.client;

import net.minecraft.world.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.state.IBlockState;

/** Client-thread world notifications invalidate collision before the next native step. */
final class TerrainChanges implements IWorldEventListener {
    boolean dirty=true;
    private int minX,minY,minZ,maxX,maxY,maxZ;
    void region(double x,double y,double z) { minX=(int)Math.floor(x-10);minY=0;minZ=(int)Math.floor(z-10);maxX=(int)Math.ceil(x+10);maxY=(int)Math.ceil(y+13);maxZ=(int)Math.ceil(z+10); }
    private void changed(int x,int y,int z,int x2,int y2,int z2) { if(x<=maxX&&x2>=minX&&y<=maxY&&y2>=minY&&z<=maxZ&&z2>=minZ) dirty=true; }
    public void notifyBlockUpdate(World w,BlockPos p,IBlockState before,IBlockState after,int flags) { if(before!=after) changed(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()); }
    public void markBlockRangeForRenderUpdate(int a,int b,int c,int d,int e,int f) { changed(a,b,c,d,e,f); }
    public void notifyLightSet(BlockPos p) {}
    public void playSoundToAllNearExcept(EntityPlayer p,SoundEvent s,SoundCategory c,double x,double y,double z,float v,float pitch) {}
    public void playRecord(SoundEvent s,BlockPos p) {}
    public void spawnParticle(int id,boolean range,double x,double y,double z,double vx,double vy,double vz,int... args) {}
    public void spawnParticle(int id,boolean range,boolean minimal,double x,double y,double z,double vx,double vy,double vz,int... args) {}
    public void onEntityAdded(Entity e) {}
    public void onEntityRemoved(Entity e) {}
    public void broadcastSound(int id,BlockPos p,int data) {}
    public void playEvent(EntityPlayer player,int type,BlockPos p,int data) {}
    public void sendBlockBreakProgress(int id,BlockPos p,int progress) {}
}
