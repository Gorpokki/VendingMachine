package com.cubefury.vendingmachine.client.viewer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;

/**
 * The layout both recipe viewers draw a store trade with, after the original's NEI page: the price coins in a grid of
 * four on the left, an arrow, then the stacks bought in a grid of four, wrapping to further rows.
 */
final class TradePage {

    /** The JEI recipe type and EMI category id. EMI skips a JEI category whose id it already has natively. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VendingMachine.MODID, "vending_machine");

    static final int SLOT = 18;
    private static final int COLUMNS = 4;
    // ponytail: two rows fit eight coin types; Numismatics has six. Grow ROWS if a currency config lists more.
    private static final int ROWS = 2;
    static final int ARROW_X = COLUMNS * SLOT + 4;
    static final int ARROW_Y = (ROWS * SLOT - 17) / 2;
    static final int OUTPUT_X = ARROW_X + 24 + 4;
    static final int OUTPUT_Y = (ROWS * SLOT - SLOT) / 2;

    private TradePage() {}

    static Component title() {
        return Component.translatable("block.vendingmachine.vending_machine");
    }

    static ItemStack machine() {
        return new ItemStack(Registration.VENDING_MACHINE_ITEM.get());
    }

    /** The page width for a trade giving {@code outputs} stacks: one column per output, up to four. */
    static int width(int outputs) {
        return OUTPUT_X + Math.min(outputs, COLUMNS) * SLOT;
    }

    /** The page height for a trade giving {@code outputs} stacks: the coins' rows, or more if the outputs wrap. */
    static int height(int outputs) {
        return Math.max(ROWS * SLOT, OUTPUT_Y + (outputs + COLUMNS - 1) / COLUMNS * SLOT);
    }

    static int coinX(int index) {
        return index % COLUMNS * SLOT;
    }

    static int coinY(int index) {
        return index / COLUMNS * SLOT;
    }

    static int outputX(int index) {
        return OUTPUT_X + index % COLUMNS * SLOT;
    }

    static int outputY(int index) {
        return OUTPUT_Y + index / COLUMNS * SLOT;
    }
}
