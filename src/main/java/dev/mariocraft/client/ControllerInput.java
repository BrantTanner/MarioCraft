package dev.mariocraft.client;

import dev.mariocraft.nativebridge.Sm64Library;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Controllers;
import org.apache.logging.log4j.Logger;

/** Baseline LWJGL/JInput gamepad path; configurable device profiles belong to issue 20. */
final class ControllerInput {
    static final float DEADZONE=.08f;
    private boolean initialized, unavailable;
    private String loggedDevice;
    static void map(Sm64Library.Inputs input, float x, float y, boolean a, boolean b, boolean z) {
        x=Float.isFinite(x)?Math.max(-1,Math.min(1,x)):0;
        y=Float.isFinite(y)?Math.max(-1,Math.min(1,y)):0;
        float length=(float)Math.sqrt(x*x+y*y);
        // Baseline radial drift threshold, then preserve magnitude (no rescaling).
        // libsm64 directly multiplies this processed stick space by 64.
        if(length<=DEADZONE) { x=0; y=0; }
        if(length>1) { x/=length; y/=length; }
        input.stickX=x; input.stickY=y;
        input.buttonA=(byte)(a?1:0); input.buttonB=(byte)(b?1:0); input.buttonZ=(byte)(z?1:0);
    }
    boolean poll(Sm64Library.Inputs input, boolean focused, Logger log) {
        if(unavailable) return false;
        try {
            if(!initialized) { Controllers.create(); initialized=true; }
            Controllers.poll();
            for(int i=0;i<Controllers.getControllerCount();i++) {
                Controller pad=Controllers.getController(i);
                if(pad.getAxisCount()<2 || pad.getButtonCount()<2) continue;
                if(!pad.getName().equals(loggedDevice)) {
                    loggedDevice=pad.getName();
                    for(int axis=0;axis<pad.getAxisCount();axis++) pad.setDeadZone(axis,0);
                    log.info("MARIOCRAFT controller={} X/Y axes, buttons 0=A 1=B 4=Z",loggedDevice);
                }
                boolean active=Math.hypot(pad.getXAxisValue(),pad.getYAxisValue())>DEADZONE || pad.isButtonPressed(0) || pad.isButtonPressed(1) || (pad.getButtonCount()>4 && pad.isButtonPressed(4));
                if(focused && active) map(input,pad.getXAxisValue(),pad.getYAxisValue(),pad.isButtonPressed(0),pad.isButtonPressed(1),pad.getButtonCount()>4 && pad.isButtonPressed(4));
                return focused && active;
            }
        } catch(Exception|LinkageError error) { unavailable=true; log.warn("MARIOCRAFT controller unavailable; keyboard remains active",error); }
        return false;
    }
}
