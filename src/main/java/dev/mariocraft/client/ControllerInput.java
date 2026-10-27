package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import org.apache.logging.log4j.Logger;

/** Windows XInput sampling: stateless slot discovery supports unplug/replug without device resource leaks. */
final class ControllerInput {
    static final float DEADZONE=.08f;
    private boolean unavailable;
    static void map(Sm64Library.Inputs input, float x, float y, boolean a, boolean b, boolean z) {
        map(input,x,y,a,b,z,DEADZONE);
    }
    static void map(Sm64Library.Inputs input,float x,float y,boolean a,boolean b,boolean z,float deadzone) {
        x=Float.isFinite(x)?Math.max(-1,Math.min(1,x)):0;
        y=Float.isFinite(y)?Math.max(-1,Math.min(1,y)):0;
        float length=(float)Math.sqrt(x*x+y*y);
        // Baseline radial drift threshold, then preserve magnitude (no rescaling).
        // libsm64 directly multiplies this processed stick space by 64.
        if(length<=deadzone) { x=0; y=0; }
        if(length>1) { x/=length; y/=length; }
        input.stickX=x; input.stickY=y;
        input.buttonA=(byte)(a?1:0); input.buttonB=(byte)(b?1:0); input.buttonZ=(byte)(z?1:0);
    }
    private long revision=-1;
    private int connected=-1;
    private XInput api;
    private final ControlState.Gate gate=new ControlState.Gate();
    interface XInput extends com.sun.jna.win32.StdCallLibrary {
        int XInputGetState(int index,PadState state);
    }
    public static final class PadState extends com.sun.jna.Structure {
        public int packet; public short buttons; public byte leftTrigger,rightTrigger;
        public short leftX,leftY,rightX,rightY;
        protected java.util.List<String> getFieldOrder() { return java.util.Arrays.asList("packet","buttons","leftTrigger","rightTrigger","leftX","leftY","rightX","rightY"); }
    }
    ControlState sample(boolean focused,Logger log) {
        if(!focused) gate.reset();
        try {
            if(api==null) api=(XInput)com.sun.jna.Native.loadLibrary("XInput9_1_0",XInput.class);
            if(revision!=ControllerProfile.revision) { connected=-1; gate.reset(); revision=ControllerProfile.revision; }
            int first=ControllerProfile.slot<0?0:ControllerProfile.slot,last=ControllerProfile.slot<0?3:first;
            for(int index=first;index<=last;index++) {
                PadState pad=new PadState();
                if(api.XInputGetState(index,pad)!=0) continue;
                if(index!=connected) { connected=index; gate.reset(); log.info("MARIOCRAFT XInput controller connected slot={}",index); }
                return gate.filter(profile(axes(pad),buttons(pad)),focused);
            }
            if(connected>=0) log.info("MARIOCRAFT XInput controller disconnected slot={}",connected);
            connected=-1; gate.reset();
        } catch(Exception|LinkageError error) {
            connected=-1; gate.reset();
            if(!unavailable) log.warn("MARIOCRAFT XInput unavailable; keyboard remains active",error);
            unavailable=true;
        }
        return new ControlState();
    }
    static float[] axes(PadState s) { return new float[]{signed(s.leftX),-signed(s.leftY),signed(s.rightX),-signed(s.rightY),(s.leftTrigger&255)/255f,(s.rightTrigger&255)/255f}; }
    private static float signed(short s) { return s/(s<0?32768f:32767f); }
    static boolean[] buttons(PadState s) {
        int[] masks={0x1000,0x2000,0x4000,0x8000,0x0100,0x0200,0x0020,0x0010,0x0040,0x0080,0x0004,0x0008,0x0001,0x0002};
        boolean[] result=new boolean[masks.length];
        for(int i=0;i<result.length;i++) result[i]=(s.buttons&masks[i])!=0; return result;
    }
    static ControlState profile(float[] axes,boolean[] buttons) {
        ControlState s=new ControlState();
        float x=axis(axes,ControllerProfile.x)*(ControllerProfile.invertX?-1:1),y=axis(axes,ControllerProfile.y)*(ControllerProfile.invertY?-1:1);
        Sm64Library.Inputs mapped=new Sm64Library.Inputs();
        map(mapped,x,y,button(buttons,ControllerProfile.a),button(buttons,ControllerProfile.b),button(buttons,ControllerProfile.z),ControllerProfile.deadzone);
        s.x=mapped.stickX; s.y=mapped.stickY; s.a=mapped.buttonA!=0; s.b=mapped.buttonB!=0; s.z=mapped.buttonZ!=0;
        s.left=button(buttons,ControllerProfile.cl); s.right=button(buttons,ControllerProfile.cr); s.up=button(buttons,ControllerProfile.cu); s.down=button(buttons,ControllerProfile.cd);
        s.l=button(buttons,ControllerProfile.l); s.r=button(buttons,ControllerProfile.r); s.start=button(buttons,ControllerProfile.start); return s;
    }
    private static float axis(float[] axes,int i) { return i>=0 && i<axes.length?axes[i]:0; }
    private static boolean button(boolean[] buttons,int i) { return i>=0 && i<buttons.length && buttons[i]; }
    void suspend() { gate.reset(); }
    void close() { connected=-1; gate.reset(); }
}
