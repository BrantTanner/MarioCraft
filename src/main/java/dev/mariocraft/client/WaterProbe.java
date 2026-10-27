package dev.mariocraft.client;

import dev.mariocraft.health.*;
import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Test-only normal-world water recipes; setup relocations/health are explicit, terrain is untouched. */
final class WaterProbe {
    static final int LENGTH=240,TOTAL=LENGTH*7;
    private static final String[] RECIPES={"shallow-ground","deep-fall-entry-stroke","surface-heal","shore-exit-return","repeat-deep-entry","surface-explosion","deep-drowning"};
    private volatile Site deep,shallow,dry,shore;
    private boolean finding;
    private int phase;
    private final int[] min=new int[7],max=new int[7],swim=new int[7],ground=new int[7],camera8=new int[7];
    private final double[] maxSpeed=new double[7],travel=new double[7];
    private double startX,startZ;
    private int lastHealth,drains,heals;
    private boolean hurt,death,vanilla=true,safeSurface,safeDeep,shoreExit,shoreReturn;
    private int previousAction;
    private ControlState input;
    private static final class Site {
        final double x,y,z,surface;
        Site(double x,double y,double z,double surface) { this.x=x;this.y=y;this.z=z;this.surface=surface; }
    }
    boolean prepare(Minecraft mc,Logger log) {
        if(!finding) {
            finding=true;UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    Site foundDeep=null,foundShallow=null,foundDry=null;
                    int sx=(int)p.posX,sz=(int)p.posZ;
                    search:for(int r=0;r<=256;r+=8) for(int dx=-r;dx<=r;dx+=4) for(int dz=-r;dz<=r;dz+=4) {
                        if(Math.max(Math.abs(dx),Math.abs(dz))!=r) continue;
                        int x=sx+dx,z=sz+dz,top=p.world.getHeight(new BlockPos(x,0,z)).getY()-1;
                        WaterVolume.Sample w=WaterVolume.sample(p.world,x+.5,top+.1,z+.5);
                        if(w.wet) {
                            if(w.surface-w.bottom<1.05 && foundShallow==null) foundShallow=new Site(x+.5,w.bottom+.02,z+.5,w.surface);
                            if(w.surface-w.bottom>=6 && foundDeep==null) {
                                boolean clear=true;
                                for(int ax=-2;ax<=2;ax++) for(int az=-2;az<=2;az++) {
                                    WaterVolume.Sample near=WaterVolume.sample(p.world,x+ax+.5,w.surface-4,z+az+.5);
                                    clear &= near.wet && near.surface-near.bottom>=5;
                                }
                                if(clear) foundDeep=new Site(x+.5,w.surface-4,z+.5,w.surface);
                            }
                        } else if(foundDry==null && p.world.getBlockState(new BlockPos(x,top,z)).isFullCube()) foundDry=new Site(x+.5,top+1.02,z+.5,0);
                        if(foundDeep!=null && foundShallow!=null && foundDry!=null) break search;
                    }
                    if(foundDeep==null || foundShallow==null || foundDry==null) throw new IllegalStateException("Natural shallow/deep/dry water sites unavailable");
                    shallow=foundShallow;dry=foundDry;deep=foundDeep;
                    bank:for(int dx=-48;dx<=48;dx++) for(int dz=-48;dz<=48;dz++) {
                        double x=shallow.x+dx,z=shallow.z+dz;
                        WaterVolume.Sample w=WaterVolume.sample(p.world,x,shallow.surface-1.8,z);
                        if(!w.wet) continue;
                        int land=p.world.getHeight(new BlockPos(x,0,z+2)).getY();
                        if(Math.abs(land-w.surface)>.3 || !p.world.getBlockState(new BlockPos(x,land-1,z+2)).isFullCube()) continue;
                        boolean clear=true;
                        for(int along=0;along<=5;along++) for(int up=0;up<3;up++) clear &= p.world.isAirBlock(new BlockPos(x,land+up,z+along));
                        if(clear) { shore=new Site(x,w.surface-.8,z,w.surface);break bank; }
                    }
                    if(shore==null) throw new IllegalStateException("Natural +Z shoreline unavailable");
                    p.connection.setPlayerLocation(shallow.x,shallow.y,shallow.z,0,0);
                    log.info("MARIOCRAFT WATER natural sites shallowDepth={} deepDepth={} terrainEdits=0",shallow.surface-shallow.y,deep.surface-deep.y);
                } catch(RuntimeException e) { log.error("Water setup",e);GameplayProbe.fail("natural water site setup"); }
            });
        }
        return deep!=null && mc.player.getDistanceSq(shallow.x,shallow.y,shallow.z)<1;
    }
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc) {
        if(frame>=TOTAL) return;
        int p=frame/LENGTH,t=frame%LENGTH;
        if(t==0) {
            phase=p;min[p]=0x880;
            session.configureHealthForProbe(p==2?0x400:p==6?0x180:0x880);
            Site s=p==0?shallow:p==3?shore:deep;
            double y=p==1?s.surface+1:p==2 || p==5?s.surface-.8:s.y;
            mc.player.setPosition(s.x,y,s.z);session.position(terrain.x(s.x),terrain.y(y),terrain.z(s.z));
            startX=s.x;startZ=s.z;lastHealth=p==2?0x400:p==6?0x180:0x880;
        }
        if(p==5 && t==45) {
            UUID id=mc.player.getUniqueID();mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                player.world.newExplosion(null,player.posX+2,player.posY+.5,player.posZ,2,false,false);
            });
        }
    }
    ControlState controls(int frame,MarioBindings bindings) {
        int p=Math.min(6,frame/LENGTH),t=frame%LENGTH;
        input=new ControlState();
        if((p==1 || p==4) && t>=60 && t<120) input.a=t%20<10;
        if(p==3) {
            if(!shoreExit) { input.y=1;input.a=t>=12 && t%20<17; }
            else if(t>=110 && !shoreReturn) input.y=1;
        }
        return input;
    }
    void observe(int frame,Sm64Library.State s,MarioCamera camera,Minecraft mc,Logger log) {
        GameplayProbe.currentFrame=frame;if(frame>TOTAL) return;
        int p=(frame-1)/LENGTH,t=(frame-1)%LENGTH,h=Short.toUnsignedInt(s.health);
        min[p]=Math.min(min[p],h);max[p]=Math.max(max[p],h);
        if((s.action&0x2000)!=0) swim[p]++;if((s.action&(0x800|0x2000))==0) ground[p]++;
        if(camera.mode==8) camera8[p]++;
        if(p==3) {
            shoreExit|=(s.action&0x2000)==0;
            shoreReturn|=shoreExit && (s.action&0x2000)!=0;
        }
        previousAction=s.action;
        maxSpeed[p]=Math.max(maxSpeed[p],s.forwardVelocity);travel[p]=Math.max(travel[p],Math.hypot(mc.player.posX-startX,mc.player.posZ-startZ));
        if(t>0) { if(h-lastHealth==-1) drains++;if(h-lastHealth==26) heals++; }lastHealth=h;
        if(p==5) hurt|=(s.action&0x20000)!=0;
        if(p==6) death|=h==0xFF && (s.action==0x300032C4 || s.action==0x300032C7);
        HealthBridge.Channel channel=HealthBridge.OWNERS.get(mc.player.getUniqueID());
        if(channel.swimRestoration!=null) { safeSurface|=channel.swimRestoration.kind==SwimRestoration.Kind.SURFACE;safeDeep|=channel.swimRestoration.kind==SwimRestoration.Kind.SUBMERGED; }
        WaterVolume.Sample w=WaterVolume.sample(mc.world,mc.player.posX,mc.player.posY,mc.player.posZ);
        write(mc,"water-trace.jsonl","{\"frame\":"+frame+",\"recipe\":\""+RECIPES[p]+"\",\"health\":"+h+",\"action\":\""+Integer.toHexString(s.action)+"\",\"y\":"+mc.player.posY+",\"surface\":"+w.surface+",\"wet\":"+w.wet+",\"speed\":"+s.forwardVelocity+",\"vy\":"+s.velocity[1]+",\"a\":"+input.a+",\"camera\":"+camera.mode+"}\n",frame==1);
        if(frame%10==0) {
            UUID id=mc.player.getUniqueID();mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                vanilla &= player!=null && player.getHealth()==player.getMaxHealth() && player.getAir()==300;
                if(frame==TOTAL) finish(mc,log);
            });
        }
    }
    private void finish(Minecraft mc,Logger log) {
        boolean passed=swim[0]==0 && ground[0]>100 && swim[1]>100 && maxSpeed[1]>=10 && travel[1]>1
            && max[2]==0x880 && shoreExit && shoreReturn && swim[4]>100 && camera8[4]>100
            && hurt && min[5]<0x880 && death && min[6]==0xFF && drains>100 && heals>10 && vanilla && safeSurface && safeDeep;
        String result="{\"passed\":"+passed+",\"water\":true,\"frames\":"+TOTAL+",\"minimumHealth\":"+Arrays.toString(min)+",\"maximumHealth\":"+Arrays.toString(max)+",\"swimFrames\":"+Arrays.toString(swim)+",\"groundFrames\":"+Arrays.toString(ground)+",\"camera8Frames\":"+Arrays.toString(camera8)+",\"maximumSpeed\":"+Arrays.toString(maxSpeed)+",\"travelBlocks\":"+Arrays.toString(travel)+",\"nativeMinusOneSteps\":"+drains+",\"nativePlus26Steps\":"+heals+",\"waterHurtAction\":"+hurt+",\"drowningDeath\":"+death+",\"vanillaHealthAndAirUnchanged\":"+vanilla+",\"safeSurface\":"+safeSurface+",\"safeSubmerged\":"+safeDeep+",\"terrainEdits\":0,\"testOnlyHealthResets\":7,\"testOnlyRelocations\":7}";
        write(mc,"gameplay-result.json",result,true);log.info("MARIOCRAFT WATER result={}",result);GameplayProbe.complete=true;
    }
    private static void write(Minecraft mc,String file,String text,boolean replace) {
        try { Files.write(new java.io.File(mc.mcDataDir,file).toPath(),text.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,replace?StandardOpenOption.TRUNCATE_EXISTING:StandardOpenOption.APPEND); }
        catch(java.io.IOException e) { throw new IllegalStateException("Water evidence write",e); }
    }
}
