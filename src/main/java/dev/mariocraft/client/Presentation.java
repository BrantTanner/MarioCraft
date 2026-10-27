package dev.mariocraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Minecraft menus retained, with a small single-player MarioCraft entry screen. */
public final class Presentation {
    @SubscribeEvent public void connection(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent e) {
        if(dev.mariocraft.ScopeRules.enabled() && !e.getManager().isLocalChannel()) e.getManager().closeChannel(new net.minecraft.util.text.TextComponentString("MarioCraft supports singleplayer worlds."));
    }
    @SubscribeEvent public void open(GuiOpenEvent e) {
        if(!dev.mariocraft.ScopeRules.enabled()) return;
        GuiScreen g=e.getGui();
        if(g instanceof GuiMainMenu && !(g instanceof Title)) e.setGui(new Title());
        else if(g instanceof GuiContainer || g instanceof GuiMultiplayer || g instanceof net.minecraft.client.multiplayer.GuiConnecting || g instanceof GuiScreenServerList || g instanceof GuiShareToLan) e.setCanceled(true);
    }
    @SubscribeEvent public void init(GuiScreenEvent.InitGuiEvent.Post e) {
        if(!dev.mariocraft.ScopeRules.enabled()) return;
        if(e.getGui() instanceof GuiIngameMenu) e.getButtonList().removeIf(b->b.id==7 || b.id==5 || b.id==6 || b.id==12);
        if(e.getGui() instanceof GuiCreateWorld) for(GuiButton b:e.getButtonList()) if(b.id==2) { b.enabled=false;b.displayString="Game Mode: Survival"; }
    }
    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if(!dev.mariocraft.ScopeRules.enabled() || e.getGui() instanceof Title) return;
        GuiScreen g=e.getGui();
        if(g instanceof GuiWorldSelection || g instanceof GuiCreateWorld || g instanceof GuiIngameMenu || g instanceof GuiOptions || g instanceof GuiControls || g instanceof GuiScreenOptionsSounds || g instanceof net.minecraftforge.fml.client.config.GuiConfig)
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow("MarioCraft",6,6,0xFFE38C);
    }
    static final class Title extends GuiMainMenu {
        @Override public void initGui() {
            buttonList.clear();
            buttonList.add(new GuiButton(1,width/2-100,height/4+48,"Singleplayer"));
            buttonList.add(new GuiButton(0,width/2-100,height/4+72,"MarioCraft Settings"));
            buttonList.add(new GuiButton(6,width/2-100,height/4+96,"MarioCraft Controls"));
            buttonList.add(new GuiButton(4,width/2-100,height/4+132,"Quit Game"));
        }
        @Override protected void actionPerformed(GuiButton b) {
            if(b.id==1) mc.displayGuiScreen(new GuiWorldSelection(this));
            if(b.id==0) mc.displayGuiScreen(new GuiOptions(this,mc.gameSettings));
            if(b.id==6) mc.displayGuiScreen(new ControlsGuiFactory().createConfigGui(this));
            if(b.id==4) mc.shutdown();
        }
        @Override public void drawScreen(int x,int y,float partial) {
            drawDefaultBackground();
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.scale(3,3,1);
            drawCenteredString(fontRenderer,"MarioCraft",width/6,20,0xFFE38C);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
            drawCenteredString(fontRenderer,"Explore a Minecraft world as Mario",width/2,100,0xFFFFFF);
            for(GuiButton b:buttonList) b.drawButton(mc,x,y,partial);
        }
    }
    static final class UnsupportedWorld extends GuiIngameMenu {
        @Override public void initGui() { super.initGui();buttonList.removeIf(b->b.id!=1); }
        @Override public void drawScreen(int x,int y,float p) {
            super.drawScreen(x,y,p);
            drawCenteredString(fontRenderer,"MarioCraft supports Survival worlds without Hardcore",width/2,62,0xFFE38C);
            drawCenteredString(fontRenderer,"Open a supported world to play. This world's mode was preserved.",width/2,76,0xFFFFFF);
        }
    }
}
