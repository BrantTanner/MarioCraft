package dev.mariocraft.client;

import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Input-only native traversal of an existing natural bank; no terrain writes. */
final class TraversalProbe {
    private volatile Site site;
    private boolean finding;
    private int previousAction;
    private ControlState input;
    private boolean initialJump;
    private volatile double serverPoseError=Double.POSITIVE_INFINITY;
    private boolean finite=true,clearCenter=true;
    private final Set<Integer> actions=new LinkedHashSet<>();
    private final Path trace=new java.io.File(Minecraft.getMinecraft().mcDataDir,"traversal-trace.jsonl").toPath();
    private static final class Site {
        final int x,y,z,dx,dz,height;
        Site(int x,int y,int z,int dx,int dz,int h) { this.x=x;this.y=y;this.z=z;this.dx=dx;this.dz=dz;height=h; }
        float yaw() { return (float)Math.toDegrees(Math.atan2(-dx,dz)); }
    }
    private static Site find(World w,int sx,int sz) {
        for(int radius=0;radius<=160;radius+=2) for(int dx=-radius;dx<=radius;dx+=2) for(int dz=-radius;dz<=radius;dz+=2) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int x=sx+dx,z=sz+dz,top=w.getHeight(new BlockPos(x,0,z)).getY();
            for(int y=top;y>=Math.max(8,top-24);y--) {
                if(!w.getBlockState(new BlockPos(x,y-1,z)).isFullCube() || !w.isAirBlock(new BlockPos(x,y,z))) continue;
                for(int[] dir:new int[][]{{0,1},{1,0},{0,-1},{-1,0}}) {
                    boolean clear=true;
                    for(int along=0;along<4&&clear;along++) {
                        BlockPos floor=new BlockPos(x+dir[0]*along,y-1,z+dir[1]*along);
                        if(!w.getBlockState(floor).isFullCube() || !w.isAirBlock(floor.up()) || !w.isAirBlock(floor.up(2)) || !w.isAirBlock(floor.up(3))) clear=false;
                    }
                    if(!clear) continue;
                    int h=0;
                    while(h<6&&w.getBlockState(new BlockPos(x+dir[0]*4,y+h,z+dir[1]*4)).isFullCube()) h++;
                    if(h!=3 || !w.isAirBlock(new BlockPos(x+dir[0]*4,y+h,z+dir[1]*4)) || !w.isAirBlock(new BlockPos(x+dir[0]*4,y+h+1,z+dir[1]*4))) continue;
                    return new Site(x,y,z,dir[0],dir[1],h);
                }
            }
        }
        throw new IllegalStateException("No natural 3..5 block bank with clear approach in bounded search");
    }
    boolean prepare(Minecraft mc,Logger log) {
        if(!finding) { finding=true; final java.util.UUID id=mc.player.getUniqueID(); mc.getIntegratedServer().addScheduledTask(()->{
            try {
                net.minecraft.entity.player.EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                Site s=find(p.world,(int)p.posX,(int)p.posZ);
                p.connection.setPlayerLocation(s.x+.5,s.y,s.z+.5,s.yaw(),0); site=s;
                log.info("MARIOCRAFT TRAVERSAL natural-bank x={} y={} z={} direction={},{} height={} terrainEdits=0",s.x,s.y,s.z,s.dx,s.dz,s.height);
            } catch(RuntimeException e) { log.error("Traversal site",e); GameplayProbe.fail("natural traversal bank setup"); }
        }); }
        return site!=null && mc.player.getDistanceSq(site.x+.5,site.y,site.z+.5)<1;
    }
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc) {
        if(frame%180==0) {
            mc.player.setPosition(site.x+.5,site.y,site.z+.5);
            session.position(terrain.x(mc.player.posX),terrain.y(mc.player.posY)+10,terrain.z(mc.player.posZ));
        }
    }
    float camera() { return site.yaw(); }
    ControlState controls(int frame,MarioBindings bindings) {
        int recipe=frame/180,t=frame%180;
        boolean forward=t>=40&&t<125;
        if(t==0) initialJump=false;
        Minecraft mc=Minecraft.getMinecraft();
        double distance=4-((mc.player.posX-(site.x+.5))*site.dx+(mc.player.posZ-(site.z+.5))*site.dz)-.5;
        boolean edge=recipe==0&&t>=40&&!initialJump&&distance<=1.1;
        if(edge) initialJump=true;
        boolean jump=recipe==0 ? edge || previousAction==0x010208B6 : t>=45&&t<65;
        for(int i=0;i<16;i++) KeyBinding.setKeyBindState(bindings.binding(i).getKeyCode(),false);
        KeyBinding.setKeyBindState(bindings.binding(0).getKeyCode(),forward);
        KeyBinding.setKeyBindState(bindings.binding(6).getKeyCode(),jump);
        input=bindings.sample(); return input;
    }
    void observe(int frame,Sm64Library.State s,Minecraft mc,Logger log) {
        previousAction=s.action; actions.add(s.action);
        finite &= Double.isFinite(mc.player.posX)&&Double.isFinite(mc.player.posY)&&Double.isFinite(mc.player.posZ);
        clearCenter &= mc.world.getCollisionBoxes(null,new net.minecraft.util.math.AxisAlignedBB(mc.player.posX-.001,mc.player.posY+.3,mc.player.posZ-.001,mc.player.posX+.001,mc.player.posY+1.5,mc.player.posZ+.001)).isEmpty();
        if(frame==340) {
            final java.util.UUID id=mc.player.getUniqueID();final double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;
            mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.entity.player.EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);if(p!=null)serverPoseError=Math.sqrt(p.getDistanceSq(x,y,z));});
        }
        String row="{\"frame\":"+frame+",\"recipe\":"+((frame-1)/180)+",\"action\":\""+Integer.toHexString(s.action)+"\",\"x\":"+mc.player.posX+",\"y\":"+mc.player.posY+",\"z\":"+mc.player.posZ+",\"vy\":"+s.velocity[1]+",\"speed\":"+s.forwardVelocity+",\"anim\":"+s.animID+",\"animFrame\":"+s.animFrame+",\"stickY\":"+input.y+",\"a\":"+input.a+"}\n";
        try { Files.write(trace,row.getBytes(StandardCharsets.UTF_8),frame==1?new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}:new OpenOption[]{StandardOpenOption.APPEND}); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
        if(frame==360) {
            boolean kick=actions.contains(0x03000886),ledge=actions.contains(0x0800034B),climb=actions.contains(0x54C)&&actions.contains(0x54D);
            String result="{\"passed\":"+(kick&&ledge&&climb&&finite&&clearCenter&&serverPoseError<=.05)+",\"traversal\":true,\"wallKick\":"+kick+",\"ledgeGrab\":"+ledge+",\"ledgeClimb\":"+climb+",\"finite\":"+finite+",\"clearCenter\":"+clearCenter+",\"serverPoseError\":"+(Double.isFinite(serverPoseError)?serverPoseError:-1)+",\"stepHeight\":"+mc.player.stepHeight+",\"terrainEdits\":0,\"actions\":\""+actions.toString()+"\"}";
            try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),result.getBytes(StandardCharsets.UTF_8)); } catch(java.io.IOException e) { throw new IllegalStateException(e); }
            log.info("MARIOCRAFT TRAVERSAL result {}",result); GameplayProbe.complete=true;
        }
    }
}
