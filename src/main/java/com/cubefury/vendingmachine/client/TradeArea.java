package com.cubefury.vendingmachine.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.ItemCost;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.Currency.Denomination;
import com.mojang.datafixers.util.Either;

/**
 * The scrolling list of trades, as tiles three to a row or as one row per trade. Shift-click buys a trade and
 * ctrl-click toggles it as a favourite.
 */
final class TradeArea {

    static final int WIDTH = 162;
    private static final int LIST_WIDTH = 161;
    private static final int TILE_WIDTH = 47;
    private static final int TILE_HEIGHT = 25;
    private static final int TILE_ROW = TILE_HEIGHT + 4;
    private static final int TILES_PER_ROW = 3;
    private static final int ROW_WIDTH = 153;
    private static final int ROW_HEIGHT = 14;
    private static final int BAR_WIDTH = 4;
    private static final int PADDING = 2;
    private static final int NAME_LENGTH = 21;
    private static final float SMALL_TEXT = 0.9F;
    private static final int CAN_PAY = 0x883CFF00;
    private static final int CANNOT_PAY = 0x88333333;
    private static final int SELECTED = 0xAA039BE5;
    private static final int COUNT_TEXT = 0xFF000000;

    private final Font font;
    private final Art.Pen pen;
    private final VendingMachineMenu menu;
    private final TradeView view;
    private final IntConsumer buy;
    private List<Integer> shown = List.of();
    private int x;
    private int y;
    private int height;
    private int scroll;
    private int pressed = -1;
    private int grab = -1;

    TradeArea(Font font, VendingMachineMenu menu, TradeView view, IntConsumer buy) {
        this.font = font;
        this.pen = new Art.Pen(font, SMALL_TEXT, COUNT_TEXT);
        this.menu = menu;
        this.view = view;
        this.buy = buy;
    }

    /** Places the area; {@code listHeight} is the height of the scrolling list inside the background. */
    void place(int x, int y, int listHeight) {
        this.x = x;
        this.y = y;
        this.height = listHeight;
        scrollBy(0);
    }

    void show(List<Integer> trades) {
        if (!trades.equals(shown)) {
            shown = trades;
            scrollBy(0);
        }
    }

    void scrollToTop() {
        scroll = 0;
    }

    /** The shown trades; none while the machine is not formed, as in the original. */
    private List<Integer> listed() {
        return menu.formed() ? shown : List.of();
    }

    private boolean tiles() {
        return !ClientSettings.LIST_MODE.get();
    }

    private int rowHeight() {
        return tiles() ? TILE_ROW : ROW_HEIGHT;
    }

    private int perRow() {
        return tiles() ? TILES_PER_ROW : 1;
    }

    private int contentHeight() {
        return 2 * PADDING + (listed().size() + perRow() - 1) / perRow() * rowHeight();
    }

    private void scrollBy(int amount) {
        scroll = Mth.clamp(scroll + amount, 0, Math.max(0, contentHeight() - height));
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        Art.drawTinted(g, Art.TRADE_BACKGROUND, new Art.Box(x, y, WIDTH, height + 2));
        int hovered = tradeAt(mouseX, mouseY);
        g.enableScissor(x, y + 1, x + LIST_WIDTH, y + 1 + height);
        for (int i = 0; i < listed().size(); i++) {
            renderTrade(g, i, selected(listed().get(i), hovered));
        }
        g.disableScissor();
        if (!menu.formed()) g.drawString(
            font,
            Component.translatable("vendingmachine.gui.error.incomplete_structure"),
            x + PADDING,
            y + 1 + PADDING,
            Art.TEXT,
            false);
        Art.scrollBar(g, new Art.Box(barX(), y + 1, BAR_WIDTH, height), contentHeight(), scroll);
    }

    /** As in the original, hovering a trade selects every trade of its group. */
    private boolean selected(int index, int hovered) {
        if (hovered < 0) return false;
        int group = view.trade(hovered)
            .group();
        return index == hovered || group != Trade.NO_GROUP && view.trade(index)
            .group() == group;
    }

    private void renderTrade(GuiGraphics g, int position, boolean hovered) {
        int left = slotX(position);
        int top = slotY(position);
        if (top + rowHeight() < y || top > y + height + 1) return;
        if (tiles()) renderTile(g, listed().get(position), left, top, hovered);
        else renderRow(g, listed().get(position), left, top, hovered);
    }

    /** Draws the trade at catalogue index {@code index} as a tile. */
    private void renderTile(GuiGraphics g, int index, int left, int top, boolean hovered) {
        Trade trade = view.trade(index);
        Art.drawTinted(
            g,
            index == pressed ? Art.TILE_PRESSED : Art.TILE,
            new Art.Box(left, top, TILE_WIDTH, TILE_HEIGHT));
        if (menu.canBuy(trade)) Art.draw(g, Art.TILE_TRADEABLE, left, top);
        if (hovered) Art.draw(g, Art.TILE_SELECTED, left, top);
        g.drawString(
            font,
            " " + trade.display()
                .getCount(),
            left + 4,
            top + 9,
            COUNT_TEXT,
            false);
        g.renderItem(trade.display(), left + 26, top + 4);
        if (ClientSettings.isFavourite(trade)) favourite(g, left + 4, top + 4, 6);
    }

    /** Draws the trade at catalogue index {@code index} as a list row. */
    private void renderRow(GuiGraphics g, int index, int left, int top, boolean hovered) {
        Trade trade = view.trade(index);
        Art.drawTinted(
            g,
            index == pressed ? Art.LIST_ROW_PRESSED : Art.LIST_ROW,
            new Art.Box(left, top, ROW_WIDTH, ROW_HEIGHT));
        Art.scaledText(
            g,
            pen,
            String.valueOf(
                trade.display()
                    .getCount()),
            left + 6,
            top + 4);
        Art.scaledItem(g, trade.display(), left + 24, top + 2, 9);
        Art.scaledText(g, pen, shortName(trade.display()), left + 36, top + 4);
        g.fill(left + 1, top + 1, left + 4, top + ROW_HEIGHT - 1, menu.canBuy(trade) ? CAN_PAY : CANNOT_PAY);
        if (hovered) g.fill(left + 1, top + 1, left + 3, top + ROW_HEIGHT - 1, SELECTED);
        if (ClientSettings.isFavourite(trade)) favourite(g, left + 139, top + 2, 10);
    }

    private static String shortName(ItemStack item) {
        String name = item.getHoverName()
            .getString();
        return name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) + "..." : name;
    }

    private static void favourite(GuiGraphics g, int left, int top, int size) {
        Art.image(g, Art.FAVOURITE, new Art.Box(left, top, size, size));
    }

    private int slotX(int position) {
        return tiles() ? x + 2 * PADDING + 1 + position % TILES_PER_ROW * (TILE_WIDTH + 2 * PADDING) : x + PADDING;
    }

    private int slotY(int position) {
        int inset = tiles() ? 2 * PADDING : PADDING;
        return y + 1 + inset + position / perRow() * rowHeight() - scroll;
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y + 1 && mouseY < y + 1 + height;
    }

    /** The catalogue index of the trade under the mouse, or -1. */
    int tradeAt(double mouseX, double mouseY) {
        if (!inList(mouseX, mouseY)) return -1;
        for (int i = 0; i < listed().size(); i++) {
            if (hits(mouseX - slotX(i), mouseY - slotY(i))) return listed().get(i);
        }
        return -1;
    }

    /** Whether a point, given relative to a trade's top-left corner, lies on the trade. */
    private boolean hits(double dx, double dy) {
        int width = tiles() ? TILE_WIDTH : ROW_WIDTH;
        int tall = tiles() ? TILE_HEIGHT : ROW_HEIGHT;
        return dx >= 0 && dx < width && dy >= 0 && dy < tall;
    }

    /** Shift-click buys; ctrl-click toggles a favourite. True when the click was on a trade. */
    boolean click(double mouseX, double mouseY) {
        int trade = tradeAt(mouseX, mouseY);
        if (trade < 0) return false;
        if (Screen.hasShiftDown() == Screen.hasControlDown()) return true;
        if (Screen.hasControlDown()) {
            ClientSettings.toggleFavourite(view.trade(trade));
        } else {
            pressed = trade;
            buy.accept(trade);
        }
        return true;
    }

    void release() {
        pressed = -1;
        grab = -1;
    }

    private int barX() {
        return x + LIST_WIDTH - BAR_WIDTH;
    }

    /** A press on the scroll bar grabs the thumb; on the track the thumb jumps to the pointer first. */
    boolean pressScrollBar(double mouseX, double mouseY) {
        boolean onBar = mouseX >= barX() && mouseX < barX() + BAR_WIDTH && inList(mouseX, mouseY);
        if (!onBar || contentHeight() <= height) return false;
        grab = ScrollThumb.grab(height, contentHeight(), scroll, pointer(mouseY));
        drag(mouseY);
        return true;
    }

    /** Moves the grabbed thumb with the pointer; true while a thumb is held. */
    boolean drag(double mouseY) {
        if (grab < 0) return false;
        scroll = ScrollThumb.scrollFor(height, contentHeight(), pointer(mouseY), grab);
        return true;
    }

    private int pointer(double mouseY) {
        return (int) mouseY - y - 1;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!inList(mouseX, mouseY)) return false;
        scrollBy(-(int) Math.signum(amount) * rowHeight());
        return true;
    }

    /**
     * The trade's tooltip in the original order: every stack it gives, price as coins and items, items that are not
     * consumed, then the controls.
     */
    List<Either<FormattedText, TooltipComponent>> tooltip(Trade trade) {
        List<Either<FormattedText, TooltipComponent>> lines = new ArrayList<>();
        trade.items()
            .forEach(item -> lines.add(Either.left(nameLine(item))));
        lines.add(Either.left(Component.empty()));
        lines.add(Either.left(heading("vendingmachine.gui.required_inputs")));
        if (trade.price() > 0) {
            PriceLine.split(trade.price(), menu.coins())
                .forEach(line -> lines.add(Either.right(line)));
            smallestCoinTotal(trade.price()).ifPresent(total -> lines.add(Either.left(total)));
        }
        trade.fromItems()
            .forEach(cost -> lines.add(Either.left(costLine(cost))));
        lines.add(Either.left(Component.empty()));
        if (!trade.nonConsumedItems()
            .isEmpty()) {
            lines.add(Either.left(heading("vendingmachine.gui.nc_inputs")));
            trade.nonConsumedItems()
                .forEach(cost -> lines.add(Either.left(costLine(cost))));
            lines.add(Either.left(Component.empty()));
        }
        lines.add(Either.left(grey("vendingmachine.gui.trade_hint")));
        lines.add(Either.left(grey("vendingmachine.gui.favourite_hint")));
        return lines;
    }

    /** The count when more than 1 and the name; a custom name is italic, with the item's own name in brackets. */
    private static Component nameLine(ItemStack item) {
        MutableComponent line = Component.literal(item.getCount() > 1 ? item.getCount() + " " : "");
        Component custom = item.get(DataComponents.CUSTOM_NAME);
        if (custom == null) return line.append(item.getHoverName())
            .withStyle(ChatFormatting.AQUA);
        return line.append(
            custom.copy()
                .withStyle(ChatFormatting.ITALIC))
            .append(" (")
            .append(
                item.getItem()
                    .getName(item))
            .append(")")
            .withStyle(ChatFormatting.AQUA);
    }

    private static Component heading(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC);
    }

    /** The count and item name, and the tag that also pays, as the original's ore dictionary line. */
    private static Component costLine(ItemCost cost) {
        MutableComponent line = Component.literal(cost.count() + " ")
            .append(
                cost.display()
                    .getHoverName());
        cost.tag()
            .ifPresent(
                tag -> line.append(" (")
                    .append(Component.translatable("vendingmachine.gui.alternative_oredict"))
                    .append(
                        " " + tag.location()
                            + ")"));
        return line.withStyle(ChatFormatting.DARK_GREEN);
    }

    /** The price counted in the smallest coin, in brackets. */
    private Optional<Component> smallestCoinTotal(int price) {
        if (menu.coins()
            .isEmpty()) return Optional.empty();
        Denomination smallest = menu.coins()
            .get(
                menu.coins()
                    .size() - 1);
        return Optional.of(
            Component.literal("(" + price / smallest.value() + " ")
                .append(
                    new ItemStack(smallest.item()).getHoverName())
                .append(")")
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    private static Component grey(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.GRAY);
    }
}
