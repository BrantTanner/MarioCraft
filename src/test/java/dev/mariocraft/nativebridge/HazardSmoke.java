package dev.mariocraft.nativebridge;
import java.nio.file.*;
import java.util.*;
/** Synthetic native reaction check; not actual Minecraft or new Project64 evidence. */
public final class HazardSmoke {
    static void require(boolean v,String m) { if(!v) throw new AssertionError(m); }
    public static void main(String[] args) throws Exception {
        System.setProperty("mariocraft.hazardProbe","true");
        try(MarioSession s=new MarioSession(Paths.get(System.getProperty("mariocraft.native")),MarioSession.discoverRom())) {
            s.surfaces(BoxSurfaces.build(Collections.singletonList(new int[]{-6000,-100,-6000,6000,0,6000})));s.create(0,0,0);
            Sm64Library.Inputs input=new Sm64Library.Inputs();input.camLookZ=1;
            for(int i=0;i<60;i++) s.tick(input);
            require(s.hazard(1,0,0),"fire entry");s.tick(input);
            System.out.println("HAZARD_NATIVE fireFirst action="+Integer.toHexString(s.state.action)+" y="+s.state.position[1]+" vy="+s.state.velocity[1]+" health="+Short.toUnsignedInt(s.state.health)+" animation="+s.state.animID);
            require(s.state.action==0x010208B4 && s.state.health==0x876,"burning jump -10");
            require(!s.hazard(1,0,0),"active burning must protect entry");
            int n=1,minus10=1;int prev=s.state.health;
            while(n<240 && (s.state.action==0x010208B4 || s.state.action==0x010208B5 || s.state.action==0x00020449)) { s.tick(input);if(prev-s.state.health==10) minus10++;prev=s.state.health;n++; }
            require(n<240 && s.state.health>0xFF,"burn recovery");
            System.out.println("HAZARD_NATIVE fireRecoveryStep="+n+" minusTenSteps="+minus10+" health="+s.state.health+" action="+Integer.toHexString(s.state.action));
            s.position(0,0,0);s.resetHealthForProbe();for(int i=0;i<60;i++) s.tick(input);
            require(s.hazard(1,0,0),"repeat fire after recovery");s.tick(input);
            s.waterLevel(80);
            for(int i=0;i<100;i++) s.tick(input);
            require(s.state.health>0xFF && s.state.health>1466 && s.state.action!=0x010208B4 && s.state.action!=0x010208B5 && s.state.action!=0x00020449,"shallow water must interrupt burning before full timeout");
            s.waterLevel(-11000);s.position(0,0,0);s.resetHealthForProbe();for(int i=0;i<60;i++) s.tick(input);
            s.waterLevel(80);require(!s.hazard(1,0,0),"water extinguishes contact above 50");s.waterLevel(-11000);
            s.damage(1,0,0,100);s.tick(input);require(!s.hazard(1,0,0),"native hurt invulnerability rejects fire");
            for(int i=0;i<150;i++) s.tick(input);s.resetHealthForProbe();
            Sm64Library.Surface[] lava=BoxSurfaces.build(Collections.singletonList(new int[]{-6000,-100,-6000,6000,0,6000}));
            for(Sm64Library.Surface f:lava) if(f.vertices[1]==0 && f.vertices[4]==0 && f.vertices[7]==0) f.type=1;
            s.surfaces(lava);s.position(0,0,0);s.tick(input);
            require(s.state.action==0x010208B7 && Math.abs(s.state.position[1]-84)<.01 && Math.abs(s.state.velocity[1]-80.8)<.01,"lava exact first-step position/velocity");
            int steps=1,drains=1,bursts=1;prev=s.state.health;
            for(;steps<400 && s.state.health!=0xFF;steps++) { s.tick(input);int h=Short.toUnsignedInt(s.state.health);if(prev-h==64) drains++;if(s.state.velocity[1]>80 && steps>1) bursts++;prev=h; }
            require(s.state.health==0xFF && drains>=30 && bursts>=2,"repeated lava lethal");
            System.out.println("HAZARD_NATIVE lavaFirstY=84 lavaFirstVy=80.8 action=10208b7 queuedMinus64Steps="+drains+" bounceSamples="+bursts+" lethalStep="+steps+" health="+s.state.health+" waterContactRejected=true protectedFireRejected=true shallowWaterInterrupt=true");
        }
    }
}
