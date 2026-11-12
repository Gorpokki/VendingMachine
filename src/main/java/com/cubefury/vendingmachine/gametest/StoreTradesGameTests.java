package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.trade.StoreTrades;
import com.cubefury.vendingmachine.trade.StoreTrades.Entry;
import com.cubefury.vendingmachine.trade.StoreTrades.Sync;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.Currency.Denomination;

/** The recipe viewer page's entries, built and synced without either viewer installed. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class StoreTradesGameTests {

    private static final List<Denomination> COINS = List
        .of(new Denomination(Items.GOLD_INGOT, 9), new Denomination(Items.GOLD_NUGGET, 1));
    private static final Trade DIAMONDS = new Trade(new ItemStack(Items.DIAMOND, 2), 20, Optional.empty(), "raw");
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD), 9, Optional.of(1), "food");

    private StoreTradesGameTests() {}

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static boolean sameEntries(List<Entry> a, List<Entry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!same(a.get(i)
                .price(),
                b.get(i)
                    .price())
                || !same(
                    a.get(i)
                        .outputs(),
                    b.get(i)
                        .outputs()))
                return false;
        }
        return true;
    }

    @GameTest(template = "empty")
    public static void entryPricesAreTheFewestCoinsLargestFirst(GameTestHelper h) {
        List<Entry> entries = StoreTrades.entries(List.of(DIAMONDS, BREAD), COINS);
        h.assertTrue(entries.size() == 2, "one entry per trade, got " + entries);
        h.assertTrue(
            same(
                entries.get(0)
                    .price(),
                List.of(new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.GOLD_NUGGET, 2))),
            "20 should be 2 ingots and 2 nuggets, got " + entries.get(0)
                .price());
        h.assertTrue(
            same(
                entries.get(0)
                    .outputs(),
                List.of(new ItemStack(Items.DIAMOND, 2))),
            "output should be the trade's item, got " + entries.get(0)
                .outputs());
        h.assertTrue(
            same(
                entries.get(1)
                    .price(),
                List.of(new ItemStack(Items.GOLD_INGOT))),
            "9 should be one ingot and no nuggets, got " + entries.get(1)
                .price());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void entryListsEveryStackOfAMultiStackTrade(GameTestHelper h) {
        Trade mixed = new Trade(
            List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD)),
            Optional.empty(),
            20,
            Optional.empty(),
            "raw",
            List.of(),
            List.of(),
            Trade.NO_GROUP,
            0);
        List<Entry> entries = StoreTrades.entries(List.of(mixed), COINS);
        h.assertTrue(
            same(
                entries.get(0)
                    .outputs(),
                List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD))),
            "every stack the trade gives should be listed, got " + entries.get(0)
                .outputs());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void eachSyncReplacesTheClientEntries(GameTestHelper h) {
        Sync sync = new Sync(List.of(DIAMONDS, BREAD), COINS);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            h.getLevel()
                .registryAccess());
        Sync.STREAM_CODEC.encode(buf, sync);
        StoreTrades.receive(Sync.STREAM_CODEC.decode(buf));
        h.assertTrue(
            sameEntries(StoreTrades.synced(), StoreTrades.entries(sync.trades(), COINS)),
            "the client should list the synced catalogue, got " + StoreTrades.synced());

        StoreTrades.receive(new Sync(List.of(BREAD), COINS));
        h.assertTrue(
            sameEntries(StoreTrades.synced(), StoreTrades.entries(List.of(BREAD), COINS)),
            "a reload's sync should replace the list, got " + StoreTrades.synced());

        // With no player on the test server, a sync sends nothing and must not fail.
        StoreTrades.send(
            new OnDatapackSyncEvent(
                h.getLevel()
                    .getServer()
                    .getPlayerList(),
                null));
        h.succeed();
    }
}
