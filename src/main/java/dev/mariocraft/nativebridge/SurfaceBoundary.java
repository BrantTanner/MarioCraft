package dev.mariocraft.nativebridge;
import java.util.*;

/** Select the exterior side of voxel walls before native quarter-step projection.
 * Native wall queries admit negative plane distances; submitting both faces of
 * a thin solid can otherwise project through the solid from its far face.
 * Floors/ceilings retain their original native winding and queries.
 */
public final class SurfaceBoundary {
    private final Sm64Library.Surface[] all;
    private BitSet previous;
    private Sm64Library.Surface[] selected;
    public SurfaceBoundary(Sm64Library.Surface[] all) { this.all=all; }
    public Sm64Library.Surface[] at(float x,float y,float z) {
        BitSet included=new BitSet(all.length);
        for(int i=0;i<all.length;i++) {
            int[] p=all[i].vertices;
            long nx=(long)(p[4]-p[1])*(p[8]-p[2])-(long)(p[5]-p[2])*(p[7]-p[1]);
            long ny=(long)(p[5]-p[2])*(p[6]-p[0])-(long)(p[3]-p[0])*(p[8]-p[2]);
            long nz=(long)(p[3]-p[0])*(p[7]-p[1])-(long)(p[4]-p[1])*(p[6]-p[0]);
            if(ny!=0 || nx*(x-p[0])+nz*(z-p[2])>=0) included.set(i);
        }
        if(included.equals(previous)) return selected;
        previous=included;
        if(included.isEmpty()) return selected=new Sm64Library.Surface[0];
        selected=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(included.cardinality());
        int n=0;
        for(int i=included.nextSetBit(0);i>=0;i=included.nextSetBit(i+1)) { Sm64Library.Surface copy=selected[n++],source=all[i]; copy.vertices=source.vertices;copy.type=source.type;copy.force=source.force;copy.terrain=source.terrain; }
        return selected;
    }
}
