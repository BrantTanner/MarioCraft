package dev.mariocraft.health;

/** Asset-free health projection and explicit Minecraft crossover policy. */
public final class MarioHealth {
    private MarioHealth() {}
    public static int sections(int health) { return Math.max(0,Math.min(8,health >>> 8)); }
    // One Mario section per two Minecraft damage points, rounded up; bounded to eight.
    public static int damage(float amount) {
        return !Float.isFinite(amount) || amount<=0?0:Math.min(8,(int)Math.ceil(amount/2.0));
    }
    public static boolean vulnerable(int health,int action,int timer) {
        return health>=0x100 && timer==0 && (action&(0x20000|0x1000))==0;
    }
    /** Immutable post-native-step event, also the stable snapshot for subsequent slices. */
    public static final class Snapshot {
        public final long sequence;
        public final int health,sections,action,invincibility,previousHealth,previousAction;
        public final boolean changed,dead,deathTransition;
        /** Most recent Java-admitted external request, never a causal label for this event. */
        public final String lastRequestedExternalSource;
        public Snapshot(long sequence,int health,int action,int invincibility,Snapshot previous,String source) {
            this.sequence=sequence;this.health=health;this.sections=sections(health);this.action=action;this.invincibility=invincibility;
            previousHealth=previous==null?health:previous.health;previousAction=previous==null?action:previous.action;
            changed=health!=previousHealth || action!=previousAction;dead=health<0x100;
            deathTransition=dead && previous!=null && !previous.dead;lastRequestedExternalSource=source;
        }
    }
}
