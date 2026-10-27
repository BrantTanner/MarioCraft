package dev.mariocraft.health;
import org.junit.Test;
import static org.junit.Assert.*;
public class SwimRestorationTest {
    @Test public void permitsOnlyAliveUnprotectedSwimCandidates() {
        assertEquals(SwimRestoration.Kind.SURFACE,new SwimRestoration(1,8.6,2,true,10,0x400,0x380022C0,0).kind);
        assertEquals(SwimRestoration.Kind.SUBMERGED,new SwimRestoration(1,8,2,true,10,0x400,0x380022C0,0).kind);
        for(int[] state:new int[][]{{0xFF,0x300032C4,0},{0x400,0x300222C5,0},{0x400,0x380022C0,1},{0x400,0x300032C4,0},{0x400,0x0C400201,0}})
            assertFalse(new SwimRestoration(1,8,2,true,10,state[0],state[1],state[2]).swimmingCandidate());
        assertEquals(SwimRestoration.Kind.DRY,new SwimRestoration(1,8,2,false,0,0x880,0x0C400201,0).kind);
        assertFalse(new SwimRestoration(Double.NaN,8,2,true,10,0x880,0x380022C0,0).swimmingCandidate());
    }
}
