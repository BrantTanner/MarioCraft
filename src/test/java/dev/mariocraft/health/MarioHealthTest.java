package dev.mariocraft.health;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarioHealthTest {
    @Test public void conversionIsBoundedAndExplicit() {
        assertEquals(0,MarioHealth.damage(Float.NaN));assertEquals(0,MarioHealth.damage(-1));
        assertEquals(1,MarioHealth.damage(.1f));assertEquals(1,MarioHealth.damage(2));assertEquals(2,MarioHealth.damage(3));
        assertEquals(8,MarioHealth.damage(100));
    }
    @Test public void nativeProjectionRetainsLowHealthSentinel() {
        assertEquals(8,MarioHealth.sections(0x880));assertEquals(7,MarioHealth.sections(0x7C0));
        assertEquals(1,MarioHealth.sections(0x100));assertEquals(0,MarioHealth.sections(0xFF));
    }
    @Test public void admissionUsesNativeStateAndTimer() {
        assertTrue(MarioHealth.vulnerable(0x100,0x0C400201,0));
        assertFalse(MarioHealth.vulnerable(0x880,0x20462,0));assertFalse(MarioHealth.vulnerable(0x880,0x1320,0));
        assertFalse(MarioHealth.vulnerable(0x880,0x0C400201,1));assertFalse(MarioHealth.vulnerable(0xFF,0,0));
    }
    @Test public void eventsPreserveRawHealthActionAndDeathTransition() {
        MarioHealth.Snapshot a=new MarioHealth.Snapshot(1,0x100,1,0,null,"native");
        MarioHealth.Snapshot b=new MarioHealth.Snapshot(2,0xFF,2,0,a,"mob");
        assertTrue(b.changed);assertTrue(b.deathTransition);assertEquals(0x100,b.previousHealth);assertEquals(1,b.previousAction);
        assertFalse(new MarioHealth.Snapshot(3,0xFF,2,0,b,"mob").deathTransition);
    }
}
