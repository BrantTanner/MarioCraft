package dev.mariocraft.health;

/** Safe semantic seam for later persistence. No native action/velocity/structure is restorable. */
public final class SwimRestoration {
    public enum Kind { DRY, SURFACE, SUBMERGED, UNSAFE }
    public final Kind kind;
    public final double x,y,z,surface;
    public SwimRestoration(double x,double y,double z,boolean wet,double surface,int health,int action,int invinc) {
        this.x=x;this.y=y;this.z=z;this.surface=surface;
        boolean finite=Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Double.isFinite(surface);
        kind=!finite || health<0x100 || (action&(0x1000|0x20000))!=0 || invinc!=0?Kind.UNSAFE:
            !wet?Kind.DRY:(action&0x2000)==0?Kind.UNSAFE:y>=surface-1.4?Kind.SURFACE:Kind.SUBMERGED;
    }
    /** Caller must revalidate world, connected water and clear body at the anchor before creating Mario. */
    public boolean swimmingCandidate() { return kind==Kind.SURFACE || kind==Kind.SUBMERGED; }
}
