package dev.mariocraft.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.concurrent.*;

/** One Minecraft save's Overworld MapStorage. NBT/storage access stays on the server thread.
 * Client publication replaces immutable values only; serialization reads that latest value even
 * after native/channel teardown. No UUID-only static registry can leak between server instances. */
public final class MarioSaveData extends WorldSavedData {
    public static final String NAME="mariocraft_players";
    private final ConcurrentMap<UUID,MarioSaveState> states=new ConcurrentHashMap<>();
    public MarioSaveData() { super(NAME); }
    public MarioSaveData(String name) { super(name); }
    public static MarioSaveData load(MinecraftServer server) {
        if(!server.isCallingFromMinecraftThread()) throw new IllegalStateException("Save storage requires server thread");
        net.minecraft.world.storage.MapStorage storage=server.getWorld(0).getMapStorage();
        MarioSaveData data=(MarioSaveData)storage.getOrLoadData(MarioSaveData.class,NAME);
        if(data==null) { data=new MarioSaveData();storage.setData(NAME,data); }
        return data;
    }
    public MarioSaveState get(UUID id) { return states.get(id); }
    public void publish(UUID id,MarioSaveState state) { states.put(id,state); }
    // MapStorage must sample publication on every Minecraft save, including pause and shutdown.
    @Override public boolean isDirty() { return true; }
    @Override public void readFromNBT(NBTTagCompound n) {
        states.clear();if(n.getInteger("version")!=1) return;
        NBTTagCompound players=n.getCompoundTag("players");
        for(String key:players.getKeySet()) try {
            MarioSaveState state=MarioSaveState.read(players.getCompoundTag(key));if(state!=null) states.put(UUID.fromString(key),state);
        } catch(IllegalArgumentException ignored) { /* Malformed owner affects no other record. */ }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n) {
        n.setInteger("version",1);NBTTagCompound players=new NBTTagCompound();
        states.forEach((id,state)->players.setTag(id.toString(),state.write()));n.setTag("players",players);return n;
    }
}
