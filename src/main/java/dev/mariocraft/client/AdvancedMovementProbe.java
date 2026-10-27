package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Bounded recorded input recipes on the existing world. Never sets native action or pose. */
final class AdvancedMovementProbe {
    private static final String[] RECIPES={"crouch-crawl", "backflip", "ground-pound", "long-jump",
        "sideflip", "jump-chain", "slide-kick", "ground-dive-rollout", "air-kick", "air-dive"};
    private static final int LENGTH=180;
    private final Map<String,Set<Integer>> actions=new LinkedHashMap<>();
    private int previousAction;
    private int chain,edge=-100;
    private boolean wasAir;
    private final Path trace=new java.io.File(Minecraft.getMinecraft().mcDataDir,"movement-trace.jsonl").toPath();
    boolean done(int frame) { return frame>=RECIPES.length*LENGTH; }
    ControlState controls(int frame,MarioBindings bindings) {
        int recipe=frame/LENGTH,t=frame%LENGTH;
        if(t==0) { chain=0; edge=-100; wasAir=false; }
        boolean forward=false,back=false,a=false,b=false,z=false;
        // Every recipe begins with 40 neutral simulation frames; inputs are retained per native step.
        if(recipe==0) { z=t>=40 && t<110; forward=t>=65 && t<100; }
        if(recipe==1) { z=t>=40 && t<55; a=t>=50 && t<75; }
        if(recipe==2) { a=t>=40 && t<48; z=t>=49 && t<52; }
        if(recipe==3) { forward=t>=40 && t<90; z=t>=65 && t<69; a=t>=68 && t<78; }
        if(recipe==4) { forward=t>=40 && t<75; back=t>=75 && t<100; a=t>=77 && t<90; }
        if(recipe==5) {
            forward=t>=40 && t<150;
            boolean air=(previousAction&0x800)!=0;
            if(t==65 || (wasAir && !air && chain<3 && t>65)) { edge=t; chain++; }
            wasAir=air;
            a=t>=edge && t<edge+5;
        }
        if(recipe==6) { forward=t>=40 && t<90; z=t>=65 && t<80; b=t>=68 && t<73; }
        if(recipe==7) { forward=t>=40 && t<105; b=t>=85 && t<90; a=t>=103 && t<108; }
        if(recipe==8) { a=t>=40 && t<46; b=t>=49 && t<54; }
        if(recipe==9) { forward=t>=40 && t<105; a=t>=75 && t<81; b=t>=84 && t<89; }
        for(int i=0;i<16;i++) KeyBinding.setKeyBindState(bindings.binding(i).getKeyCode(),false);
        KeyBinding.setKeyBindState(bindings.binding(0).getKeyCode(),forward);
        KeyBinding.setKeyBindState(bindings.binding(1).getKeyCode(),back);
        KeyBinding.setKeyBindState(bindings.binding(6).getKeyCode(),a);
        KeyBinding.setKeyBindState(bindings.binding(7).getKeyCode(),b);
        KeyBinding.setKeyBindState(bindings.binding(8).getKeyCode(),z);
        return bindings.sample();
    }
    void observe(int frame,Sm64Library.State state,ControlState input) {
        if(done(frame)) return;
        String recipe=RECIPES[frame/LENGTH];
        actions.computeIfAbsent(recipe,k->new LinkedHashSet<>()).add(state.action);
        previousAction=state.action;
        String row="{\"frame\":"+frame+",\"recipe\":\""+recipe+"\",\"action\":\""+Integer.toHexString(state.action)+"\",\"x\":"+state.position[0]+",\"y\":"+state.position[1]+",\"z\":"+state.position[2]+",\"vx\":"+state.velocity[0]+",\"vy\":"+state.velocity[1]+",\"vz\":"+state.velocity[2]+",\"speed\":"+state.forwardVelocity+",\"anim\":"+state.animID+",\"animFrame\":"+state.animFrame+",\"stickX\":"+input.x+",\"stickY\":"+input.y+",\"a\":"+input.a+",\"b\":"+input.b+",\"zButton\":"+input.z+"}\n";
        try { Files.write(trace,row.getBytes(StandardCharsets.UTF_8),frame==0?new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}:new OpenOption[]{StandardOpenOption.APPEND}); }
        catch(IOException e) { throw new IllegalStateException("Movement trace write",e); }
    }
    boolean passed() {
        int[] expected={0x04008448,0x01000883,0x008008A9,0x03000888,0x01000887,0x01000882,0x018008AA,0x0188088A,0x018008AC,0x0188088A};
        for(int i=0;i<RECIPES.length;i++) if(!actions.getOrDefault(RECIPES[i],Collections.emptySet()).contains(expected[i])) return false;
        return actions.get("ground-pound").contains(0x0080023C) && actions.get("ground-dive-rollout").contains(0x010008A6);
    }
    String summary() {
        StringBuilder out=new StringBuilder("{");
        for(Map.Entry<String,Set<Integer>> entry:actions.entrySet()) {
            if(out.length()>1) out.append(',');
            out.append('"').append(entry.getKey()).append("\":[");
            boolean comma=false;
            for(int action:entry.getValue()) { if(comma) out.append(','); out.append('"').append(Integer.toHexString(action)).append('"'); comma=true; }
            out.append(']');
        }
        return out.append('}').toString();
    }
}
