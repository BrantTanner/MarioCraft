package dev.mariocraft.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarioCameraTest {
    private static final MarioCamera.Obstruction CLEAR=(f,e)->1;
    private MarioCamera camera() { MarioCamera c=new MarioCamera();c.reset(0,0,0,0);return c; }
    @Test public void pulseSettlesAndShouldersDoNotInventIdleRotation() {
        MarioCamera c=camera();ControlState s=new ControlState();s.right=true;
        for(int i=0;i<15;i++) c.controls(s,0);
        assertTrue(c.controlYaw>30);s.right=false;
        for(int i=0;i<40;i++) c.controls(s,0);
        assertTrue(c.controlYaw<1);float before=c.controlYaw;s.l=s.r=true;c.controls(s,0);
        assertEquals(16,c.mode);assertTrue(c.controlYaw<=before);
    }
    @Test public void inspectionEdgesAndActionWaterContract() {
        MarioCamera c=camera();ControlState s=new ControlState();s.up=true;c.controls(s,0);
        assertTrue(c.inspecting());s.up=false;s.x=1;c.controls(s,0);assertTrue(c.inspecting());
        s.down=true;c.controls(s,0);assertEquals(16,c.mode);
        s=new ControlState();c.controls(s,0x2000);assertEquals(8,c.mode);
        s.up=true;c.controls(s,0x2000);assertEquals(8,c.mode);
        c.controls(new ControlState(),0);assertEquals(16,c.mode);
        c.controls(s,0);assertTrue(c.inspecting());c.controls(new ControlState(),0x800);assertEquals(16,c.mode);
    }
    @Test public void obstructionDoesNotChangeMovementBasisAndInterpolationRechecks() {
        MarioCamera c=camera();c.follow(0,0,0,0,0,(f,e)->.25);
        assertEquals(0,c.controlYaw,0);assertEquals(.25,c.clearance,0);
        MarioCamera.View v=c.view(.5f,(f,e)->0);
        assertEquals(0,v.eye.distance(v.focus),0);
        c.pause();MarioCamera.View a=c.view(0,CLEAR),b=c.view(1,CLEAR);
        assertEquals(0,a.eye.distance(b.eye),0);
    }
    @Test public void inspectionEyeAvoidsCeilingWithoutReplacingLookVector() {
        MarioCamera c=camera();ControlState s=new ControlState();s.up=true;c.controls(s,0);
        MarioCamera.Obstruction ceiling=(f,e)->f.y>1.3?0:1;
        c.follow(0,0,0,0,0,ceiling);
        MarioCamera.View v=c.view(1,ceiling);
        assertTrue(v.eye.y<=1.3);assertEquals(0,v.pitch,.00001);
    }
    @Test public void focusCannotSmoothThroughNewSolid() {
        MarioCamera c=camera();
        MarioCamera.Obstruction solid=(f,e)->Math.abs(f.x-.8)<.15?0:1;
        c.follow(1,0,0,0,0,solid);
        assertEquals(1,c.view(1,solid).focus.x,0);
        c.follow(1,0,0,0,0,(f,e)->f.y<1.3?0:1);
        assertTrue(c.view(.5f,(f,e)->f.y<1.3?0:1).focus.y>=1.3);
    }
    @Test public void resetDropsOldWorldPoseModeAndButtonEdges() {
        MarioCamera c=camera();ControlState s=new ControlState();s.up=true;c.controls(s,0);
        c.reset(900,100,-700,-1080);assertEquals(16,c.mode);
        assertEquals(0,c.view(0,CLEAR).eye.distance(c.view(1,CLEAR).eye),0);
        assertEquals(900,c.view(1,CLEAR).focus.x,.00001);
        c.controls(s,0);assertEquals(6,c.mode);
        assertEquals(20,MarioCamera.delta(1080,1100),0);
        assertEquals(-20,MarioCamera.delta(-1080,-1100),0);
        assertEquals(0,MarioCamera.delta(1080,-1080),0);
    }
    @Test public void followUsesWorldScaleAndSeparateRenderYaw() {
        MarioCamera c=camera();c.follow(1,2,0,(float)-Math.PI/2,10,CLEAR);
        MarioCamera.View v=c.view(1,CLEAR);
        assertEquals(.8,v.focus.x,.00001);assertEquals(1.6,v.focus.y,.00001);
        assertEquals(4.5,c.controlYaw,.00001);assertTrue(Math.abs(v.yaw-c.controlYaw)>.1);
    }
}
