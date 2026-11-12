package com.cubefury.vendingmachine.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;

/**
 * The wallet's coins: one icon and count per coin type, in columns of four. A count that includes ME coins is drawn in
 * the ME colour. Shift-clicking a coin takes that coin type out of the wallet.
 */
final class CoinRow {

    static final int HEIGHT = 55;
    static final int COLUMN_WIDTH = 40;
    private static final int ROWS = 4;
    private static final int ROW_HEIGHT = 14;
    private static final int ICON = 12;
    private static final float TEXT_SCALE = 0.8F;
    private static final int TEXT_LEFT = 14;
    private static final int TEXT_WIDTH = 21;
    private static final int ME_TEXT = 0xFF0000AA;
    private static final long BUMP_MILLIS = 200;
    private static final float BUMP = 0.1F;

    private final Font font;
    private final VendingMachineMenu menu;
    private int x;
    private int y;
    /** Per coin type: the last count drawn (-1 before the first), when it last changed, and whether it went up. */
    private final long[] lastTotal;
    private final long[] changedAt;
    private final boolean[] increased;

    CoinRow(Font font, VendingMachineMenu menu) {
        this.font = font;
        this.menu = menu;
        int coins = menu.coins()
            .size();
        lastTotal = new long[coins];
        changedAt = new long[coins];
        increased = new boolean[coins];
        Arrays.fill(lastTotal, -1);
    }

    /** Places the first coin's top-left corner. */
    void place(int left, int top) {
        x = left;
        y = top;
    }

    private int coinX(int coin) {
        return x + coin / ROWS * COLUMN_WIDTH;
    }

    private int coinY(int coin) {
        return y + coin % ROWS * ROW_HEIGHT;
    }

    void render(GuiGraphics g) {
        for (int i = 0; i < menu.coins()
            .size(); i++) {
            renderCoin(g, i);
        }
    }

    private void renderCoin(GuiGraphics g, int coin) {
        Art.scaledItem(g, stack(coin), coinX(coin), coinY(coin) + 1, ICON);
        long total = menu.walletCount(coin) + menu.meCount(coin);
        int color = menu.meCount(coin) > 0 ? ME_TEXT : Art.TEXT;
        if (total == 0) color = FastColor.ARGB32.lerp(0.2F, color, Art.PANEL_FILL);
        float scale = scale(coin, total, System.currentTimeMillis());
        // As in the original, the count is centred in a 21-pixel box that starts 14 pixels right of the coin.
        String text = readable(total);
        int left = coinX(coin) + TEXT_LEFT + Math.round((TEXT_WIDTH - font.width(text) * scale) / 2);
        Art.scaledText(g, new Art.Pen(font, scale, color), text, left, coinY(coin) + 4);
    }

    /**
     * The count's text scale. As in the original, for 200 ms after the count changes the scale moves by up to 0.1 along
     * a parabola, up when the count grew and down when it shrank; the first count drawn does not move.
     */
    private float scale(int coin, long total, long now) {
        if (total != lastTotal[coin]) {
            if (lastTotal[coin] >= 0) {
                changedAt[coin] = now;
                increased[coin] = total > lastTotal[coin];
            }
            lastTotal[coin] = total;
        }
        long elapsed = now - changedAt[coin];
        if (elapsed >= BUMP_MILLIS) return TEXT_SCALE;
        float t = (float) elapsed / BUMP_MILLIS - 0.5F;
        return TEXT_SCALE + (increased[coin] ? BUMP : -BUMP) * (1 - 4 * t * t);
    }

    /** Counts of 10 000 and more are cut to thousands, millions or billions. */
    static String readable(long amount) {
        if (amount < 10_000) return Long.toString(amount);
        if (amount < 1_000_000) return amount / 1_000 + "K";
        if (amount < 1_000_000_000) return amount / 1_000_000 + "M";
        return amount / 1_000_000_000 + "B";
    }

    private ItemStack stack(int coin) {
        return new ItemStack(
            menu.coins()
                .get(coin)
                .item());
    }

    /** The coin type under the mouse, or -1. */
    int coinAt(double mouseX, double mouseY) {
        for (int i = 0; i < menu.coins()
            .size(); i++) {
            if (mouseX >= coinX(i) && mouseX < coinX(i) + ICON
                && mouseY >= coinY(i) + 1
                && mouseY < coinY(i) + 1 + ICON) return i;
        }
        return -1;
    }

    /** The coin type a shift-click takes out of the wallet, or -1. */
    int click(double mouseX, double mouseY) {
        return Screen.hasShiftDown() ? coinAt(mouseX, mouseY) : -1;
    }

    List<Component> tooltip(int coin) {
        long me = menu.meCount(coin);
        Component value = Component.literal(menu.walletCount(coin) + " " + (me > 0 ? "(+" + me + ") " : ""))
            .append(stack(coin).getHoverName());
        return List.of(
            value,
            Component.empty(),
            Component.translatable("vendingmachine.gui.single_coin_type_eject_hint")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
