package dev.mariocraft.simulation;

/** Rational 30 Hz accumulator. Pauses discard elapsed time; stalls permit at most five steps. */
public final class FixedStepClock {
    private long last = Long.MIN_VALUE;
    private long accumulated;
    public int advance(long nowNanos, boolean paused) {
        if (last == Long.MIN_VALUE || paused) { last = nowNanos; accumulated = 0; return 0; }
        long elapsed = Math.max(0, Math.min(166666667L, nowNanos - last));
        last = nowNanos;
        accumulated += elapsed * 30;
        int steps = (int)Math.min(5, accumulated / 1000000000L);
        accumulated %= 1000000000L;
        return steps;
    }
    public float interpolation() { return accumulated / 1000000000f; }
}
