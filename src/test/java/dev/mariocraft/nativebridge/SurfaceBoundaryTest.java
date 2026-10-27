package dev.mariocraft.nativebridge;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class SurfaceBoundaryTest {
    @Test public void farSideOfThinSolidIsExcludedAndCached() {
        Sm64Library.Surface[] all=BoxSurfaces.build(Collections.singletonList(new int[]{-100,0,100,100,300,113}));
        for(Sm64Library.Surface s:all) {s.type=5;s.force=7;s.terrain=2;}
        SurfaceBoundary boundary=new SurfaceBoundary(all);
        Sm64Library.Surface[] front=boundary.at(0,0,0);
        assertSame(front,boundary.at(0,100,20));
        assertEquals(6,front.length); // top/bottom and the two front-facing wall triangles
        for(Sm64Library.Surface s:front) {assertEquals(5,s.type);assertEquals(7,s.force);assertEquals(2,s.terrain);}
        assertNotSame(front,boundary.at(0,0,200));
    }
    @Test public void insertedBlockChoosesSupportedBoundedExit() {
        List<int[]> boxes=Arrays.asList(new int[]{-1000,-100,-1000,1000,0,1000},new int[]{-50,0,-50,50,100,50});
        float[] result=VoxelRecovery.find(0,0,0,boxes);
        assertNotNull(result);
        assertTrue(Math.abs(result[0])>=100 || Math.abs(result[2])>=100 || result[1]>=100);
        assertTrue(Math.sqrt(result[0]*result[0]+result[1]*result[1]+result[2]*result[2])<=160);
    }
    @Test public void noClearExitFreezesRatherThanJumpingThroughDistantSolids() {
        assertNull(VoxelRecovery.find(0,0,0,Collections.singletonList(new int[]{-1000,-100,-1000,1000,1000,1000})));
    }
    @Test public void normalPoseIsUnchanged() {
        assertArrayEquals(new float[]{12,0,34},VoxelRecovery.find(12,0,34,Collections.singletonList(new int[]{-1000,-100,-1000,1000,0,1000})),0);
    }
}
