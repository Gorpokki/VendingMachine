package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.ShippingBinBlockEntity;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.Currency;

/** Selling to the shipping bin, with Create: Numismatics coins as the currency. Prices are in spurs. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public class ShippingBinGameTests {

    private static final BlockPos BIN = new BlockPos(1, 1, 1);
    /** Sells 3 bread for 8. */
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.of(8), "food");
    /** Buyable only: no sell price. */
    private static final Trade APPLE = new Trade(new ItemStack(Items.APPLE), 2, Optional.empty(), "food");
    private static final List<Trade> CATALOGUE = List.of(BREAD, APPLE);

    private static ShippingBinBlockEntity placeBin(GameTestHelper h) {
        h.setBlock(BIN, Registration.SHIPPING_BIN.get());
        return h.getBlockEntity(BIN);
    }

    private static long coinValue(ShippingBinBlockEntity bin) {
        long value = 0;
        for (int i = 0; i < bin.getContainerSize(); i++) {
            value += Currency.valueOf(bin.getItem(i));
        }
        return value;
    }

    @GameTest(template = "empty")
    public static void sellsPricedItemsForCoins(GameTestHelper h) {
        ShippingBinBlockEntity bin = placeBin(h);
        bin.setItem(0, new ItemStack(Items.BREAD, 7));
        h.assertTrue(bin.sell(CATALOGUE), "bread has a sell price, so it should sell");
        h.assertTrue(bin.countItem(Items.BREAD) == 1, "two lots of 3 bread sell; 1 bread stays");
        h.assertTrue(coinValue(bin) == 16, "two lots at 8 should pay 16 spurs in coins, paid " + coinValue(bin));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unpricedItemsStay(GameTestHelper h) {
        ShippingBinBlockEntity bin = placeBin(h);
        bin.setItem(0, new ItemStack(Items.STONE, 64));
        bin.setItem(1, new ItemStack(Items.APPLE, 5));
        h.assertFalse(bin.sell(CATALOGUE), "nothing has a sell price, so nothing should sell");
        h.assertTrue(bin.countItem(Items.STONE) == 64, "stone with no catalogue entry should stay");
        h.assertTrue(bin.countItem(Items.APPLE) == 5, "apple with no sell price should stay");
        h.assertTrue(coinValue(bin) == 0, "no coins should appear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullBinKeepsItemsWhenCoinsDoNotFit(GameTestHelper h) {
        ShippingBinBlockEntity bin = placeBin(h);
        for (int i = 0; i < ShippingBinBlockEntity.SLOTS - 1; i++) {
            bin.setItem(i, new ItemStack(Items.STONE, 64));
        }
        bin.setItem(ShippingBinBlockEntity.SLOTS - 1, new ItemStack(Items.BREAD, 4));
        h.assertFalse(bin.sell(CATALOGUE), "the coins have no room, so the sale should not happen");
        h.assertTrue(bin.countItem(Items.BREAD) == 4, "all 4 bread should stay");
        h.assertTrue(coinValue(bin) == 0, "no coins should appear");
        h.succeed();
    }
}
