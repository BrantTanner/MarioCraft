package dev.mariocraft.health;

/** Version-one semantic save contract. Native actions/timers are deliberately not persistent. */
public final class MarioLives {
    public static final int VERSION=1,DEFAULT_LIVES=4,FULL_HEALTH=0x880;
    public enum Phase { ALIVE, AWAITING_RESPAWN }
    private int lives=DEFAULT_LIVES;
    private long deaths;
    private Phase phase=Phase.ALIVE;
    public synchronized Snapshot snapshot() { return new Snapshot(lives,deaths,phase); }
    public synchronized boolean die() {
        if(phase!=Phase.ALIVE) return false;
        lives=Math.max(0,lives-1);deaths++;phase=Phase.AWAITING_RESPAWN;return true;
    }
    public synchronized boolean respawn() {
        if(phase!=Phase.AWAITING_RESPAWN) return false;
        if(lives==0) lives=DEFAULT_LIVES;
        phase=Phase.ALIVE;return true;
    }
    /** Restoring a pending death must retain its already-decremented counter. */
    public static MarioLives restore(Snapshot saved) {
        if(saved==null || saved.version!=VERSION || saved.lives<0 || saved.lives>99 || saved.deaths<0 || saved.phase==null || (saved.phase==Phase.ALIVE && saved.lives==0)) throw new IllegalArgumentException("Invalid life state");
        MarioLives result=new MarioLives();result.lives=saved.lives;result.deaths=saved.deaths;result.phase=saved.phase;return result;
    }
    public static final class Snapshot {
        public final int version,lives;
        public final long deaths;
        public final Phase phase;
        public Snapshot(int lives,long deaths,Phase phase) { this(VERSION,lives,deaths,phase); }
        public Snapshot(int version,int lives,long deaths,Phase phase) { this.version=version;this.lives=lives;this.deaths=deaths;this.phase=phase; }
    }
}
