package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.material.Material;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Input-only long-distance test over an inspected natural ocean, no terrain/action/velocity writes. */
final class DistanceProbe {
    private volatile BlockPos site;
    private boolean requested;
    private double startX,startZ,lastZ;
    private int lastProgress,transitions,lastChunk=Integer.MIN_VALUE;
    private volatile boolean watcher=true;
    private volatile int serverSamples;
    private int samples,waterSamples,collisionSamples,naturalClient;
    private volatile int naturalServer;
    private final Set<Integer> freshChunks=new HashSet<>();
    boolean prepare(Minecraft mc,Logger log) {
        if(!requested) {
            requested=true;UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{
                try {
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    outer:for(int radius=0;radius<=3072;radius+=128) for(int x=-radius;x<=radius;x+=128) for(int z=-radius;z<=radius;z+=128) {
                        if(Math.max(Math.abs(x),Math.abs(z))!=radius) continue;
                        boolean clear=true;
                        for(int dz=0;dz<=224 && clear;dz+=8) for(int dx=-1;dx<=1;dx++) {
                            BlockPos q=new BlockPos(x+dx,62,z+dz);
                            if(p.world.getBlockState(q).getMaterial()!=Material.WATER || p.world.getBlockState(q.up(2)).getMaterial()!=Material.AIR) { clear=false;break; }
                        }
                        if(clear) { site=new BlockPos(x,62,z);break outer; }
                    }
                    if(site==null) { GameplayProbe.fail("no bounded natural ocean corridor");return; }
                    p.connection.setPlayerLocation(site.getX()+.5,62.3,site.getZ()+.5,0,0);
                    p.getServer().getPlayerList().serverUpdateMovingPlayer(p);
                    log.info("MARIOCRAFT DISTANCE natural-ocean setupOnly=true terrainEdits=0 corridorBlocks=224");
                } catch(Exception e) { log.error("Distance site",e);GameplayProbe.fail("distance setup"); }
            });
        }
        return site!=null && mc.player.getDistanceSq(site.getX()+.5,62.3,site.getZ()+.5)<1;
    }
    ControlState controls(int frame,MarioBindings bindings) {
        for(int i=0;i<16;i++) KeyBinding.setKeyBindState(bindings.binding(i).getKeyCode(),false);
        // Neutral stick keeps the native swimming pitch horizontal; A strokes propel along facing.
        KeyBinding.setKeyBindState(bindings.binding(0).getKeyCode(),false);
        KeyBinding.setKeyBindState(bindings.binding(6).getKeyCode(),frame>=45 && frame%18<9);
        return bindings.sample();
    }
    void setup(int frame,TerrainMesh mesh,Minecraft mc) {
        if(frame%30==0) { collisionSamples++;if(mesh.at(mc.player.posX,mc.player.posY,mc.player.posZ).length==0) GameplayProbe.fail("distance zero collision surfaces"); }
    }
    void observe(int frame,Sm64Library.State state,Minecraft mc,Logger log) {
        GameplayProbe.currentFrame=frame;
        if(GameplayProbe.complete) return;
        if(frame<=1) { startX=mc.player.posX;startZ=lastZ=mc.player.posZ;lastProgress=0; }
        int chunk=((int)Math.floor(mc.player.posZ))>>4;
        if(lastChunk!=Integer.MIN_VALUE && lastChunk!=chunk) transitions++;
        lastChunk=chunk;
        if(mc.player.posZ-lastZ>1) { lastZ=mc.player.posZ;lastProgress=frame; }
        if(frame%30==0) {
            if(mc.player.posZ-startZ>80 && mc.world.getBlockState(new BlockPos(mc.player.posX,62,mc.player.posZ)).getMaterial()==Material.WATER) { naturalClient++;freshChunks.add(chunk); }
            samples++;if((state.action&0x2000)!=0) waterSamples++;
            final UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()->{ EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);if(p!=null) { serverSamples++;if(p.posZ-startZ>80 && p.world.getBlockState(new BlockPos(p.posX,62,p.posZ)).getMaterial()==Material.WATER) naturalServer++;watcher &= ((net.minecraft.world.WorldServer)p.world).getPlayerChunkMap().isPlayerWatchingChunk(p,(int)Math.floor(p.posX)>>4,(int)Math.floor(p.posZ)>>4); } });
        }
        double distance=Math.hypot(mc.player.posX-startX,mc.player.posZ-startZ);
        if(frame>450 && frame-lastProgress>450) { GameplayProbe.fail("distance no input progress for 15 native seconds");return; }
        if(frame>=5400 || distance>=176) {
            boolean pass=distance>=176 && transitions>=10 && watcher && serverSamples>=10 && waterSamples>=10 && collisionSamples>=10 && naturalClient>=10 && naturalServer>=10 && freshChunks.size()>=5;
            log.info("MARIOCRAFT DISTANCE endpoints startX={} startZ={} endX={} endZ={} freshNaturalChunks={} clientNatural={} serverNatural={}",startX,startZ,mc.player.posX,mc.player.posZ,freshChunks.size(),naturalClient,naturalServer);
            String result="{\"passed\":"+pass+",\"kind\":\"distance\",\"distanceBlocks\":"+distance+",\"chunkTransitions\":"+transitions+",\"serverWatcher\":"+watcher+",\"serverSamples\":"+serverSamples+",\"collisionSamples\":"+collisionSamples+",\"waterSamples\":"+waterSamples+",\"frames\":"+frame+",\"naturalClientSamples\":"+naturalClient+",\"naturalServerSamples\":"+naturalServer+",\"freshNaturalChunks\":"+freshChunks.size()+",\"terrainEdits\":0,\"setupRelocation\":true,\"forcedActionOrVelocity\":false}";
            try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),result.getBytes(StandardCharsets.UTF_8)); } catch(Exception e) { throw new IllegalStateException(e); }
            log.info("MARIOCRAFT DISTANCE result {}",result);GameplayProbe.complete=true;
        }
    }
}
