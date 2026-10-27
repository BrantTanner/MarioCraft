package dev.mariocraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.*;

/** Matches vanilla 1.12.2's eight camera obstruction rays, including its corner offsets. */
final class BaselineCamera {
    static double clearance(Minecraft mc,float yaw,float pitch,float partial) {
        net.minecraft.entity.Entity view=mc.getRenderViewEntity();
        Vec3d focus=new Vec3d(view.prevPosX+(view.posX-view.prevPosX)*partial,
            view.prevPosY+(view.posY-view.prevPosY)*partial+view.getEyeHeight(),
            view.prevPosZ+(view.posZ-view.prevPosZ)*partial);
        double dx=-Math.sin(Math.toRadians(yaw))*Math.cos(Math.toRadians(pitch))*4;
        double dz=Math.cos(Math.toRadians(yaw))*Math.cos(Math.toRadians(pitch))*4;
        double dy=-Math.sin(Math.toRadians(pitch))*4;
        double distance=4;
        for(int corner=0;corner<8;corner++) {
            double x=((corner&1)*2-1)*.1,y=(((corner>>1)&1)*2-1)*.1,z=(((corner>>2)&1)*2-1)*.1;
            RayTraceResult hit=mc.world.rayTraceBlocks(focus.addVector(x,y,z),focus.addVector(-dx+x+z,-dy+y,-dz+z));
            if(hit!=null) distance=Math.min(distance,focus.distanceTo(hit.hitVec));
        }
        return distance;
    }
}
