package dev.mariocraft.client;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The connected vertical vanilla-water volume containing Mario's feet, never a global plane. */
final class WaterVolume {
    static final int DRY=-11000;
    static final class Sample {
        final boolean wet;
        final double bottom,surface;
        Sample(boolean wet,double bottom,double surface) { this.wet=wet;this.bottom=bottom;this.surface=surface; }
        int nativeLevel(TerrainMesh terrain) { return wet?Math.round(terrain.y(surface)):DRY; }
    }
    interface Column { int level(int y); }
    /** level -1 means dry/unavailable; 0..15 are the vanilla LEVEL property. */
    static Sample sample(double y,Column column) {
        int at=(int)Math.floor(y);
        if(at<0 || at>255 || column.level(at)<0) return new Sample(false,0,0);
        int low=at,high=at;
        while(low>0 && column.level(low-1)>=0) low--;
        while(high<255 && column.level(high+1)>=0) high++;
        // Vanilla source/falling water has 8/9 height at an exposed top; submerged blocks are full.
        int level=column.level(high);
        double surface=high+1-((level>=8?0:level)+1)/9.0;
        return y<surface?new Sample(true,low,surface):new Sample(false,0,0);
    }
    static Sample sample(World world,double x,double y,double z) {
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        return sample(y,by->{
            BlockPos pos=new BlockPos(bx,by,bz);
            if(!world.isBlockLoaded(pos)) return -1;
            IBlockState state=world.getBlockState(pos);
            return state.getMaterial()==Material.WATER && state.getBlock() instanceof BlockLiquid?state.getValue(BlockLiquid.LEVEL):-1;
        });
    }
}
