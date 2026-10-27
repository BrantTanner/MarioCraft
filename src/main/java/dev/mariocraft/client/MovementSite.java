package dev.mariocraft.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Test setup only: find an existing flat runway, without writing any terrain. */
final class MovementSite {
    final int x,y,z;
    MovementSite(int x,int y,int z) { this.x=x; this.y=y; this.z=z; }
    static MovementSite find(World world,int sx,int sz) {
        // Bounded search of generated normal terrain. Z is the recorded forward direction.
        for(int radius=0;radius<=192;radius+=8) {
            for(int dx=-radius;dx<=radius;dx+=8) for(int dz=-radius;dz<=radius;dz+=8) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                int x=sx+dx,z=sz+dz,y=world.getHeight(new BlockPos(x,0,z)).getY();
                boolean clear=true;
                for(int along=-8;along<=28 && clear;along++) for(int across=-2;across<=2 && clear;across++) {
                    BlockPos floor=new BlockPos(x+across,y-1,z+along);
                    if(!world.getBlockState(floor).isFullCube() || world.getHeight(floor).getY()!=y) clear=false;
                }
                if(clear) return new MovementSite(x,y,z);
            }
        }
        throw new IllegalStateException("No naturally clear movement runway within bounded search");
    }
}
