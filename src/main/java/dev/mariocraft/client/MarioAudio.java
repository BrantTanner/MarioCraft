package dev.mariocraft.client;

import dev.mariocraft.nativebridge.MarioSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import paulscode.sound.*;
import javax.sound.sampled.AudioFormat;
import java.lang.reflect.Field;

/** Native synthesis stays on Mario's owner thread. Minecraft queues all playback commands. */
final class MarioAudio implements AutoCloseable {
    private static final Field MANAGER=ReflectionHelper.findField(SoundHandler.class,"sndManager","field_147694_f");
    private static final Field SYSTEM=ReflectionHelper.findField(SoundManager.class,"sndSystem","field_148620_e");
    private static final Field LOADED=ReflectionHelper.findField(SoundManager.class,"loaded","field_148617_f");
    private final String name="mariocraft-"+java.util.UUID.randomUUID();
    private SoundSystem backend;
    private final short[] pcm=new short[544*4];
    private boolean suspended,closed,failed;
    int blocks,nonzero,flushes,creates,closes,peak;
    private int ticks;
    private boolean ready() throws IllegalAccessException {
        if(closed || failed) return false;
        SoundManager manager=(SoundManager)MANAGER.get(Minecraft.getMinecraft().getSoundHandler());
        if(!Boolean.TRUE.equals(LOADED.get(manager))) return false;
        SoundSystem current=(SoundSystem)SYSTEM.get(manager);
        if(current==null) return false;
        if(current!=backend) {
            // The old SoundSystem removes its entire old generation during reload.
            backend=current;
            backend.rawDataStream(new AudioFormat(32000,16,2,true,false),true,name,0,0,0,SoundSystemConfig.ATTENUATION_NONE,0);
            creates++;
        }
        return true;
    }
    private void error(Throwable cause) {
        failed=true;
        org.apache.logging.log4j.LogManager.getLogger("mariocraft").warn("MARIOCRAFT audio unavailable; Mario simulation continues",cause);
        close();
    }
    void state(MarioSession session,boolean pause) {
        try {
            if(!ready()) return;
            float volume=Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.PLAYERS);
            boolean mute=pause || !ControllerProfile.marioSounds || Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.MASTER)==0 || volume==0;
            backend.setVolume(name,volume);
            ListenerData listener=backend.getListenerData();backend.setPosition(name,listener.position.x,listener.position.y,listener.position.z);
            if(mute && !suspended) { flush();session.resetAudio(); }
            suspended=mute;
        } catch(Exception|LinkageError cause) { error(cause); }
    }
    void tick(MarioSession session) {
        try {
            // 1088 + 1056 + 1056 frames = 3200 frames over three 30 Hz ticks.
            int frames=session.audio(pcm,(ticks++%3)==0?0:3000);
            if(!ready() || suspended || frames==0) return;
            byte[] bytes=new byte[frames*4];blocks++;boolean nz=false;
            for(int i=0;i<frames*2;i++) {
                int value=Math.abs((int)pcm[i]);peak=Math.max(peak,value);nz|=value!=0;
                bytes[i*2]=(byte)pcm[i];bytes[i*2+1]=(byte)(pcm[i]>>8);
            }
            if(nz) nonzero++;
            backend.feedRawAudioData(name,bytes);
        } catch(Exception|LinkageError cause) { error(cause); }
    }
    private void flush() { if(backend!=null) { backend.stop(name);backend.flush(name);flushes++; } }
    boolean silent() { return backend==null || !backend.playing(name); }
    float masterVolume() { return backend==null?-1:backend.getMasterVolume(); }
    @Override public void close() {
        if(closed) return;closed=true;
        try { if(backend!=null) { flush();backend.removeSource(name);closes++; } }
        catch(RuntimeException|LinkageError cause) { org.apache.logging.log4j.LogManager.getLogger("mariocraft").warn("MARIOCRAFT audio backend already unavailable during cleanup"); }
    }
}
