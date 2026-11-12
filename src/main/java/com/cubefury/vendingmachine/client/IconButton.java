package com.cubefury.vendingmachine.client;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A square raised button with an icon, as the original's option and eject buttons. */
class IconButton extends AbstractButton {

    private final Supplier<Art.Texture> icon;
    private final int iconSize;
    private final Supplier<Component> tooltip;
    private final Runnable action;

    IconButton(int size, int iconSize, Supplier<Art.Texture> icon, Supplier<Component> tooltip, Runnable action) {
        super(0, 0, size, size, Component.empty());
        this.icon = icon;
        this.iconSize = iconSize;
        this.tooltip = tooltip;
        this.action = action;
        updateTooltip();
    }

    @Override
    public void onPress() {
        action.run();
        updateTooltip();
    }

    /** Reads the tooltip again, for a tooltip that shows something other than this button changes. */
    void updateTooltip() {
        Component text = tooltip.get();
        setTooltip(text == null ? null : Tooltip.create(text));
        setMessage(text == null ? Component.empty() : text);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Art.button(g, getX(), getY(), width, isHovered());
        int inset = (width - iconSize) / 2;
        Art.image(g, icon.get(), new Art.Box(getX() + inset, getY() + inset, iconSize, iconSize));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
