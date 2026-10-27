package dev.mariocraft.client;
import net.minecraft.util.math.*;
/** Conservative three-centimeter contact corridor against actual voxel collision boxes. */
final class CombatGeometry {
    static boolean clear(Iterable<AxisAlignedBB> boxes,Vec3d from,Vec3d to) {
        for(AxisAlignedBB box:boxes) {
            AxisAlignedBB padded=box.grow(.03);
            if(padded.contains(from) || padded.contains(to) || padded.calculateIntercept(from,to)!=null) return false;
        }
        return true;
    }
}
