package dev.mariocraft.client;
import org.junit.Test;
import static org.junit.Assert.*;
public class HazardVolumeTest {
    @Test public void fluidHeightsAndBodyBoundaries() {
        assertEquals(8/9.0,HazardVolume.top(0,false),1e-10);
        assertEquals(8/9.0,HazardVolume.top(8,false),1e-10);
        assertEquals(1/9.0,HazardVolume.top(7,false),1e-10);
        assertEquals(1,HazardVolume.top(7,true),0);
        assertTrue(HazardVolume.overlaps(.5,.8,.5,1.6,0,0,0,8/9.0));
        assertFalse(HazardVolume.overlaps(.5,8/9.0,.5,1.6,0,0,0,8/9.0));
        assertFalse(HazardVolume.overlaps(-.37,0,.5,1.6,0,0,0,1));
        assertTrue(HazardVolume.overlaps(-.36,0,.5,1.6,0,0,0,1));
        assertTrue(HazardVolume.overlaps(.5,0,.5,1.6,0,1,0,1));
        assertFalse(HazardVolume.overlaps(.5,0,.5,1,0,1,0,1));
    }
}
