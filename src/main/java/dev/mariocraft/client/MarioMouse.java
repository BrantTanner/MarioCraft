package dev.mariocraft.client;
import net.minecraft.util.MouseHelper;
final class MarioMouse extends MouseHelper {
    @Override public void mouseXYChange() { super.mouseXYChange(); deltaX=0; deltaY=0; }
}
