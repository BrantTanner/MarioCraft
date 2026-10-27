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
        return new GuiConfig(parent,new ConfigElement(ControllerProfile.config.getCategory("controller")).getChildElements(),"mariocraft",false,false,"MarioCraft Controller Profile (keyboard: Options > Controls)");
    }
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
}
