package dev.mariocraft.client;
import dev.mariocraft.nativebridge.Sm64Library;
import org.junit.Test;
import static org.junit.Assert.*;
public class ControllerInputTest {
    @Test public void analogAndSimultaneousActionsReachNativeInput() {
        Sm64Library.Inputs input=new Sm64Library.Inputs();
        ControllerInput.map(input,.25f,-.5f,true,true,true);
        assertEquals(.25f,input.stickX,0); assertEquals(-.5f,input.stickY,0);
        assertEquals(1,input.buttonA); assertEquals(1,input.buttonB); assertEquals(1,input.buttonZ);
        ControllerInput.map(input,1,-1,false,false,false);
        assertEquals(1,Math.hypot(input.stickX,input.stickY),.00001);
        ControllerInput.map(input,.03f,.04f,false,false,false);
        assertEquals(0,input.stickX,0); assertEquals(0,input.stickY,0); assertEquals(0,input.buttonA);
    }
    @Test public void invalidDeviceValuesAreNeutral() {
        Sm64Library.Inputs input=new Sm64Library.Inputs();
        ControllerInput.map(input,Float.NaN,Float.POSITIVE_INFINITY,false,false,false);
        assertEquals(0,input.stickX,0); assertEquals(0,input.stickY,0);
    }
}
