package dev.mariocraft.client;

/** Client-owned camera, advanced once per native 30 Hz step. Coordinates are blocks. */
final class MarioCamera {
    interface Obstruction { double fraction(Point focus, Point eye); }
    static final class Point {
        final double x,y,z;
        Point(double x,double y,double z) { this.x=x;this.y=y;this.z=z; }
        Point mix(Point b,double t) { return new Point(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t); }
        double distance(Point b) { return Math.sqrt((x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z)); }
    }
    static final class View {
        final Point eye,focus;
        final float yaw,pitch;
        View(Point eye,Point focus) {
            this.eye=eye;this.focus=focus;
            double dx=focus.x-eye.x,dy=focus.y-eye.y,dz=focus.z-eye.z;
            yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
            pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        }
    }
    int mode=16;
    float controlYaw;
    double clearance=1;
    private float baseYaw,offset,inspectYaw,inspectPitch;
    private boolean upHeld,downHeld;
    private Point eye,focus,previousEye,previousFocus,anchor;
    void reset(double x,double y,double z,float yaw) {
        mode=16; baseYaw=controlYaw=yaw;offset=0;inspectYaw=yaw;inspectPitch=0;
        upHeld=downHeld=false;
        anchor=focus=new Point(x,y+1,z);eye=orbit(focus,yaw,20,4);
        previousEye=eye;previousFocus=focus;clearance=1;
    }
    private static Point orbit(Point f,double yaw,double pitch,double distance) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new Point(f.x+Math.sin(y)*Math.cos(p)*distance,f.y+Math.sin(p)*distance,f.z-Math.cos(y)*Math.cos(p)*distance);
    }
    static float delta(float from,float to) { return ((to-from+180)%360+360)%360-180; }
    /** Input handling precedes native movement; the returned basis never comes from render interpolation. */
    void controls(ControlState s,int action) {
        boolean water=(action&0x2000)!=0;
        if(water) mode=8;
        else if(mode==8) mode=16;
        if(mode==6 && ((s.down&&!downHeld)||s.a||s.b||(action&0x800)!=0)) mode=16;
        else if(mode==16 && s.up&&!upHeld && (action&0x800)==0 && s.x==0&&s.y==0) {
            mode=6;inspectYaw=controlYaw;inspectPitch=0;offset=0;
        }
        if(mode==6) {
            inspectYaw+=(s.right?3:0)-(s.left?3:0)+s.x*3;
            inspectPitch=Math.max(-60,Math.min(60,inspectPitch+s.y*3));
        } else {
            // C pulses are a temporary orbit, with settling after release (research 47).
            float target=(s.right?45:0)-(s.left?45:0);
            offset+=(target-offset)*.1f;
        }
        // L/R idle pulses had no observed mode/yaw effect in the applicable free-roam evidence.
        upHeld=s.up;downHeld=s.down;
        controlYaw=baseYaw+offset;
    }
    boolean inspecting() { return mode==6; }
    void follow(double x,double y,double z,float faceRadians,float speed,Obstruction collision) {
        previousEye=eye;previousFocus=focus;
        if(mode!=6 && Math.abs(speed)>1) baseYaw+=delta(baseYaw,(float)-Math.toDegrees(faceRadians))*.05f;
        controlYaw=baseYaw+offset;
        Point goalFocus=new Point(x,y+1,z);anchor=goalFocus;
        Point goalEye=mode==6?new Point(x,y+1.5,z):orbit(goalFocus,controlYaw,mode==8?10:20,4);
        if(mode==6) goalFocus=new Point(goalEye.x-Math.sin(Math.toRadians(inspectYaw))*4*Math.cos(Math.toRadians(inspectPitch)),goalEye.y-Math.sin(Math.toRadians(inspectPitch))*4,goalEye.z+Math.cos(Math.toRadians(inspectYaw))*4*Math.cos(Math.toRadians(inspectPitch)));
        if(mode==6) {
            eye=clearFocus(goalEye,anchor,collision);
            focus=new Point(goalFocus.x+eye.x-goalEye.x,goalFocus.y+eye.y-goalEye.y,goalFocus.z+eye.z-goalEye.z);
            clearance=1;return;
        }
        focus=new Point(focus.x+(goalFocus.x-focus.x)*.8,focus.y+(goalFocus.y-focus.y)*.3,focus.z+(goalFocus.z-focus.z)*.8);
        focus=clearFocus(focus,anchor,collision);
        Point candidate=new Point(eye.x+(goalEye.x-eye.x)*.8,eye.y+(goalEye.y-eye.y)*.3,eye.z+(goalEye.z-eye.z)*.8);
        clearance=collision.fraction(focus,candidate);
        // Bounded candidate orbit: never a full-circle best-view search, and never changes input yaw.
        if(clearance<1 && clearance>.5) {
            Point best=candidate;double bestFraction=clearance;
            for(int turn:new int[]{-15,15,-30,30}) {
                Point alternate=orbit(focus,controlYaw+turn,mode==8?10:20,4);
                double f=collision.fraction(focus,alternate);
                if(f>bestFraction) { best=alternate;bestFraction=f; }
            }
            candidate=candidate.mix(best,.05);clearance=collision.fraction(focus,candidate);
        }
        eye=focus.mix(candidate,Math.max(0,Math.min(1,clearance)));
    }
    private static Point clearFocus(Point desired,Point currentMario,MarioCamera.Obstruction collision) {
        if(collision.fraction(desired,desired)>0) return desired;
        if(collision.fraction(currentMario,currentMario)>0) return currentMario;
        // A changed solid can enclose the anchor. Bounded camera-only clearance search.
        for(double radius=.2;radius<=1.6001;radius+=.2)
            for(int[] d:new int[][]{{0,1,0},{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}) {
                Point p=new Point(currentMario.x+d[0]*radius,currentMario.y+d[1]*radius,currentMario.z+d[2]*radius);
                if(collision.fraction(p,p)>0) return p;
            }
        return currentMario; // Fully enclosed: no unbounded teleport or distant view through solids.
    }
    void pause() { previousEye=eye;previousFocus=focus; }
    View view(float alpha,Obstruction collision) {
        Point f=previousFocus.mix(focus,alpha),e=previousEye.mix(eye,alpha);
        if(mode==6) {
            Point safe=clearFocus(e,anchor,collision);
            f=new Point(f.x+safe.x-e.x,f.y+safe.y-e.y,f.z+safe.z-e.z);e=safe;
        } else f=clearFocus(f,anchor,collision);
        // Recheck the interpolated segment: interpolation must not cross a newly loaded voxel.
        if(mode!=6) e=f.mix(e,Math.max(0,Math.min(1,collision.fraction(f,e))));
        return new View(e,f);
    }
}
