package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.trade.ItemCost;
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

    private static ItemCost cost(Item item, int count, Optional<TagKey<Item>> tag) {
        return new ItemCost(BuiltInRegistries.ITEM.wrapAsHolder(item), count, tag);
    }

    /** One diamond for {@code price} coins, taking {@code takes} and asking to see {@code holds}. */
    private static Entry diamondFor(int price, List<ItemCost> takes, List<ItemCost> holds) {
        Trade trade = new Trade(
            List.of(new ItemStack(Items.DIAMOND)),
            Optional.empty(),
            price,
            Optional.empty(),
            "raw",
            takes,
            holds,
            Trade.NO_GROUP,
            0);
        return StoreTrades.entries(List.of(trade), COINS)
            .get(0);
    }

    @GameTest(template = "empty")
    public static void coinOnlyEntryShowsOnlyCoins(GameTestHelper h) {
        Entry entry = diamondFor(20, List.of(), List.of());
        h.assertTrue(
            same(entry.price(), List.of(new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.GOLD_NUGGET, 2))),
            "20 should be 2 ingots and 2 nuggets, got " + entry.price());
        h.assertTrue(
            entry.items()
                .isEmpty()
                && entry.kept()
                    .isEmpty(),
            "a coins-only trade shows no items, got " + entry);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void itemOnlyEntryShowsTheItemsAndNoCoins(GameTestHelper h) {
        Entry entry = diamondFor(0, List.of(cost(Items.EMERALD, 4, Optional.empty())), List.of());
        h.assertTrue(
            entry.price()
                .isEmpty(),
            "a price of 0 shows no coins, got " + entry.price());
        h.assertTrue(
            entry.items()
                .size() == 1
                && same(
                    entry.items()
                        .get(0),
                    List.of(new ItemStack(Items.EMERALD, 4))),
            "the trade takes 4 emeralds, got " + entry.items());
        h.assertTrue(
            entry.kept()
                .isEmpty(),
            "nothing is only required, got " + entry.kept());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coinAndItemEntryShowsBoth(GameTestHelper h) {
        Entry entry = diamondFor(
            9,
            List.of(cost(Items.IRON_INGOT, 2, Optional.empty()), cost(Items.OAK_LOG, 3, Optional.of(ItemTags.LOGS))),
            List.of());
        h.assertTrue(
            same(entry.price(), List.of(new ItemStack(Items.GOLD_INGOT))),
            "9 should be one ingot, got " + entry.price());
        h.assertTrue(
            entry.items()
                .size() == 2
                && same(
                    entry.items()
                        .get(0),
                    List.of(new ItemStack(Items.IRON_INGOT, 2))),
            "the iron cost should come first, got " + entry.items());
        List<ItemStack> logs = entry.items()
            .get(1);
        h.assertTrue(
            ItemStack.matches(logs.get(0), new ItemStack(Items.OAK_LOG, 3)),
            "a tag cost shows its own item first, got " + logs);
        h.assertTrue(
            logs.stream()
                .anyMatch(stack -> ItemStack.matches(stack, new ItemStack(Items.BIRCH_LOG, 3)))
                && logs.stream()
                    .filter(stack -> stack.is(Items.OAK_LOG))
                    .count() == 1
                && logs.stream()
                    .allMatch(stack -> stack.is(ItemTags.LOGS) && stack.getCount() == 3),
            "a tag cost shows the tag's other logs once each in its count, got " + logs);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void notConsumedItemsAreShownApart(GameTestHelper h) {
        Entry entry = diamondFor(
            0,
            List.of(cost(Items.EMERALD, 1, Optional.empty())),
            List.of(cost(Items.DIAMOND_PICKAXE, 1, Optional.empty())));
        h.assertTrue(
            entry.items()
                .size() == 1
                && same(
                    entry.items()
                        .get(0),
                    List.of(new ItemStack(Items.EMERALD))),
            "the emerald is taken, got " + entry.items());
        h.assertTrue(
            entry.kept()
                .size() == 1
                && same(
                    entry.kept()
                        .get(0),
                    List.of(new ItemStack(Items.DIAMOND_PICKAXE))),
            "the pickaxe is only required, got " + entry.kept());
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
