package dev.mariocraft.combat;

import java.util.*;

/** Pure contact rules. Distances are blocks; velocities retain native units/frame. */
public final class MarioCombat {
    private MarioCombat() {}
    public enum Attack {
        PUNCH(3), KICK(4), STOMP(5), DIVE(6), GROUND_POUND(8);
        public final float damage;
        Attack(float damage) { this.damage=damage; }
    }
    public static final class Sample {
        public final int action,flags,anim,animFrame;
        public final double x,y,z,previousY,face,vy,speed;
        public Sample(int action,int flags,int anim,int animFrame,double x,double y,double z,double previousY,double face,double vy,double speed) {
            this.action=action;this.flags=flags;this.anim=anim;this.animFrame=animFrame;
            this.x=x;this.y=y;this.z=z;this.previousY=previousY;this.face=face;this.vy=vy;this.speed=speed;
        }
        public double height() { return (action&0x8000)!=0?1:1.6; }
        public boolean punchAction() { return action==0x800380 || action==0x800457 || action==0x18008ac; }
        // Keep first/second punch and their fast recovery animation in the same stage.
        public int stage() {
            if(action==0x18008ac) return 15;
            if(punchAction()) return anim==0x68 || anim==0x6a?12:anim==0x66?13:anim==0x71?14:11;
            if(action==0x8008a9 || action==0x80023c) return 20;
            if(action==0x188088a || action==0x880456) return 30;
            if(action==0x18008aa || action==0x80045a) return 40;
            return (action&0x800)!=0 && vy<0?50:0;
        }
    }
    public static final class Target {
        public final double x,y,z,radius,height;
        public Target(double x,double y,double z,double radius,double height) {
            this.x=x;this.y=y;this.z=z;this.radius=radius;this.height=height;
        }
    }
    /** Native flag windows own punch/kick timing; no animation-duration guess. */
    public static Attack classify(Sample s,Target t) {
        double dx=t.x-s.x,dz=t.z-s.z;
        if(!Double.isFinite(dx+dz+s.y+s.previousY+s.face+s.vy+s.speed+t.y+t.height+t.radius) || t.radius<=0 || t.height<=0) return null;
        double radius=.37+t.radius;
        if(dx*dx+dz*dz>radius*radius || s.y+s.height()<t.y || s.y>t.y+t.height+.25) return null;
        if(s.punchAction()) {
            double angle=Math.atan2(dx,dz)-s.face;
            angle=Math.atan2(Math.sin(angle),Math.cos(angle));
            Attack result=null;
            if((s.flags&0x100000)!=0 && Math.abs(angle)<=0x2aaa*Math.PI/32768) result=Attack.PUNCH;
            if((s.flags&0x200000)!=0 && Math.abs(angle)<=0x2aaa*Math.PI/32768) result=Attack.KICK;
            if((s.flags&0x400000)!=0 && Math.abs(angle)<=Math.PI/2) result=Attack.KICK;
            if(result!=null) return result; // Preserve the native descending aerial fallback below.
        }
        if((s.action==0x188088a || s.action==0x880456) && Math.abs(s.speed)>=26) return Attack.DIVE;
        if(s.action==0x18008aa || s.action==0x80045a) return Attack.KICK;
        boolean top=s.vy<0 && Math.max(s.y,s.previousY)>t.y && s.y>=t.y-.25 && s.previousY>=t.y+t.height-.25 && s.y<=t.y+t.height+.25;
        if(s.action==0x8008a9 || s.action==0x80023c) return top?Attack.GROUND_POUND:null;
        return (s.action&0x800)!=0 && top?Attack.STOMP:null;
    }
    /** A UUID is consumed once per stage/contact, irrespective of server damage acceptance. */
    public static final class Ledger {
        private int stage;
        private long epoch;
        private final Set<UUID> targets=new HashSet<>();
        public long advance(Sample s) {
            int next=s.stage();
            if(next!=stage) { stage=next;targets.clear();epoch++; }
            return epoch;
        }
        public boolean contains(UUID id) { return targets.contains(id); }
        public void consume(UUID id) { targets.add(id); }
        public void clear() { stage=0;targets.clear();epoch++; }
    }
}
