package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import java.util.*;

/** Outward-wound real block collision boxes, with an explicit local coordinate origin. */
final class TerrainMesh {
    final double ox,oy,oz;
    TerrainMesh(double x,double y,double z) { ox=Math.floor(x/16)*16; oy=Math.floor(y/16)*16; oz=Math.floor(z/16)*16; }
    float x(double x) { return (float)((x-ox)*100); }
    float y(double y) { return (float)((y-oy)*100); }
    float z(double z) { return (float)((z-oz)*100); }
    Sm64Library.Surface[] build(World world,double x,double y,double z) {
        List<AxisAlignedBB> boxes = world.getCollisionBoxes(null,new AxisAlignedBB(x-9,y-8,z-9,x+9,y+12,z+9));
        if(boxes.isEmpty()) return new Sm64Library.Surface[0];
        Sm64Library.Surface[] surfaces=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(boxes.size()*12);
        int index=0;
        for(AxisAlignedBB b:boxes) {
            int a=Math.round(x(b.minX)), c=Math.round(x(b.maxX));
            int d=Math.round(y(b.minY)), e=Math.round(y(b.maxY));
            int f=Math.round(z(b.minZ)), g=Math.round(z(b.maxZ));
            int[][] v={{a,d,f},{c,d,f},{c,d,g},{a,d,g},{a,e,f},{c,e,f},{c,e,g},{a,e,g}};
            int[][] triangles={{4,7,6},{4,6,5},{0,1,2},{0,2,3},{0,3,7},{0,7,4},{1,5,6},{1,6,2},{0,4,5},{0,5,1},{3,2,6},{3,6,7}};
            for(int[] t:triangles) {
                int[] vertices=surfaces[index++].vertices;
                for(int i=0;i<3;i++) System.arraycopy(v[t[i]],0,vertices,i*3,3);
            }
        }
        return surfaces;
    }
}
