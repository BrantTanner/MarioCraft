package dev.mariocraft.client;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import net.minecraft.util.math.*;
public class CombatGeometryTest {
    @Test public void thinWallAndContainingEndpointsBlockContact() {
        Vec3d from=new Vec3d(0,.8,0),to=new Vec3d(0,.8,.6);
        assertFalse(CombatGeometry.clear(Arrays.asList(new AxisAlignedBB(-1,0,.3,1,2,.31)),from,to));
        assertFalse(CombatGeometry.clear(Arrays.asList(new AxisAlignedBB(-1,0,-.1,1,2,.1)),from,to));
        assertFalse(CombatGeometry.clear(Arrays.asList(new AxisAlignedBB(-1,0,.59,1,2,.8)),from,to));
    }
    @Test public void partialBlockClearanceAndTopSweepRespectTheFloor() {
        assertTrue(CombatGeometry.clear(Arrays.asList(new AxisAlignedBB(-1,0,.2,1,.5,.4)),new Vec3d(0,.8,0),new Vec3d(0,.8,.6)));
        assertFalse(CombatGeometry.clear(Arrays.asList(new AxisAlignedBB(-1,0,-1,1,.5,1)),new Vec3d(0,.8,0),new Vec3d(0,.2,0)));
        assertTrue(CombatGeometry.clear(Collections.emptyList(),new Vec3d(0,.8,0),new Vec3d(0,.2,0)));
    }
}
