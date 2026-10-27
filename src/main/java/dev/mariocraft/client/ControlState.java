package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;

/** Device-independent controls, also consumed by camera modes in issue 23. */
final class ControlState {
    float x,y;
    boolean a,b,z,left,right,up,down,l,r,start;
    static ControlState keyboard(boolean forward,boolean back,boolean left,boolean right,
            boolean walk,boolean intermediate,boolean a,boolean b,boolean z) {
        ControlState s=new ControlState();
        s.x=(right?1:0)-(left?1:0); s.y=(back?1:0)-(forward?1:0);
        float length=(float)Math.hypot(s.x,s.y), speed=walk?.4f:intermediate?.7f:1;
        if(length>0) { s.x=s.x/length*speed; s.y=s.y/length*speed; }
        s.a=a; s.b=b; s.z=z; return s;
    }
    boolean neutral() { return x==0 && y==0 && !a && !b && !z && !left && !right && !up && !down && !l && !r && !start; }
    void apply(Sm64Library.Inputs input) { ControllerInput.map(input,x,y,a,b,z,0); }
    /** Require a neutral sample after every context loss, so held actions never resume. */
    static final class Gate {
        private boolean armed;
        ControlState filter(ControlState sample,boolean enabled) {
            if(!enabled) { armed=false; return new ControlState(); }
            if(!armed) { armed=sample.neutral(); return new ControlState(); }
            return sample;
        }
        void reset() { armed=false; }
    }
}
