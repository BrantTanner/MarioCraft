package dev.mariocraft.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/** Vanilla Controls screen provides rebinding, conflict labels, reset and options.txt persistence. */
final class MarioBindings {
    private final KeyBinding[] keys;
    MarioBindings() {
        String[] names={"forward","back","left","right","walk","intermediate","a","b","z","c_left","c_right","c_up","c_down","l","r","start"};
        int[] defaults={Keyboard.KEY_W,Keyboard.KEY_S,Keyboard.KEY_A,Keyboard.KEY_D,Keyboard.KEY_LCONTROL,Keyboard.KEY_LMENU,Keyboard.KEY_SPACE,Keyboard.KEY_J,Keyboard.KEY_LSHIFT,Keyboard.KEY_LEFT,Keyboard.KEY_RIGHT,Keyboard.KEY_UP,Keyboard.KEY_DOWN,Keyboard.KEY_Q,Keyboard.KEY_E,Keyboard.KEY_RETURN};
        keys=new KeyBinding[names.length];
        for(int i=0;i<keys.length;i++) { keys[i]=new KeyBinding("key.mariocraft."+names[i],i==15?KeyConflictContext.UNIVERSAL:KeyConflictContext.IN_GAME,defaults[i],"key.categories.mariocraft"); ClientRegistry.registerKeyBinding(keys[i]); }
    }
    boolean startDown() { return physical(keys[15]); }
    private boolean physical(KeyBinding key) {
        int code=key.getKeyCode();
        return code>0?org.lwjgl.input.Keyboard.isKeyDown(code):code<0 && code>=-100 && org.lwjgl.input.Mouse.isButtonDown(code+100);
    }
    KeyBinding binding(int i) { return keys[i]; }
    void synthetic(boolean forward,boolean a,boolean b) {
        for(KeyBinding key:keys) KeyBinding.setKeyBindState(key.getKeyCode(),false);
        KeyBinding.setKeyBindState(keys[0].getKeyCode(),forward);
        KeyBinding.setKeyBindState(keys[6].getKeyCode(),a);
        KeyBinding.setKeyBindState(keys[7].getKeyCode(),b);
    }
    private boolean down(int i) { return keys[i].isKeyDown(); }
    ControlState sample() {
        ControlState s=ControlState.keyboard(down(0),down(1),down(2),down(3),down(4),down(5),down(6),down(7),down(8));
        s.left=down(9); s.right=down(10); s.up=down(11); s.down=down(12); s.l=down(13); s.r=down(14); s.start=down(15); return s;
    }
}
