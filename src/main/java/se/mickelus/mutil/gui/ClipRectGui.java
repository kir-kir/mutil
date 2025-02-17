package se.mickelus.mutil.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

public class ClipRectGui extends GuiElement {
    public ClipRectGui(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    protected void drawChildren(PoseStack matrixStack, int refX, int refY, int screenWidth, int screenHeight, int mouseX, int mouseY, float opacity) {
        matrixStack.pushPose();
        matrixStack.translate(0.0D, 0.0D, 950.0D);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.colorMask(false, false, false, false);
        fill(matrixStack, 4680, 2260, -4680, -2260, 0x01ffffff);
        RenderSystem.colorMask(true, true, true, true);
        matrixStack.translate(0.0D, 0.0D, -950.0D);
        RenderSystem.depthFunc(518);
        fill(matrixStack, refX + width, refY + height, refX, refY, 0x01ffffff);
        RenderSystem.depthFunc(515);

        super.drawChildren(matrixStack, refX, refY, screenWidth, screenHeight, mouseX, mouseY, opacity);

        RenderSystem.depthFunc(518);
        matrixStack.translate(0.0D, 0.0D, -950.0D);
        RenderSystem.colorMask(false, false, false, false);
        fill(matrixStack, 4680, 2260, -4680, -2260, 0x01ffffff);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(515);
        matrixStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
    }

    @Override
    public void updateFocusState(int refX, int refY, int mouseX, int mouseY) {
        boolean gainFocus = mouseX >= getX() + refX
                && mouseX < getX() + refX + getWidth()
                && mouseY >= getY() + refY
                && mouseY < getY() + refY + getHeight();

        if (gainFocus != hasFocus) {
            hasFocus = gainFocus;
            if (hasFocus) {
                onFocus();
            } else {
                onBlur();
            }
        }

        if (hasFocus) {
            elements.stream()
                    .filter(GuiElement::isVisible)
                    .forEach(element -> element.updateFocusState(
                            refX + x + getXOffset(this, element.attachmentAnchor) - getXOffset(element, element.attachmentPoint),
                            refY + y + getYOffset(this, element.attachmentAnchor) - getYOffset(element, element.attachmentPoint),
                            mouseX, mouseY));
        }
    }
}
