package dev.mariocraft.health;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarioLivesTest {
    @Test public void repeatedDeathIsLatchedAndZeroResetsOnlyOnRespawn() {
        MarioLives lives=new MarioLives();
        for(int i=1;i<=4;i++) {
            assertTrue(lives.die());assertFalse(lives.die());
            assertEquals(4-i,lives.snapshot().lives);assertEquals(i,lives.snapshot().deaths);
            lives=MarioLives.restore(lives.snapshot());assertFalse(lives.die());
            assertTrue(lives.respawn());assertFalse(lives.respawn());
            assertEquals(i==4?4:4-i,lives.snapshot().lives);
        }
        assertTrue(lives.die());assertEquals(3,lives.snapshot().lives);
    }
    @Test(expected=IllegalArgumentException.class) public void unknownVersionIsRejected() {
        MarioLives.restore(new MarioLives.Snapshot(2,4,0,MarioLives.Phase.ALIVE));
    }
    @Test(expected=IllegalArgumentException.class) public void aliveZeroIsNotASaveState() {
        MarioLives.restore(new MarioLives.Snapshot(0,4,MarioLives.Phase.ALIVE));
    }
}
