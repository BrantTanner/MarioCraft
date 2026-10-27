package dev.mariocraft.client;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.io.File;

/** Persistent controller profile edited using Forge's standard Mods > Config screen. */
public final class ControllerProfile {
    static Configuration config;
    static int x=0,y=1,a=0,b=1,z=4,cl=10,cr=11,cu=12,cd=13,l=8,r=5,start=7;
    static float deadzone=.08f;
    static boolean invertX,invertY;
    static int slot=-1;
    static long revision;
    static void initialize() {
        config=new Configuration(new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,"config/mariocraft-controls.cfg"));
        config.load(); reload();
    }
    static void reload() {
        String category="controller";
        slot=config.getInt("slot",category,-1,-1,3,"XInput slot 0-3; -1 selects first connected.");
        x=config.getInt("axisX",category,0,0,63,"Zero-based horizontal axis index.");
        y=config.getInt("axisY",category,1,0,63,"Zero-based vertical axis index.");
        invertX=config.getBoolean("invertX",category,false,"Invert horizontal axis.");
        invertY=config.getBoolean("invertY",category,false,"Invert vertical axis.");
        deadzone=config.getFloat("deadzone",category,.08f,0,.95f,"Radial drift cutoff, preserving magnitude above it.");
        a=button("A",0); b=button("B",1); z=button("Z",4);
        cl=button("CLeft",10); cr=button("CRight",11); cu=button("CUp",12); cd=button("CDown",13);
        l=button("L",8); r=button("R",5); start=button("Start",7);
        config.save(); revision++;
    }
    private static int button(String name,int value) { return config.getInt("button"+name,"controller",value,-1,127,"Zero-based button index; -1 disables this binding. Missing buttons are neutral."); }
    @SubscribeEvent public void changed(ConfigChangedEvent.OnConfigChangedEvent e) { if("mariocraft".equals(e.getModID())) reload(); }
}
