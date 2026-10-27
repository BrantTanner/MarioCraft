package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Actual vanilla fire cells and lava fluid heights, projected at 100 units/block. */
final class HazardVolume {
    static final class Contact {
        final int kind; final float nx,nz;
        Contact(int kind,float nx,float nz) { this.kind=kind;this.nx=nx;this.nz=nz; }
    }
    static double top(int level,boolean covered) { return covered?1:1-((level>=8?0:level)+1)/9.0; }
    static boolean overlaps(double x,double y,double z,double height,int bx,int by,int bz,double fluidHeight) {
        return x+.37>bx && x-.37<bx+1 && z+.37>bz && z-.37<bz+1 && y+height>by && y<by+fluidHeight;
    }
    static Contact sample(World world,double x,double y,double z,int action) {
        Contact fire=new Contact(0,0,0);double height=(action&0x8000)!=0?1:1.6;
        for(int bx=(int)Math.floor(x-.37);bx<=(int)Math.floor(x+.37);bx++)
            for(int by=(int)Math.floor(y);by<=(int)Math.floor(y+height);by++)
                for(int bz=(int)Math.floor(z-.37);bz<=(int)Math.floor(z+.37);bz++) {
                    BlockPos p=new BlockPos(bx,by,bz);if(!world.isBlockLoaded(p)) continue;
                    IBlockState s=world.getBlockState(p);Material m=s.getMaterial();
                    if(m!=Material.FIRE && !(m==Material.LAVA && s.getBlock() instanceof BlockLiquid)) continue;
                    double top=m==Material.FIRE?1:top(s.getValue(BlockLiquid.LEVEL),world.getBlockState(p.up()).getMaterial()==Material.LAVA);
                    if(!overlaps(x,y,z,height,bx,by,bz,top)) continue;
                    if(m==Material.FIRE) fire=new Contact(1,0,0);
                    else {
                        // Nearest horizontal face points away from the fluid cell. At
                        // center tie, use +X. This is a fluid-body crossover policy.
                        double dx=x-(bx+.5),dz=z-(bz+.5);
                        return Math.abs(dx)>=Math.abs(dz)?new Contact(2,dx<0?-1:1,0):new Contact(2,0,dz<0?-1:1);
                    }
                }
        return fire;
    }
    static Sm64Library.Surface[] surfaces(World world,TerrainMesh terrain,AxisAlignedBB region,Sm64Library.Surface[] solids) {
        List<int[]> tops=new ArrayList<>();
        for(int bx=(int)Math.floor(region.minX);bx<=(int)Math.floor(region.maxX);bx++)
            for(int bz=(int)Math.floor(region.minZ);bz<=(int)Math.floor(region.maxZ);bz++)
                for(int by=Math.max(0,(int)Math.floor(region.minY));by<=Math.min(255,(int)Math.floor(region.maxY));by++) {
                    BlockPos p=new BlockPos(bx,by,bz);if(!world.isBlockLoaded(p)) continue;
                    IBlockState s=world.getBlockState(p);
                    if(s.getMaterial()!=Material.LAVA || !(s.getBlock() instanceof BlockLiquid) || world.getBlockState(p.up()).getMaterial()==Material.LAVA) continue;
                    int y=Math.round(terrain.y(by+top(s.getValue(BlockLiquid.LEVEL),false)));
                    int x0=Math.round(terrain.x(bx)),x1=x0+100,z0=Math.round(terrain.z(bz)),z1=z0+100;
                    tops.add(new int[]{x0,y,z0,x0,y,z1,x1,y,z1});tops.add(new int[]{x0,y,z0,x1,y,z1,x1,y,z0});
                }
        if(tops.isEmpty()) return solids;
        Sm64Library.Surface[] result=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(solids.length+tops.size());
        for(int i=0;i<solids.length;i++) { result[i].vertices=solids[i].vertices;result[i].type=solids[i].type;result[i].force=solids[i].force;result[i].terrain=solids[i].terrain; }
        for(int i=0;i<tops.size();i++) { result[i+solids.length].type=1;result[i+solids.length].vertices=tops.get(i); }
        return result;
    }
}
