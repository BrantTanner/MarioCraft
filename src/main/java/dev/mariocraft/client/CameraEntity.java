package dev.mariocraft.client;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Render-only viewpoint. Never spawned, ticked or submitted as the player identity. */
final class CameraEntity extends Entity {
    CameraEntity(World world) { super(world); }
    protected void entityInit() { }
    protected void readEntityFromNBT(NBTTagCompound tag) { }
    protected void writeEntityToNBT(NBTTagCompound tag) { }
    public float getEyeHeight() { return 0; }
    void place(MarioCamera.View v) {
        setPosition(v.eye.x,v.eye.y,v.eye.z);
        prevPosX=lastTickPosX=posX;prevPosY=lastTickPosY=posY;prevPosZ=lastTickPosZ=posZ;
        rotationYaw=prevRotationYaw=v.yaw;rotationPitch=prevRotationPitch=v.pitch;
    }
}
