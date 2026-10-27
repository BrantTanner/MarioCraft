package dev.mariocraft.nativebridge;
import dev.mariocraft.health.MarioHealth;
import java.nio.file.*;
/** Synthetic pinned-native checks. Test resets use separate fresh contexts, never production healing. */
public final class HealthSmoke {
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path dll=Paths.get(System.getProperty("mariocraft.native"));
        for(int height:new int[]{200,1150,1200,3000,3100}) {
            try(MarioSession s=new MarioSession(dll,MarioSession.discoverRom())) {
                java.util.List<int[]> boxes=java.util.Arrays.asList(new int[]{-6000,-100,-6000,6000,0,6000},new int[]{-200,height-100,-200,200,height,100});
                SurfaceBoundary boundary=new SurfaceBoundary(BoxSurfaces.build(boxes));
                s.surfaces(boundary.at(0,height+10,0));s.create(0,height+10,0);Sm64Library.Inputs input=new Sm64Library.Inputs();input.camLookZ=-1;
                for(int i=0;i<90;i++) s.tick(input);
                for(int i=0;i<180;i++) { input.stickY=i<25?-1:0;s.surfaces(boundary.at(s.state.position[0],s.state.position[1],s.state.position[2]));s.tick(input); }
                int expected=height>3000?0x480:height>1150?0x680:0x880;
                require(Short.toUnsignedInt(s.state.health)==expected,"Fall health "+height+" got "+s.state.health);
                require(Math.abs(s.state.position[1])<1,"Fall did not land");
                System.out.println("HEALTH_NATIVE fallHeight="+height+" health="+s.state.health+" sections="+MarioHealth.sections(s.state.health));
            }
        }
        try(MarioSession s=new MarioSession(dll,MarioSession.discoverRom())) {
            floor(s);s.create(0,100,0);Sm64Library.Inputs input=new Sm64Library.Inputs();for(int i=0;i<90;i++) s.tick(input);
            s.damage(2,0,0,100);s.tick(input);
            require(s.state.health==0x840,"Damage must drain only one hurt tick immediately");
            require((s.state.action&0x20000)!=0,"Missing native hurt action");
            int protectedSteps=0;boolean timer=false;
            for(int i=0;i<180;i++) {
                if(!MarioHealth.vulnerable(s.state.health,s.state.action,s.state.invincTimer)) protectedSteps++;
                timer|=s.state.invincTimer>0;
                s.damage(2,0,0,100); // deliberate repeated requests exercise native protection
                s.tick(input);
                if(i==7) require(s.state.health==0x680,"Repeated protected hits stacked damage");
                if(i==60) break;
            }
            require(timer && protectedSteps>20,"Missing native recovery/invulnerability window");
            System.out.println("HEALTH_NATIVE protectedSteps="+protectedSteps+" timerSeen="+timer+" health="+s.state.health);
        }
        try(MarioSession s=new MarioSession(dll,MarioSession.discoverRom())) {
            floor(s);s.create(0,100,0);Sm64Library.Inputs input=new Sm64Library.Inputs();for(int i=0;i<90;i++) s.tick(input);
            s.damage(8,0,0,100);for(int i=0;i<60;i++) s.tick(input);
            require(s.state.health==0xFF,"Missing edge-of-death sentinel");
            System.out.println("HEALTH_NATIVE lethalHealth="+s.state.health+" action="+Integer.toHexString(s.state.action));
        }
    }
    private static void floor(MarioSession s) {
        Sm64Library.Surface[] surfaces=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(2);
        surfaces[0].vertices=new int[]{-6000,0,-6000,-6000,0,6000,6000,0,6000};
        surfaces[1].vertices=new int[]{-6000,0,-6000,6000,0,6000,6000,0,-6000};s.surfaces(surfaces);
    }
}
