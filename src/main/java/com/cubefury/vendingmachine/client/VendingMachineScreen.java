package com.cubefury.vendingmachine.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;

public class VendingMachineScreen extends AbstractContainerScreen<VendingMachineMenu> {

    private static final int LIST_X = 8;
    private static final int LIST_Y = 32;
    private static final int ROW_HEIGHT = 20;
    private static final int ROWS = 3;
    private static final int LIST_WIDTH = 160;

    private final List<String> categories = new ArrayList<>();
    /** Indices into {@code menu.trades()} that match the category and search text. */
    private final List<Integer> shown = new ArrayList<>();
    private int category = -1; // -1 shows every category
    private int scroll;
    private EditBox search;
    private Button categoryButton;

    public VendingMachineScreen(VendingMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = imageHeight - 94;
        for (Trade trade : menu.trades()) {
            if (!categories.contains(trade.category())) categories.add(trade.category());
        }
    }

    @Override
    protected void init() {
        super.init();
        search = addRenderableWidget(
            new EditBox(
                font,
                leftPos + LIST_X,
                topPos + 17,
                98,
                12,
                Component.translatable("vendingmachine.gui.search")));
        search.setHint(Component.translatable("vendingmachine.gui.search"));
        search.setResponder(text -> refilter());
        categoryButton = addRenderableWidget(
            Button.builder(categoryName(), b -> nextCategory())
                .bounds(leftPos + 108, topPos + 17, 60, 12)
                .build());
        addRenderableWidget(
            Button
                .builder(
                    Component.translatable("vendingmachine.gui.coin_eject"),
                    b -> pressButton(VendingMachineMenu.BUTTON_REFUND))
                .bounds(leftPos + 100, topPos + 99, 68, 12)
                .build());
        refilter();
    }

    private Component categoryName() {
        if (category < 0) return Component.translatable("vendingmachine.gui.all_categories");
        String name = categories.get(category);
        return Component.translatableWithFallback("vendingmachine.category." + name, name);
    }

    private void nextCategory() {
        category = category + 1 < categories.size() ? category + 1 : -1;
        categoryButton.setMessage(categoryName());
        refilter();
    }

    private void refilter() {
        String text = search.getValue()
            .toLowerCase(Locale.ROOT);
        shown.clear();
        List<Trade> trades = menu.trades();
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            boolean inCategory = category < 0 || trade.category()
                .equals(categories.get(category));
            if (inCategory && trade.item()
                .getHoverName()
                .getString()
                .toLowerCase(Locale.ROOT)
                .contains(text)) shown.add(i);
        }
        scroll = 0;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        g.fill(leftPos + LIST_X - 1, topPos + LIST_Y - 1, leftPos + LIST_X + LIST_WIDTH + 1,
            topPos + LIST_Y + ROWS * ROW_HEIGHT + 1, 0xFF373737);
        for (Slot slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF8B8B8B);
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < ROWS && scroll + row < shown.size(); row++) {
            Trade trade = menu.trades()
                .get(shown.get(scroll + row));
            renderRow(g, trade, topPos + LIST_Y + row * ROW_HEIGHT, row == hovered);
        }
    }

    private void renderRow(GuiGraphics g, Trade trade, int y, boolean hover) {
        int x = leftPos + LIST_X;
        boolean affordable = menu.credit() >= trade.price();
        g.fill(x, y, x + LIST_WIDTH, y + ROW_HEIGHT - 1, hover ? 0xFF5A5A5A : 0xFF474747);
        ItemStack item = trade.item();
        g.renderItem(item, x + 2, y + 1);
        g.renderItemDecorations(font, item, x + 2, y + 1);
        g.drawString(font, item.getHoverName(), x + 22, y + 6, 0xFFFFFF, false);
        String price = Integer.toString(trade.price());
        g.drawString(font, price, x + LIST_WIDTH - 4 - font.width(price), y + 6,
            affordable ? 0x80FF80 : 0xFF8080, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("vendingmachine.gui.credit"), 8, 95, 0x404040, false);
        g.drawString(font, String.valueOf(menu.credit()), 8, 104, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Keeps typed letters in the search box instead of closing the screen on the inventory key. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (button == 0 && row >= 0) {
            pressButton(VendingMachineMenu.BUTTON_FIRST_TRADE + shown.get(scroll + row));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, shown.size() - ROWS);
        scroll = Math.clamp(scroll - (long) Math.signum(scrollY), 0, max);
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (!inside(x, LIST_WIDTH) || !inside(y, ROWS * ROW_HEIGHT)) return -1;
        int row = (int) (y / ROW_HEIGHT);
        return scroll + row < shown.size() ? row : -1;
    }

    private static boolean inside(double offset, int size) {
        return offset >= 0 && offset < size;
    }

    private void pressButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
