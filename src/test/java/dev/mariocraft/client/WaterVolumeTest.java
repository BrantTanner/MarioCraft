package dev.mariocraft.client;
import org.junit.Test;
import static org.junit.Assert.*;
public class WaterVolumeTest {
    @Test public void connectedSourceAndDryAirPocket() {
        WaterVolume.Column c=y->y>=60&&y<=63 || y>=68&&y<=70?0:-1;
        assertEquals(63+8/9.0,WaterVolume.sample(61,c).surface,1e-8);
        assertEquals(60,WaterVolume.sample(61,c).bottom,0);
        assertFalse(WaterVolume.sample(66,c).wet);
        assertFalse(WaterVolume.sample(63.9,c).wet);
    }
    @Test public void flowingFallingAndQuantizedRebase() {
        assertEquals(60+4/9.0,WaterVolume.sample(60.1,y->y==60?4:-1).surface,1e-8);
        WaterVolume.Sample falling=WaterVolume.sample(60.1,y->y==60?12:-1);
        assertEquals(60+8/9.0,falling.surface,1e-8);
        assertEquals(1289,falling.nativeLevel(new TerrainMesh(0,60,0)));
        assertEquals(-311,falling.nativeLevel(new TerrainMesh(0,64,0)));
        assertEquals(WaterVolume.DRY,WaterVolume.sample(-1,y->-1).nativeLevel(new TerrainMesh(0,0,0)));
    }
}
