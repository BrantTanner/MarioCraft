package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Bounded camera controls on an unedited natural bank, through actual KeyBinding samples. */
final class CameraProbe {
    private final TraversalProbe site=new TraversalProbe();
    static boolean reloaded;
    private String firstResult;
    private ControlState submitted;
    private float initialYaw;
    private double turn,settled,shoulderBefore,shoulderAfter,minFraction=1,maxYawSeparation,travel;
    private boolean inspection,returned,finite=true,inspectingStopped=true;
    private double startX,startZ,inspectX,inspectZ;
    boolean prepare(Minecraft mc,Logger log) { return reloaded || site.prepare(mc,log); }
    float yaw() { return site.camera()+180; }
    ControlState controls(int frame,MarioBindings bindings) {
        for(int i=0;i<16;i++) KeyBinding.setKeyBindState(bindings.binding(i).getKeyCode(),false);
        int[] active=frame>=30&&frame<45?new int[]{10}:frame==90?new int[]{11}:frame>=100&&frame<115?new int[]{2}:frame==130?new int[]{12}:frame>=140&&frame<145?new int[]{13}:frame>=150&&frame<155?new int[]{14}:frame>=210&&frame<240?new int[]{1}:new int[]{};
        for(int key:active) KeyBinding.setKeyBindState(bindings.binding(key).getKeyCode(),true);
        if(frame>=225&&frame<230) KeyBinding.setKeyBindState(bindings.binding(6).getKeyCode(),true);
        submitted=bindings.sample();return submitted;
    }
    void observe(int frame,Sm64Library.State state,MarioCamera camera,Minecraft mc,Logger log) {
        GameplayProbe.currentFrame=frame;
        if(reloaded) {
            if(frame==30) {
                boolean reset=camera.mode==16 && mc.getRenderViewEntity() instanceof CameraEntity;
                String result=firstResult.substring(0,firstResult.length()-1)+",\"worldReloadReset\":"+reset+"}";
                if(!reset) result=result.replace("\"passed\":true","\"passed\":false");
                try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),result.getBytes(StandardCharsets.UTF_8)); } catch(java.io.IOException e) { throw new IllegalStateException(e); }
                log.info("MARIOCRAFT CAMERA reload result {}",result);GameplayProbe.complete=true;
            }
            return;
        }
        MarioCamera.View v=camera.view(1,new VoxelCamera(mc.world));
        finite &= Double.isFinite(v.eye.x)&&Double.isFinite(v.eye.y)&&Double.isFinite(v.eye.z)&&Float.isFinite(v.yaw)&&Float.isFinite(v.pitch);
        if(camera.mode!=6) { minFraction=Math.min(minFraction,camera.clearance); maxYawSeparation=Math.max(maxYawSeparation,Math.abs(MarioCamera.delta(camera.controlYaw,v.yaw))); }
        if(frame==30) initialYaw=camera.controlYaw;
        if(frame==45) turn=Math.abs(MarioCamera.delta(initialYaw,camera.controlYaw));
        if(frame==85) settled=Math.abs(MarioCamera.delta(initialYaw,camera.controlYaw));
        if(frame==100) { inspection=camera.mode==6;inspectX=mc.player.posX;inspectZ=mc.player.posZ; }
        if(frame>=101&&frame<=125) inspectingStopped &= Math.hypot(mc.player.posX-inspectX,mc.player.posZ-inspectZ)<.01;
        if(frame==135) { returned=camera.mode==16;shoulderBefore=camera.controlYaw; }
        if(frame==160) shoulderAfter=camera.controlYaw;
        if(frame==210) { startX=mc.player.posX;startZ=mc.player.posZ; }
        if(frame>=210) travel=Math.max(travel,Math.hypot(mc.player.posX-startX,mc.player.posZ-startZ));
        String row="{\"stickX\":"+submitted.x+",\"stickY\":"+submitted.y+",\"cRight\":"+submitted.right+",\"cUp\":"+submitted.up+",\"cDown\":"+submitted.down+",\"a\":"+submitted.a+",\"frame\":"+frame+",\"mode\":"+camera.mode+",\"controlYaw\":"+camera.controlYaw+",\"renderYaw\":"+v.yaw+",\"pitch\":"+v.pitch+",\"eyeX\":"+v.eye.x+",\"eyeY\":"+v.eye.y+",\"eyeZ\":"+v.eye.z+",\"fraction\":"+camera.clearance+",\"action\":\""+Integer.toHexString(state.action)+"\"}\n";
        try { Files.write(new java.io.File(mc.mcDataDir,"camera-trace.jsonl").toPath(),row.getBytes(StandardCharsets.UTF_8),frame==1?new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}:new OpenOption[]{StandardOpenOption.APPEND}); } catch(java.io.IOException e) { throw new IllegalStateException(e); }
        if(frame==360) {
            boolean shoulders=Math.abs(shoulderAfter-shoulderBefore)<.1;
            boolean passed=finite&&turn>20&&settled<2&&inspection&&returned&&inspectingStopped&&shoulders&&minFraction<.95&&travel>1&&GameplayProbe.pauseVerified;
            String result="{\"passed\":"+passed+",\"camera\":true,\"frames\":360,\"cPulseDegrees\":"+turn+",\"settledDegrees\":"+settled+",\"inspection\":"+inspection+",\"inspectionStoppedMovement\":"+inspectingStopped+",\"returnedFreeRoam\":"+returned+",\"shouldersIdleNoChange\":"+shoulders+",\"minObstructionFraction\":"+minFraction+",\"maxControlRenderYawSeparation\":"+maxYawSeparation+",\"travelled\":"+travel+",\"finite\":"+finite+",\"pauseVerified\":"+GameplayProbe.pauseVerified+",\"terrainEdits\":0}";
            firstResult=result;
            try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),"{\"passed\":false,\"pendingWorldReload\":true}".getBytes(StandardCharsets.UTF_8)); } catch(java.io.IOException e) { throw new IllegalStateException(e); }
            log.info("MARIOCRAFT CAMERA result {}",result);GameplayProbe.complete=true;
        }
    }
}
