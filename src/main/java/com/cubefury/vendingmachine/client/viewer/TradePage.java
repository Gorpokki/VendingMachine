package com.cubefury.vendingmachine.client.viewer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.trade.StoreTrades.Entry;

/**
 * The layout both recipe viewers draw a store trade with, after the original's NEI page: the price coins, then the
 * items taken, then the items only asked to see, in a grid of four on the left, an arrow, then the stacks bought in a
 * grid of four, both wrapping to further rows.
 */
final class TradePage {

    /** The JEI recipe type and EMI category id. EMI skips a JEI category whose id it already has natively. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VendingMachine.MODID, "vending_machine");

    static final int SLOT = 18;
    private static final int COLUMNS = 4;
    // ponytail: the arrow and outputs centre on two input rows; inputs past eight wrap below them. Centre on each
    // trade's own input rows if long input lists look lopsided.
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

    /** The tooltip line marking an item the trade asks to see but does not take, in the machine's own words. */
    static Component kept() {
        return Component.translatable("vendingmachine.gui.nc_inputs")
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC);
    }

    /** How many input slots an entry fills: its coins, the items it takes, and the items it only asks to see. */
    static int inputs(Entry entry) {
        return entry.price()
            .size()
            + entry.items()
                .size()
            + entry.kept()
                .size();
    }

    /** The page width for a trade giving {@code outputs} stacks: one column per output, up to four. */
    static int width(int outputs) {
        return OUTPUT_X + Math.min(outputs, COLUMNS) * SLOT;
    }

    /**
     * The page height for a trade with {@code inputs} input slots giving {@code outputs} stacks: the coins' rows, or
     * more if the inputs or outputs wrap.
     */
    static int height(int inputs, int outputs) {
        int inputRows = Math.max(ROWS, (inputs + COLUMNS - 1) / COLUMNS);
        return Math.max(inputRows * SLOT, OUTPUT_Y + (outputs + COLUMNS - 1) / COLUMNS * SLOT);
    }

    static int inputX(int index) {
        return index % COLUMNS * SLOT;
    }

    static int inputY(int index) {
        return index / COLUMNS * SLOT;
    }

    static int outputX(int index) {
        return OUTPUT_X + index % COLUMNS * SLOT;
    }

    static int outputY(int index) {
        return OUTPUT_Y + index / COLUMNS * SLOT;
    }
}
