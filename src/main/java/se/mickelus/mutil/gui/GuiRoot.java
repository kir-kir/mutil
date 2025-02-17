package se.mickelus.mutil.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

public class GuiRoot extends GuiElement {

    protected Minecraft mc;

    public GuiRoot(Minecraft mc) {
        super(0, 0, 0 ,0);
        this.mc = mc;
    }

    public void draw() {
        draw(new PoseStack());
    }

    public void draw(PoseStack poseStack) {
        if (isVisible()) {
            Window window = mc.getWindow();

            width = window.getGuiScaledWidth();
            height = window.getGuiScaledHeight();
            double mouseX = mc.mouseHandler.xpos() * width / window.getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * height / window.getScreenHeight();

            drawChildren(poseStack, 0, 0, width, height, (int) mouseX, (int) mouseY, 1);
        }
    }
}
