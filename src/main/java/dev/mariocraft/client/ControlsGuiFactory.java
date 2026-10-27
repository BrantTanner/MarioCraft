package dev.mariocraft.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.common.config.ConfigElement;
import java.util.Set;
public final class ControlsGuiFactory implements IModGuiFactory {
    public void initialize(Minecraft mc) { }
    public boolean hasConfigGui() { return true; }
    public GuiScreen createConfigGui(GuiScreen parent) {
        java.util.List<net.minecraftforge.fml.client.config.IConfigElement> elements=new java.util.ArrayList<>(new ConfigElement(ControllerProfile.config.getCategory("controller")).getChildElements());
        elements.addAll(new ConfigElement(ControllerProfile.config.getCategory("presentation")).getChildElements());
        return new GuiConfig(parent,elements,"mariocraft",false,false,"MarioCraft Settings (keyboard: Options > Controls)");
    }
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
}
