package dev.mariocraft.client;
import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.*;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.*;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opt-in normal same-save Nether generation; no block writes or forced actions. */
final class HazardProbe {
    static final int LENGTH=240,TOTAL=1200;
    private volatile double[] fire,lava;
    private boolean finding;
    private int contact,entries,fireEntries,lavaEntries,minus10,minus64;
    private final int[] minimum={0x880,0x880,0x880,0x880,0x880};
    private int previousHealth;
    private boolean lavaRecovered;
    private boolean recovered,death,vanilla=true;
    private int previousAction;
    boolean prepare(Minecraft mc,Logger log) {
        if(!finding) {
            finding=true;UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    WorldServer w=mc.getIntegratedServer().getWorld(-1);
                    double[] f=null,l=null;
                    search:for(int r=0;r<=6;r++) for(int cx=-r;cx<=r;cx++) for(int cz=-r;cz<=r;cz++) {
                        if(Math.max(Math.abs(cx),Math.abs(cz))!=r) continue;
                        w.getChunkFromChunkCoords(cx,cz);
                        for(int x=cx*16;x<cx*16+16;x++) for(int z=cz*16;z<cz*16+16;z++) for(int y=30;y<=110;y++) {
                            BlockPos p=new BlockPos(x,y,z);Material m=w.getBlockState(p).getMaterial();
                            if(f==null && m==Material.FIRE && w.isAirBlock(p.up()) && w.getBlockState(p.down()).isFullCube()) {
                                // Keep the initial burning leap clear of nearby lava;
                                // subsequent native movement/collision remains unmodified.
                                boolean clear=true;
                                for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=8;dz++) for(int dy=0;dy<=3;dy++) {
                                    BlockPos q=p.add(dx,dy,dz);Material a=w.getBlockState(q).getMaterial();
                                    if(a==Material.LAVA || (dy>0 && !w.isAirBlock(q))) clear=false;
                                }
                                if(clear && w.getBlockState(p.add(0,-1,6)).isFullCube() && w.isAirBlock(p.add(0,0,6))) f=new double[]{x+.5,y+.02,z+.5};
                            }
                            if(l==null && m==Material.LAVA && w.isAirBlock(p.up())) {
                                boolean clear=true;
                                for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                                    if(w.getBlockState(p.add(dx,0,dz)).getMaterial()!=Material.LAVA) clear=false;
                                    for(int dy=1;dy<=12;dy++) if(!w.isAirBlock(p.add(dx,dy,dz))) clear=false;
                                }
                                if(clear) l=new double[]{x+.5,y+1.2,z+.5};
                            }
                            if(f!=null && l!=null) break search;
                        }
                    }
                    if(f==null || l==null) throw new IllegalStateException("Bounded natural Nether fire/lava search failed fire="+(f!=null)+" lava="+(l!=null));
                    final double[] start=f;
                    dev.mariocraft.ScopeRules.fixtureTransfer(player,-1,new Teleporter(w) {
                        @Override public void placeInPortal(Entity entity,float yaw) { entity.setLocationAndAngles(start[0],start[1],start[2],0,0); }
                    },"hazard");
                    player.connection.setPlayerLocation(f[0],f[1],f[2],0,0);player.getServer().getPlayerList().serverUpdateMovingPlayer(player);
                    fire=f;lava=l;
                    log.info("MARIOCRAFT HAZARD natural-same-save-Nether fire={},{},{} lava={},{},{} terrainEdits=0",f[0],f[1],f[2],l[0],l[1],l[2]);
                } catch(RuntimeException e) { log.error("Hazard natural setup",e);GameplayProbe.fail("natural hazard site setup"); }
            });
        }
        return fire!=null && mc.player.dimension==-1 && mc.player.getDistanceSq(fire[0],fire[1],fire[2])<1;
    }
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc) {
        if(frame>=TOTAL) return;
        if(frame%LENGTH==0) {
            int phase=frame/LENGTH;double[] p=phase<2?fire:lava;
            session.configureHealthForProbe(phase==4?0x180:0x880);
            previousHealth=phase==4?0x180:0x880;
            double y=p[1]-(phase==3?.5:0);
            mc.player.setPosition(p[0],y,p[2]);session.position(terrain.x(p[0]),terrain.y(y),terrain.z(p[2]));
        }
        int phase=frame/LENGTH,t=frame%LENGTH;
        if((phase==2 && t==70) || (phase==3 && t==15)) {
            // Explicit setup-only hazard exit, no action/velocity reset or healing.
            mc.player.setPosition(fire[0],fire[1],fire[2]+6);
            session.position(terrain.x(fire[0]),terrain.y(fire[1]),terrain.z(fire[2]+6));
        }
    }
    ControlState controls(int frame) { return new ControlState(); }
    void contact(int kind,boolean admitted) { contact=kind;if(admitted) { entries++;if(kind==1) fireEntries++;else lavaEntries++; } }
    void observe(int frame,Sm64Library.State s,Minecraft mc,Logger log) {
        GameplayProbe.currentFrame=frame;if(frame>TOTAL) return;
        int phase=(frame-1)/LENGTH,h=Short.toUnsignedInt(s.health);
        minimum[phase]=Math.min(minimum[phase],h);
        if((frame-1)%LENGTH>0) { if(previousHealth-h==10) minus10++;if(previousHealth-h==64) minus64++; }
        previousHealth=h;
        boolean burn=s.action==0x010208B4 || s.action==0x010208B5 || s.action==0x00020449;
        if(phase<2 && !burn && (previousAction==0x010208B4 || previousAction==0x010208B5 || previousAction==0x00020449)) recovered=true;
        previousAction=s.action;
        if((phase==2 || phase==3) && frame%LENGTH>170 && s.action!=0x010208B7 && h>0xFF) lavaRecovered=true;
        death|=phase==4 && h==0xFF;
        write(mc,"hazard-trace.jsonl","{\"frame\":"+frame+",\"phase\":"+phase+",\"contactBeforeStep\":"+contact+",\"health\":"+h+",\"action\":\""+Integer.toHexString(s.action)+"\",\"animation\":"+s.animID+",\"animFrame\":"+s.animFrame+",\"invinc\":"+s.invincTimer+",\"x\":"+mc.player.posX+",\"y\":"+mc.player.posY+",\"z\":"+mc.player.posZ+",\"vx\":"+s.velocity[0]+",\"vy\":"+s.velocity[1]+",\"vz\":"+s.velocity[2]+",\"speed\":"+s.forwardVelocity+"}\n",frame==1);
        if(death) { finish(mc,log,frame);return; }
        if(frame%10==0) {
            UUID id=mc.player.getUniqueID();mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                vanilla &= p!=null && p.getHealth()==p.getMaxHealth();
                if(frame==TOTAL) {
                    finish(mc,log,frame);
                }
            });
        }
    }
    private void finish(Minecraft mc,Logger log,int terminalFrame) {
        boolean pass=fireEntries>=2 && minus10>20 && recovered && minus64>10 && minimum[2]<0x880 && minimum[2]>0xFF && minimum[3]>0xFF && lavaEntries>=1 && lavaRecovered && death && vanilla;
        String result="{\"passed\":"+pass+",\"hazards\":true,\"frames\":"+terminalFrame+",\"minimumHealth\":"+Arrays.toString(minimum)+",\"fireEntries\":"+fireEntries+",\"lavaSideEntries\":"+lavaEntries+",\"minusTenSteps\":"+minus10+",\"minus64Steps\":"+minus64+",\"lavaRecovery\":"+lavaRecovered+",\"recovery\":"+recovered+",\"nativeDeath\":"+death+",\"vanillaHealthUnchanged\":"+vanilla+",\"terrainEdits\":0,\"testOnlyHealthResets\":5,\"testOnlyRelocations\":7,\"nativeDeathEndpoint\":true,\"naturalDimension\":-1}";
        write(mc,"gameplay-result.json",result,true);log.info("MARIOCRAFT HAZARD result={}",result);GameplayProbe.complete=true;
    }
    private static void write(Minecraft mc,String file,String value,boolean replace) {
        try { Files.write(new java.io.File(mc.mcDataDir,file).toPath(),value.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,replace?StandardOpenOption.TRUNCATE_EXISTING:StandardOpenOption.APPEND); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
