package dev.mariocraft.nativebridge;
import java.nio.file.*;
/** Synthetic native volumes, not Minecraft or new Project64 evidence. */
public final class WaterSmoke {
    private static void require(boolean v,String m) { if(!v) throw new AssertionError(m); }
    public static void main(String[] args) throws Exception {
        System.setProperty("mariocraft.waterProbe","true");
        try(MarioSession s=new MarioSession(Paths.get(System.getProperty("mariocraft.native")),MarioSession.discoverRom())) {
            s.surfaces(BoxSurfaces.build(java.util.Collections.singletonList(new int[]{-6000,-100,-6000,6000,0,6000})));
            s.create(0,100,0);Sm64Library.Inputs input=new Sm64Library.Inputs();input.camLookZ=-1;
            for(int i=0;i<60;i++) { s.waterLevel(-11000);s.tick(input); }
            // Strict entry threshold from standing floor: 100 does not enter; 101 does.
            s.waterLevel(100);s.tick(input);require((s.state.action&0x2000)==0,"Entry must be strict below waterLevel-100");
            s.waterLevel(101);s.tick(input);require((s.state.action&0x2000)!=0,"Missing water entry");
            s.waterLevel(1000);s.position(0,600,0);s.configureHealthForProbe(0x400);
            int drains=0,previous=0x400;
            for(int i=0;i<90;i++) { s.tick(input);int health=Short.toUnsignedInt(s.state.health);if(previous-health==1) drains++;previous=health; }
            require(drains==90,"Deep native drain must be -1 each step, got "+drains);
            s.position(0,920,0);s.configureHealthForProbe(0x400);
            int heals=0;previous=0x400;
            for(int i=0;i<60;i++) { s.tick(input);int health=Short.toUnsignedInt(s.state.health);if(health-previous==26) heals++;previous=health; }
            require(heals>40 && s.state.health==0x880,"Surface recovery must be +26 and cap");
            require(Math.abs(s.state.position[1]-920)<.01,"Surface clamp must be -80");
            float peak=0;
            for(int i=0;i<90;i++) { input.buttonA=(byte)(i%20<10?1:0);s.tick(input);peak=Math.max(peak,s.state.forwardVelocity); }
            require(peak>=10,"Missing researched stroke speed");
            input.buttonA=0;s.waterLevel(-11000);s.tick(input);require((s.state.action&0x2000)==0,"Dry exit must leave swim action");
            // Enter again, then relocate an already swimming Mario for deep death fixture.
            s.waterLevel(1000);s.position(0,800,0);s.tick(input);s.waterLevel(5000);s.position(0,2000,0);s.configureHealthForProbe(0x880);
            float[] vertical=new float[2];
            for(int direction=0;direction<2;direction++) {
                s.position(0,2000,0);input.stickY=direction==0?-1:1;input.buttonA=1;
                for(int i=0;i<90;i++) s.tick(input);
                vertical[direction]=s.state.velocity[1];
                input.stickY=0;input.buttonA=0;for(int i=0;i<90;i++) s.tick(input);
            }
            require(vertical[0]<0 && vertical[1]>0,"Shared forward=-Y must dive, backward=+Y must rise");
            s.position(0,2000,0);s.configureHealthForProbe(0x880);
            boolean drown=false;
            int lethalStep=0;
            for(int i=1;i<=1922;i++) { s.tick(input);if(lethalStep==0 && s.state.health==0xFF) lethalStep=i;drown|=s.state.action==0x300032C4; }
            require(lethalStep==1921,"Full-health underwater breath budget must use 1921 native steps");
            require(s.state.health==0xFF && drown,"Missing native drowning/death sentinel");
            System.out.println("WATER_NATIVE strictEntry=true deepMinusOneSteps="+drains+" surfacePlus26Steps="+heals+" cap=2176 surfaceOffset=80 strokePeak="+peak+" forwardDiveVelocity="+vertical[0]+" backwardRiseVelocity="+vertical[1]+" dryExit=true drowning=true fullHealthLethalStep="+lethalStep+" health="+s.state.health);
        }
    }
}
