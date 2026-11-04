package com.cubefury.vendingmachine.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;

public class VendingMachineScreen extends AbstractContainerScreen<VendingMachineMenu> {

    private static final int LIST_X = 8;
    private static final int LIST_Y = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int ROWS = 4;
    private static final int LIST_WIDTH = 160;

    private int scroll;

    public VendingMachineScreen(VendingMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(
            Button
                .builder(
                    Component.translatable("vendingmachine.gui.coin_eject"),
                    b -> pressButton(VendingMachineMenu.BUTTON_REFUND))
                .bounds(leftPos + 100, topPos + 99, 68, 12)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        g.fill(leftPos + LIST_X - 1, topPos + LIST_Y - 1, leftPos + LIST_X + LIST_WIDTH + 1,
            topPos + LIST_Y + ROWS * ROW_HEIGHT + 1, 0xFF373737);
        for (Slot slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF8B8B8B);
        }
        List<Trade> trades = menu.trades();
        for (int row = 0; row < ROWS && scroll + row < trades.size(); row++) {
            Trade trade = trades.get(scroll + row);
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y + row * ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean affordable = menu.credit() >= trade.price();
            g.fill(x, y, x + LIST_WIDTH, y + ROW_HEIGHT - 1, hover ? 0xFF5A5A5A : 0xFF474747);
            ItemStack shown = trade.displayItem();
            g.renderItem(shown, x + 2, y + 1);
            g.renderItemDecorations(font, shown, x + 2, y + 1);
            g.drawString(font, shown.getHoverName(), x + 22, y + 6, 0xFFFFFF, false);
            String price = Integer.toString(trade.price());
            g.drawString(font, price, x + LIST_WIDTH - 4 - font.width(price), y + 6,
                affordable ? 0x80FF80 : 0xFF8080, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("vendingmachine.gui.credit", menu.credit()), 8, 101, 0x404040,
            false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (button == 0 && row >= 0) {
            pressButton(VendingMachineMenu.BUTTON_FIRST_TRADE + scroll + row);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, menu.trades()
            .size() - ROWS);
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (x < 0 || x >= LIST_WIDTH || y < 0 || y >= ROWS * ROW_HEIGHT) return -1;
        int row = (int) (y / ROW_HEIGHT);
        return scroll + row < menu.trades()
            .size() ? row : -1;
    }

    private void pressButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
