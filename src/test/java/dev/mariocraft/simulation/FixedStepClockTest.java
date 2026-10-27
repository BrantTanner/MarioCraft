package dev.mariocraft.simulation;
import org.junit.Test;
import static org.junit.Assert.*;
public class FixedStepClockTest {
    @Test public void sameNumberOfStepsAcrossRefreshRates() {
        for (int hz : new int[]{30, 60, 144, 240}) {
            FixedStepClock clock = new FixedStepClock();
            clock.advance(0, false);
            int count = 0;
            for (int frame = 1; frame <= hz * 10; frame++) count += clock.advance(frame * 1000000000L / hz, false);
            assertEquals(300, count);
        }
    }
    @Test public void pauseAndStallDoNotCreateUnboundedCatchup() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, false);
        assertEquals(0, clock.advance(10000000000L, true));
        assertEquals(1, clock.advance(10033333334L, false));
        assertEquals(5, clock.advance(60000000000L, false));
    }
    @Test public void interpolationTracksRemainingFractionAndResetsOnPause() {
        FixedStepClock clock=new FixedStepClock();
        clock.advance(0,false);
        assertEquals(0,clock.advance(16666667,false));
        assertEquals(.5f,clock.interpolation(),.00001f);
        assertEquals(1,clock.advance(33333334,false));
        assertEquals(0,clock.interpolation(),.00001f);
        clock.advance(50000000,true);
        assertEquals(0,clock.interpolation(),0);
        assertEquals(1,clock.renderFraction(true),0);
    }
}
