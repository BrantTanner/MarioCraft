package dev.mariocraft.client;

import dev.mariocraft.nativebridge.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Outward union boundary of real block collision boxes at 100 native units/block. */
final class TerrainMesh {
    final double ox,oy,oz;
    private SurfaceBoundary boundary;
    private List<int[]> collisionBoxes;
    float[] recovery(double x,double y,double z) { return VoxelRecovery.find(x(x),y(y),z(z),collisionBoxes); }
    Sm64Library.Surface[] at(double x,double y,double z) { return boundary.at(x(x),y(y),z(z)); }
    TerrainMesh(double x,double y,double z) { ox=Math.floor(x/16)*16; oy=Math.floor(y/16)*16; oz=Math.floor(z/16)*16; }
    float x(double x) { return (float)((x-ox)*100); }
    float y(double y) { return (float)((y-oy)*100); }
    float z(double z) { return (float)((z-oz)*100); }
    static long loadedMask(World world,double x,double z) {
        int minX=Math.floorDiv((int)Math.floor(x-9),16),maxX=Math.floorDiv((int)Math.floor(x+9),16);
        int minZ=Math.floorDiv((int)Math.floor(z-9),16),maxZ=Math.floorDiv((int)Math.floor(z+9),16);
        long result=17;
        for(int cx=minX;cx<=maxX;cx++) for(int cz=minZ;cz<=maxZ;cz++) result=result*31+(world.isBlockLoaded(new BlockPos(cx*16,64,cz*16))?1:0);
        return result;
    }
    Sm64Library.Surface[] build(World world,double x,double y,double z) {
        AxisAlignedBB region=new AxisAlignedBB(x-9,y-8,z-9,x+9,y+12,z+9);
        List<AxisAlignedBB> boxes=new ArrayList<>(world.getCollisionBoxes(null,region));
        // Missing chunks are a temporary wall, never an invitation to walk into
        // absent collision. Chunk arrival/removal invalidates this mesh.
        for(int cx=Math.floorDiv((int)Math.floor(region.minX),16);cx<=Math.floorDiv((int)Math.floor(region.maxX),16);cx++)
            for(int cz=Math.floorDiv((int)Math.floor(region.minZ),16);cz<=Math.floorDiv((int)Math.floor(region.maxZ),16);cz++)
                if(!world.isBlockLoaded(new BlockPos(cx*16,64,cz*16))) boxes.add(new AxisAlignedBB(cx*16,region.minY,cz*16,cx*16+16,region.maxY,cz*16+16));
        List<int[]> nativeBoxes=new ArrayList<>();
        for(AxisAlignedBB b:boxes) nativeBoxes.add(new int[]{Math.round(x(b.minX)),Math.round(y(b.minY)),Math.round(z(b.minZ)),Math.round(x(b.maxX)),Math.round(y(b.maxY)),Math.round(z(b.maxZ))});
        collisionBoxes=nativeBoxes;
        boundary=new SurfaceBoundary(BoxSurfaces.build(nativeBoxes));
        return at(x,y,z);
    }
}
