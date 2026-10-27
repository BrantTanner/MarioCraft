package dev.mariocraft.nativebridge;

import com.sun.jna.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;

/** One process-global native context. Create/tick/close on its owning simulation thread. */
public final class MarioSession implements AutoCloseable {
    public static final String ROM_SHA256 = "17ce077343c6133f8c9f2d6d6d9a4ab62c8cd2aa57c40aea1f490b4c8bb21d91";
    private static boolean active;
    private final Thread owner = Thread.currentThread();
    private final Sm64Library lib;
    private boolean closed;
    private int mario = -1;
    public final byte[] texture = new byte[704 * 64 * 4];
    public final Sm64Library.State state = new Sm64Library.State();
    public final Sm64Library.Geometry geometry = new Sm64Library.Geometry();
    private final Memory positions = new Memory(1024 * 3 * 3 * 4), normals = new Memory(1024 * 3 * 3 * 4);
    private final Memory colors = new Memory(1024 * 3 * 3 * 4), uvs = new Memory(1024 * 3 * 2 * 4);

    public static Path discoverRom() {
        String explicit = System.getProperty("mariocraft.rom");
        return explicit != null ? Paths.get(explicit) : Paths.get(System.getProperty("user.home"), "Downloads", "Super Mario 64", "Super Mario 64 (USA).z64");
    }
    public static byte[] validatedRom(Path path) throws IOException {
        if (Files.size(path) != 8388608) throw new IOException("Expected 8 MiB USA rev0 ROM");
        byte[] bytes = Files.readAllBytes(path);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b & 255));
            if (!ROM_SHA256.equals(hex.toString())) throw new IOException("ROM SHA-256 does not match the researched USA rev0 release");
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        return bytes;
    }
    public MarioSession(Path library, Path rom) throws IOException {
        byte[] bytes = validatedRom(rom);
        synchronized (MarioSession.class) {
            if (active) throw new IllegalStateException("Only one native context is supported");
            lib = (Sm64Library)Native.loadLibrary(library.toAbsolutePath().toString(), Sm64Library.class);
            if(lib.mariocraft_hazard_abi()!=1) throw new IllegalStateException("Native hazard ABI mismatch; rebuild native backend");
            lib.sm64_global_init(bytes, texture);
            active = true;
        }
        geometry.position = positions; geometry.normal = normals; geometry.color = colors; geometry.uv = uvs;
    }
    private void check() {
        if (closed || Thread.currentThread() != owner) throw new IllegalStateException("Native session is closed or called from another thread");
    }
    public void surfaces(Sm64Library.Surface[] surfaces) {
        check();
        for (int i = 0; i < surfaces.length; i++) {
            if (Pointer.nativeValue(surfaces[i].getPointer()) != Pointer.nativeValue(surfaces[0].getPointer()) + (long)i * surfaces[0].size())
                throw new IllegalArgumentException("Surfaces must use Structure.toArray for contiguous native storage");
            surfaces[i].write();
        }
        lib.sm64_static_surfaces_load(surfaces.length == 0 ? Pointer.NULL : surfaces[0].getPointer(), surfaces.length);
    }
    public void create(float x, float y, float z) {
        check();
        if (mario >= 0) throw new IllegalStateException("Mario already exists");
        mario = lib.sm64_mario_create(x, y, z);
        if (mario < 0) throw new IllegalStateException("Mario spawn requires a valid floor");
    }
    public void tick(Sm64Library.Inputs input) {
        check();
        if (mario < 0) throw new IllegalStateException("Create Mario first");
        lib.sm64_mario_tick(mario, input, state, geometry);
        int triangles = Short.toUnsignedInt(geometry.numTrianglesUsed);
        if (triangles > 1024) throw new IllegalStateException("Native geometry count exceeds capacity");
    }
    public boolean hazard(int kind,float nx,float nz) { check();return lib.mariocraft_hazard_contact(mario,kind,nx,nz)!=0; }
    public boolean attack(float x,float y,float z,float height) { check(); return lib.sm64_mario_attack(mario,x,y,z,height)!=0; }
    public void damage(int sections,float x,float y,float z) {
        check();
        if(sections<1 || sections>8) throw new IllegalArgumentException("Damage must be 1..8 sections");
        lib.sm64_mario_take_damage(mario,sections,0,x,y,z);
    }
    /** Test setup only. No production healing or reset control is exposed. */
    public void resetHealthForProbe() {
        configureHealthForProbe(0x880);
    }
    public void configureHealthForProbe(int health) {
        check();
        if(!Boolean.getBoolean("mariocraft.healthProbe") && !Boolean.getBoolean("mariocraft.combatProbe") && !Boolean.getBoolean("mariocraft.waterProbe") && !Boolean.getBoolean("mariocraft.hazardProbe") && !Boolean.getBoolean("mariocraft.respawnProbe")) throw new IllegalStateException("Health reset requires opt-in test probe");
        if(health<0x100 || health>0x880) throw new IllegalArgumentException("Test health setup range");
        lib.sm64_set_mario_health(mario,(short)health);
    }
    public void position(float x,float y,float z) { check(); lib.sm64_set_mario_position(mario,x,y,z); }
    /** Per-step local-origin surface height. Native state stores this as a signed short. */
    public void waterLevel(int level) {
        check();
        if(level<Short.MIN_VALUE || level>Short.MAX_VALUE) throw new IllegalArgumentException("Water level outside native range");
        lib.sm64_set_mario_water_level(mario,level);
    }
    @Override public void close() {
        if (closed) return;
        check();
        if (mario >= 0) lib.sm64_mario_delete(mario);
        lib.sm64_global_terminate();
        closed = true;
        synchronized (MarioSession.class) { active = false; }
    }
}
