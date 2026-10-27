package dev.mariocraft.nativebridge;

import com.sun.jna.*;
import java.util.*;

/** ABI pinned to libsm64 fd11813208272b4271d92bd92feb8f3fdbe61be5, src/libsm64.h. */
public interface Sm64Library extends Library {
    void sm64_global_init(byte[] rom, byte[] texture);
    void sm64_global_terminate();
    void sm64_static_surfaces_load(Pointer surfaces, int count);
    int sm64_mario_create(float x, float y, float z);
    void sm64_mario_delete(int id);
    void sm64_mario_tick(int id, Inputs inputs, State state, Geometry geometry);
    void sm64_set_mario_position(int id, float x, float y, float z);
    void sm64_mario_take_damage(int id, int damage, int subtype, float x, float y, float z);
    void sm64_set_mario_health(int id, short health);
    byte sm64_mario_attack(int id, float x, float y, float z, float hitboxHeight);

    class Surface extends Structure {
        public short type, force, terrain;
        public int[] vertices = new int[9];
        @Override protected List<String> getFieldOrder() { return Arrays.asList("type", "force", "terrain", "vertices"); }
    }
    class Inputs extends Structure {
        public float camLookX, camLookZ, stickX, stickY;
        public byte buttonA, buttonB, buttonZ;
        @Override protected List<String> getFieldOrder() { return Arrays.asList("camLookX", "camLookZ", "stickX", "stickY", "buttonA", "buttonB", "buttonZ"); }
    }
    class State extends Structure {
        public int offset(String name) { return fieldOffset(name); }
        public float[] position = new float[3], velocity = new float[3];
        public float faceAngle, forwardVelocity;
        public short health;
        public int action, animID;
        public short animFrame;
        public int flags, particleFlags;
        public short invincTimer;
        @Override protected List<String> getFieldOrder() { return Arrays.asList("position", "velocity", "faceAngle", "forwardVelocity", "health", "action", "animID", "animFrame", "flags", "particleFlags", "invincTimer"); }
    }
    class Geometry extends Structure {
        public Pointer position, normal, color, uv;
        public short numTrianglesUsed;
        @Override protected List<String> getFieldOrder() { return Arrays.asList("position", "normal", "color", "uv", "numTrianglesUsed"); }
    }
}
