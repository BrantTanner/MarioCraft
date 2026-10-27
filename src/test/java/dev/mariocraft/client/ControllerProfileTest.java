package dev.mariocraft.client;
import org.junit.Test;
import static org.junit.Assert.*;
public class ControllerProfileTest {
    @Test public void configuredAxesButtonsAndSmallDeadzoneAreRespected() {
        int oldX=ControllerProfile.x,oldY=ControllerProfile.y,oldA=ControllerProfile.a;
        float oldDeadzone=ControllerProfile.deadzone; boolean oldInvert=ControllerProfile.invertX;
        try {
            ControllerProfile.x=2; ControllerProfile.y=3; ControllerProfile.a=5;
            ControllerProfile.deadzone=.01f; ControllerProfile.invertX=true;
            ControlState s=ControllerInput.profile(new float[]{1,1,.03f,-.04f},new boolean[]{false,false,false,false,false,true});
            assertEquals(-.03f,s.x,0); assertEquals(-.04f,s.y,0); assertTrue(s.a);
            dev.mariocraft.nativebridge.Sm64Library.Inputs nativeInput=new dev.mariocraft.nativebridge.Sm64Library.Inputs();
            s.apply(nativeInput); assertEquals(-.03f,nativeInput.stickX,0);
            assertTrue(ControllerInput.profile(new float[0],new boolean[0]).neutral());
        } finally { ControllerProfile.x=oldX; ControllerProfile.y=oldY; ControllerProfile.a=oldA; ControllerProfile.deadzone=oldDeadzone; ControllerProfile.invertX=oldInvert; }
    }
    @Test public void xinputEndpointsAndButtonBitsArePreserved() {
        ControllerInput.PadState s=new ControllerInput.PadState();
        assertEquals(16,s.size());
        s.leftX=Short.MIN_VALUE; s.leftY=Short.MAX_VALUE; s.rightX=Short.MAX_VALUE; s.leftTrigger=(byte)255;
        s.buttons=(short)(0x1000|0x2000|0x0100|0x0010);
        float[] axes=ControllerInput.axes(s);
        assertEquals(-1,axes[0],0); assertEquals(-1,axes[1],0); assertEquals(1,axes[2],0); assertEquals(1,axes[4],0);
        boolean[] buttons=ControllerInput.buttons(s);
        assertTrue(buttons[0]); assertTrue(buttons[1]); assertTrue(buttons[4]); assertTrue(buttons[7]); assertFalse(buttons[2]);
    }
}
