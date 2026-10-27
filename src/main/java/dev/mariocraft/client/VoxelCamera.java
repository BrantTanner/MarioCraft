package dev.mariocraft.client;

import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Swept padded collision boxes, plus eight 0.125 samples for missing-chunk barriers. */
final class VoxelCamera implements MarioCamera.Obstruction {
    private final World world;
    VoxelCamera(World world) { this.world=world; }
    public double fraction(MarioCamera.Point focus,MarioCamera.Point eye) {
        Vec3d a=new Vec3d(focus.x,focus.y,focus.z),b=new Vec3d(eye.x,eye.y,eye.z);
        double length=a.distanceTo(b);
        if(length<.00001) return world.isBlockLoaded(new BlockPos(a)) && world.getCollisionBoxes(null,new AxisAlignedBB(a.x-.12,a.y-.12,a.z-.12,a.x+.12,a.y+.12,a.z+.12)).isEmpty()?1:0;
        double fraction=1;
        AxisAlignedBB swept=new AxisAlignedBB(a.x,a.y,a.z,b.x,b.y,b.z).grow(.12);
        for(AxisAlignedBB box:world.getCollisionBoxes(null,swept)) {
            AxisAlignedBB padded=box.grow(.12);
            if(padded.contains(a)) return 0;
            RayTraceResult hit=padded.calculateIntercept(a,b);
            if(hit!=null) fraction=Math.min(fraction,Math.max(0,a.distanceTo(hit.hitVec)/length-.01/length));
        }
        for(int i=1;i<=8;i++) {
            double t=i*.125;
            if(!world.isBlockLoaded(new BlockPos(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t))) fraction=Math.min(fraction,(i-1)*.125);
        }
        return fraction;
    }
}
