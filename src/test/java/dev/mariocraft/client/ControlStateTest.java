package dev.mariocraft.client;
import org.junit.Test;
import static org.junit.Assert.*;
public class ControlStateTest {
    @Test public void keyboardMagnitudesAndModifierRelease() {
        assertEquals(1,Math.hypot(ControlState.keyboard(true,false,true,false,false,false,false,false,false).x,ControlState.keyboard(true,false,true,false,false,false,false,false,false).y),.00001);
        assertEquals(-.4f,ControlState.keyboard(true,false,false,false,true,true,true,true,true).y,0);
        assertEquals(-.7f,ControlState.keyboard(true,false,false,false,false,true,false,false,false).y,0);
        assertEquals(-1,ControlState.keyboard(true,false,false,false,false,false,false,false,false).y,0);
    }
    @Test public void contextLossAndReconnectRequireNeutralBeforeHeldActions() {
        ControlState.Gate gate=new ControlState.Gate();
        ControlState held=ControlState.keyboard(true,false,false,false,false,false,true,true,true);
        assertTrue(gate.filter(held,true).neutral());
        gate.filter(new ControlState(),true);
        assertSame(held,gate.filter(held,true));
        assertTrue(gate.filter(held,false).neutral());
        assertTrue(gate.filter(held,true).neutral());
        gate.filter(new ControlState(),true);
        assertSame(held,gate.filter(held,true));
        gate.reset(); assertTrue(gate.filter(held,true).neutral());
    }
}
