package com.cubefury.vendingmachine.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.util.Currency;
import com.cubefury.vendingmachine.util.Currency.Denomination;

/** One tooltip line of a price: the amount, the coin's icon and the coin's name, in dark green. */
record PriceLine(long amount, ItemStack coin) implements TooltipComponent {

    private static final int ICON = 12;

    /** {@code price} as the fewest coins, largest first. */
    static List<PriceLine> split(long price, List<Denomination> coins) {
        List<PriceLine> lines = new ArrayList<>();
        long[] counts = Currency.split(price, coins);
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) lines.add(new PriceLine(counts[i], new ItemStack(coins.get(i)
                .item())));
        }
        return lines;
    }

    static final class Client implements ClientTooltipComponent {

        private final Component amount;
        private final Component name;
        private final ItemStack coin;

        Client(PriceLine line) {
            amount = Component.literal(line.amount() + " ")
                .withStyle(ChatFormatting.DARK_GREEN);
            name = Component.literal(" ")
                .append(line.coin()
                    .getHoverName())
                .withStyle(ChatFormatting.DARK_GREEN);
            coin = line.coin();
        }

        @Override
        public int getHeight() {
            return ICON + 1;
        }

        @Override
        public int getWidth(Font font) {
            return font.width(amount) + ICON + font.width(name);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int textY = y + (ICON - font.lineHeight) / 2 + 1;
            g.drawString(font, amount, x, textY, 0xFFFFFFFF);
            int iconX = x + font.width(amount);
            Art.scaledItem(g, coin, iconX, y, ICON);
            g.drawString(font, name, iconX + ICON, textY, 0xFFFFFFFF);
        }
    }
}
