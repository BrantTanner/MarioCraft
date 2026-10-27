package dev.mariocraft.nativebridge;
import java.util.*;

/** Bounded depenetration only when a changed solid contains Mario's probe center.
 * Native actions and velocity remain owned by libsm64. No free candidate means freeze.
 */
public final class VoxelRecovery {
    private VoxelRecovery() {}
    public static float[] find(float x,float y,float z,List<int[]> boxes) {
        List<int[]> containing=new ArrayList<>();
        for(int[] b:boxes) if(x>b[0]&&x<b[3]&&z>b[2]&&z<b[5]&&y+150>b[1]&&y+30<b[4]) containing.add(b);
        if(containing.isEmpty()) return new float[]{x,y,z};
        float[] best=null; double distance=Double.POSITIVE_INFINITY;
        for(int[] b:containing) for(float[] candidate:new float[][]{{b[0]-51,y,z},{b[3]+51,y,z},{x,y,b[2]-51},{x,y,b[5]+51},{x,b[4]+1,z}}) {
            double d=Math.pow(candidate[0]-x,2)+Math.pow(candidate[1]-y,2)+Math.pow(candidate[2]-z,2);
            if(d>160*160||d>=distance) continue;
            boolean clear=true,supported=false;
            for(int[] solid:boxes) {
                if(candidate[0]+50>solid[0]&&candidate[0]-50<solid[3]&&candidate[1]+161>solid[1]&&candidate[1]+1<solid[4]&&candidate[2]+50>solid[2]&&candidate[2]-50<solid[5]) {clear=false;break;}
                supported|=candidate[0]>=solid[0]&&candidate[0]<=solid[3]&&candidate[2]>=solid[2]&&candidate[2]<=solid[5]&&solid[4]<=candidate[1]+1&&solid[4]>=candidate[1]-10;
            }
            if(clear&&supported) {best=candidate;distance=d;}
        }
        return best;
    }
}
