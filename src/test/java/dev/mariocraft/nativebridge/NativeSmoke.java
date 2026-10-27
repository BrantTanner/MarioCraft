package dev.mariocraft.nativebridge;

import com.sun.jna.NativeLibrary;
import java.nio.file.*;

/** Executable native/ROM test, separate from asset-free unit tests. */
public final class NativeSmoke {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path dll = Paths.get(System.getProperty("mariocraft.native"));
        NativeLibrary abi = NativeLibrary.getInstance(dll.toAbsolutePath().toString());
        String[] names = {"surface", "inputs", "state", "geometry"};
        int[] javaSizes = {new Sm64Library.Surface().size(), new Sm64Library.Inputs().size(), new Sm64Library.State().size(), new Sm64Library.Geometry().size()};
        for (int i = 0; i < names.length; i++) {
            int nativeSize = abi.getFunction("mariocraft_size_" + names[i]).invokeInt(new Object[0]);
            require(nativeSize == javaSizes[i], "ABI mismatch " + names[i]);
            System.out.println("ABI " + names[i] + "=" + nativeSize);
        }
        Sm64Library.State layout = new Sm64Library.State();
        for (String field : new String[]{"action", "health", "flags", "invincTimer"}) {
            int nativeOffset = abi.getFunction("mariocraft_offset_" + field).invokeInt(new Object[0]);
            require(nativeOffset == layout.offset(field), "ABI offset mismatch " + field);
            System.out.println("ABI offset " + field + "=" + nativeOffset);
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            try (MarioSession session = new MarioSession(dll, MarioSession.discoverRom())) {
                Sm64Library.Surface[] floor = (Sm64Library.Surface[])new Sm64Library.Surface().toArray(2);
                floor[0].vertices = new int[]{-6000,0,-6000, -6000,0,6000, 6000,0,6000};
                floor[1].vertices = new int[]{-6000,0,-6000, 6000,0,6000, 6000,0,-6000};
                session.surfaces(floor);
                session.create(0, 100, 0);
                Sm64Library.Inputs input = new Sm64Library.Inputs();
                input.camLookZ = -1;
                for (int tick = 0; tick < 90; tick++) session.tick(input);
                require(Math.abs(session.state.position[1]) < 1, "Mario did not settle on the test floor");
                float startZ = session.state.position[2];
                input.stickY = -1;
                for (int tick = 0; tick < 60; tick++) session.tick(input);
                require(Math.abs(session.state.position[2] - startZ) > 500, "Forward input did not move Mario");
                input.buttonA = 1;
                session.tick(input);
                input.buttonA = 0;
                float peak = session.state.position[1];
                for (int tick = 0; tick < 60; tick++) {
                    session.tick(input);
                    peak = Math.max(peak, session.state.position[1]);
                    for (float coordinate : session.state.position) require(Float.isFinite(coordinate), "Nonfinite state");
                }
                require(peak > 50, "Jump did not leave the floor");
                require(Math.abs(session.state.position[1]) < 1, "Jump did not land");
                require(Short.toUnsignedInt(session.geometry.numTrianglesUsed) > 0, "Missing Mario geometry");
                for(float coordinate:session.geometry.position.getFloatArray(0,Short.toUnsignedInt(session.geometry.numTrianglesUsed)*9)) require(Float.isFinite(coordinate),"Nonfinite geometry");
                require(session.state.health > 0, "Unexpected death");
                System.out.println("NATIVE_SMOKE cycle=" + cycle + " peak=" + peak + " triangles=" + Short.toUnsignedInt(session.geometry.numTrianglesUsed) + " health=" + session.state.health);
            }
        }
    }
}
