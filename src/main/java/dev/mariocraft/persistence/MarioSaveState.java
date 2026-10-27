package dev.mariocraft.persistence;

import dev.mariocraft.health.MarioLives;
import net.minecraft.nbt.NBTTagCompound;

/** Versioned semantic state only; native structures and concrete game objects never enter NBT. */
public final class MarioSaveState {
    public static final int VERSION=1;
    public final int dimension,health;
    public final double x,y,z;
    public final MarioLives.Snapshot lives;
    public MarioSaveState(int dimension,double x,double y,double z,int health,MarioLives.Snapshot lives) {
        MarioLives.restore(lives);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29999984||Math.abs(z)>29999984||Math.abs(y)>29999984
            ||health<0||health>MarioLives.FULL_HEALTH || (lives.phase==MarioLives.Phase.ALIVE && health<0x100)) throw new IllegalArgumentException("Invalid Mario save state");
        this.dimension=dimension;this.x=x;this.y=y;this.z=z;this.health=health;this.lives=lives;
    }
    public NBTTagCompound write() {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("version",VERSION);n.setInteger("dimension",dimension);
        n.setDouble("x",x);n.setDouble("y",y);n.setDouble("z",z);n.setInteger("health",health);
        n.setInteger("lifeVersion",lives.version);n.setInteger("lives",lives.lives);n.setLong("deaths",lives.deaths);n.setString("phase",lives.phase.name());return n;
    }
    public static MarioSaveState read(NBTTagCompound n) {
        // NBT getters coerce absent/wrong fields to zero. Require exact types first.
        String[] ints={"version","dimension","health","lifeVersion","lives"};
        for(String k:ints) if(!n.hasKey(k,3)) return null;
        for(String k:new String[]{"x","y","z"}) if(!n.hasKey(k,6)) return null;
        if(!n.hasKey("deaths",4)||!n.hasKey("phase",8)||n.getInteger("version")!=VERSION) return null;
        try { return new MarioSaveState(n.getInteger("dimension"),n.getDouble("x"),n.getDouble("y"),n.getDouble("z"),n.getInteger("health"),
            new MarioLives.Snapshot(n.getInteger("lifeVersion"),n.getInteger("lives"),n.getLong("deaths"),MarioLives.Phase.valueOf(n.getString("phase")))); }
        catch(IllegalArgumentException e) { return null; }
    }
}
