package dev.mariocraft.nativebridge;

import com.sun.jna.NativeLibrary;
import java.nio.file.*;

/** Executable native/ROM test, separate from asset-free unit tests. */
public final class NativeSmoke {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path dll = Paths.get(System.getProperty("mariocraft.native"));
        NativeLibrary abi = NativeLibrary.getInstance(dll.toAbsolutePath().toString());
        String[] names = {"surface", "inputs", "state", "geometry"};
        int[] javaSizes = {new Sm64Library.Surface().size(), new Sm64Library.Inputs().size(), new Sm64Library.State().size(), new Sm64Library.Geometry().size()};
        for (int i = 0; i < names.length; i++) {
            int nativeSize = abi.getFunction("mariocraft_size_" + names[i]).invokeInt(new Object[0]);
            require(nativeSize == javaSizes[i], "ABI mismatch " + names[i]);
            System.out.println("ABI " + names[i] + "=" + nativeSize);
        }
        Sm64Library.State layout = new Sm64Library.State();
        for (String field : new String[]{"action", "health", "flags", "invincTimer"}) {
            int nativeOffset = abi.getFunction("mariocraft_offset_" + field).invokeInt(new Object[0]);
            require(nativeOffset == layout.offset(field), "ABI offset mismatch " + field);
            System.out.println("ABI offset " + field + "=" + nativeOffset);
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            try (MarioSession session = new MarioSession(dll, MarioSession.discoverRom())) {
                Sm64Library.Surface[] floor = (Sm64Library.Surface[])new Sm64Library.Surface().toArray(2);
                floor[0].vertices = new int[]{-6000,0,-6000, -6000,0,6000, 6000,0,6000};
                floor[1].vertices = new int[]{-6000,0,-6000, 6000,0,6000, 6000,0,-6000};
                session.surfaces(floor);
                session.create(0, 100, 0);
                Sm64Library.Inputs input = new Sm64Library.Inputs();
                input.camLookZ = -1;
                for (int tick = 0; tick < 90; tick++) session.tick(input);
                require(Math.abs(session.state.position[1]) < 1, "Mario did not settle on the test floor");
                float startZ = session.state.position[2];
                input.stickY = -1;
                for (int tick = 0; tick < 60; tick++) session.tick(input);
                require(Math.abs(session.state.position[2] - startZ) > 500, "Forward input did not move Mario");
                input.buttonA = 1;
                session.tick(input);
                input.buttonA = 0;
                float peak = session.state.position[1];
                for (int tick = 0; tick < 60; tick++) {
                    session.tick(input);
                    peak = Math.max(peak, session.state.position[1]);
                    for (float coordinate : session.state.position) require(Float.isFinite(coordinate), "Nonfinite state");
                }
                require(peak > 50, "Jump did not leave the floor");
                require(Math.abs(session.state.position[1]) < 1, "Jump did not land");
                require(Short.toUnsignedInt(session.geometry.numTrianglesUsed) > 0, "Missing Mario geometry");
                for(float coordinate:session.geometry.position.getFloatArray(0,Short.toUnsignedInt(session.geometry.numTrianglesUsed)*9)) require(Float.isFinite(coordinate),"Nonfinite geometry");
                require(session.state.health > 0, "Unexpected death");
                System.out.println("NATIVE_SMOKE cycle=" + cycle + " peak=" + peak + " triangles=" + Short.toUnsignedInt(session.geometry.numTrianglesUsed) + " health=" + session.state.health);
            }
        }
        attackFallback(dll);
        float shortPeak=jumpControl(dll,false,false);
        float heldPeak=jumpControl(dll,true,false);
        require(heldPeak>shortPeak+100,"Held A did not preserve the higher jump arc");
        jumpControl(dll,true,true);
        System.out.println("MOVEMENT_NATIVE heldPeak="+heldPeak+" releasedPeak="+shortPeak);
    }

    /** Synthetic native regression: active kick facing miss must retain descending top fallback. */
    private static void attackFallback(Path dll) throws Exception {
        try(MarioSession session=new MarioSession(dll,MarioSession.discoverRom())) {
            Sm64Library.Surface[] floor=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(2);
            floor[0].vertices=new int[]{-6000,0,-6000,-6000,0,6000,6000,0,6000};
            floor[1].vertices=new int[]{-6000,0,-6000,6000,0,6000,6000,0,-6000};
            session.surfaces(floor);session.create(0,100,0);
            Sm64Library.Inputs input=new Sm64Library.Inputs();input.camLookZ=-1;
            for(int i=0;i<90;i++) session.tick(input);
            boolean checked=false;
            for(int frame=0;frame<50;frame++) {
                input.buttonA=(byte)(frame<6?1:0);
                input.buttonB=(byte)(frame>=9 && frame<13?1:0);
                session.tick(input);
                Sm64Library.State state=session.state;
                if(state.action==0x18008ac && (state.flags&0x200000)!=0 && state.velocity[1]<0 && state.position[1]>35) {
                    float tx=state.position[0]-(float)Math.sin(state.faceAngle)*30;
                    float ty=state.position[1]-30;
                    float tz=state.position[2]-(float)Math.cos(state.faceAngle)*30;
                    dev.mariocraft.combat.MarioCombat.Sample sample=new dev.mariocraft.combat.MarioCombat.Sample(state.action,state.flags,state.animID,state.animFrame,
                        state.position[0]/100.0,state.position[1]/100.0,state.position[2]/100.0,state.position[1]/100.0+.1,state.faceAngle,state.velocity[1],state.forwardVelocity);
                    dev.mariocraft.combat.MarioCombat.Target target=new dev.mariocraft.combat.MarioCombat.Target(tx/100.0,ty/100.0,tz/100.0,.3,.3);
                    require(dev.mariocraft.combat.MarioCombat.classify(sample,target)==dev.mariocraft.combat.MarioCombat.Attack.STOMP,"Java lost active-facing-miss aerial fallback");
                    require(session.attack(tx,ty,tz,30),"Native rejected active-facing-miss aerial fallback");
                    session.tick(new Sm64Library.Inputs());
                    require(session.state.velocity[1]>0,"Native fallback did not bounce Mario");
                    System.out.println("COMBAT_NATIVE activeFacingMissTopFallback=true bouncedVelocity="+session.state.velocity[1]);
                    checked=true;break;
                }
            }
            require(checked,"Input recipe did not reach descending active jump kick");
        }
    }

    /** Native-only flat floor: these checks are not normal-world integration evidence. */
    private static float jumpControl(Path dll,boolean hold,boolean steer) throws Exception {
        try(MarioSession session=new MarioSession(dll,MarioSession.discoverRom())) {
            Sm64Library.Surface[] floor=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(2);
            floor[0].vertices=new int[]{-6000,0,-6000,-6000,0,6000,6000,0,6000};
            floor[1].vertices=new int[]{-6000,0,-6000,6000,0,6000,6000,0,-6000};
            session.surfaces(floor); session.create(0,100,0);
            Sm64Library.Inputs input=new Sm64Library.Inputs(); input.camLookZ=-1;
            for(int frame=0;frame<60;frame++) session.tick(input);
            input.buttonA=1; session.tick(input);
            require(session.state.action==0x03000880,"A did not select normal jump");
            require(Math.abs(session.state.velocity[1]-38)<0.001,"Normal jump post-update vertical speed");
            int animation=session.state.animID;
            input.buttonA=(byte)(hold?1:0);
            float peak=session.state.position[1];
            boolean animationAdvanced=false,steered=false,landed=false;
            for(int frame=1;frame<60;frame++) {
                if(steer && frame>=5 && frame<15) input.stickX=1;
                else input.stickX=0;
                session.tick(input);
                if(frame==1 && !hold) require(Math.abs(session.state.velocity[1]-9.5f)<0.001,"Early A release must quarter upward velocity");
                peak=Math.max(peak,session.state.position[1]);
                animationAdvanced|=session.state.animID==animation && session.state.animFrame>0;
                steered|=Math.abs(session.state.velocity[0])>1;
                landed|=(session.state.action&0x800)==0;
            }
            require(animationAdvanced,"Jump animation did not advance");
            require(landed,"Controlled jump did not land");
            if(steer) require(steered && Math.abs(session.state.position[0])>10,"Airborne lateral input did not steer Mario");
            return peak;
        }
    }
}
