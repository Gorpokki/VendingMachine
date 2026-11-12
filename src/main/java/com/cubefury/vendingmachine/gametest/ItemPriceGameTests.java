package com.cubefury.vendingmachine.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.client.WorldFavourites;
import com.cubefury.vendingmachine.trade.ItemCost;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

/** Trades whose price includes items, as the original's "Requires:" items and "Requires (Not Consumed):" items. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class ItemPriceGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final String BUY_FAILS = "buy should fail";
    private static final ItemCost TWO_IRON = new ItemCost(holder(Items.IRON_INGOT), 2, Optional.empty());
    /** 64 spurs and two iron ingots for three bread. */
    private static final Trade BREAD = new Trade(
        List.of(new ItemStack(Items.BREAD, 3)),
        Optional.empty(),
        64,
        Optional.empty(),
        "food",
        List.of(TWO_IRON),
        List.of(),
        Trade.NO_GROUP,
        0);
    /** Two oak logs, or any two logs, and no coins, for a diamond, shown a pickaxe that is not taken. */
    private static final Trade DIAMOND = new Trade(
        List.of(new ItemStack(Items.DIAMOND)),
        Optional.empty(),
        0,
        Optional.empty(),
        "raw",
        List.of(new ItemCost(holder(Items.OAK_LOG), 2, Optional.of(ItemTags.LOGS))),
        List.of(new ItemCost(holder(Items.DIAMOND_PICKAXE), 1, Optional.empty())),
        Trade.NO_GROUP,
        0);

    private ItemPriceGameTests() {}

    private static Holder<Item> holder(Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item);
    }

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name));
    }

    private static VendingMachineMenu open(GameTestHelper h, Player player, Trade trade) {
        VendingMachineBlockEntity machine = Machines.build(h, MACHINE);
        return new VendingMachineMenu(0, player.getInventory(), machine, List.of(trade));
    }

    private static boolean buy(VendingMachineMenu menu, Player player) {
        return menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
    }

    private static int count(Player player, Item item) {
        return player.getInventory()
            .countItem(item);
    }

    @GameTest(template = "empty")
    public static void itemPricesParseBesideCoinOnlyTrades(GameTestHelper h) {
        var table = JsonParser.parseString("""
            [
                {"item": {"id": "minecraft:bread", "count": 3}, "price": 64, "category": "food"},
                {"item": {"id": "minecraft:diamond"}, "category": "raw",
                    "from_items": [{"id": "minecraft:oak_log", "count": 2, "tag": "minecraft:logs"}],
                    "non_consumed_items": [{"id": "minecraft:diamond_pickaxe"}]},
                {"item": {"id": "minecraft:stone"}, "category": "raw"},
                {"item": {"id": "minecraft:dirt"}, "price": 1, "category": "raw",
                    "from_items": [{"id": "minecraft:oak_log"}, {"id": "nomod:missing"}]}
            ]""");
        List<Trade> trades = TradeDatabase.parse(
            Map.of(ResourceLocation.fromNamespaceAndPath("test", "items"), table),
            h.getLevel()
                .registryAccess()
                .createSerializationContext(JsonOps.INSTANCE));
        h.assertTrue(trades.size() == 2, "stone costs nothing and dirt asks for a missing item, so both skip");
        Trade bread = trades.get(0);
        h.assertTrue(bread.price() == 64 && bread.fromItems()
            .isEmpty()
            && bread.nonConsumedItems()
                .isEmpty(),
            "a coin-only trade should load as before");
        Trade diamond = trades.get(1);
        ItemCost logs = diamond.fromItems()
            .get(0);
        h.assertTrue(diamond.price() == 0 && logs.item()
            .value() == Items.OAK_LOG && logs.count() == 2
            && logs.tag()
                .equals(Optional.of(ItemTags.LOGS)),
            "the item price should match the file");
        h.assertTrue(diamond.nonConsumedItems()
            .get(0)
            .count() == 1, "a cost's count should default to 1");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buyTakesCoinsAndItems(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = open(h, player, BREAD);
        menu.machine()
            .deposit(player, new ItemStack(coin("cog")));
        player.getInventory()
            .add(new ItemStack(Items.IRON_INGOT, 3));
        h.assertTrue(menu.canBuy(BREAD), "a cog and three ingots should buy the bread");
        h.assertTrue(buy(menu, player), "buy should succeed");
        h.assertTrue(menu.machine()
            .wallets()
            .total(player.getUUID()) == 0, "the cog should pay the coins");
        h.assertTrue(count(player, Items.IRON_INGOT) == 1, "two of three ingots should be taken");
        Chute.drain(menu.machine());
        h.assertTrue(
            menu.machine().output.getStackInSlot(0)
                .is(Items.BREAD),
            "the chute should hold the bread");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tooFewItemsKeepsCoins(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = open(h, player, BREAD);
        menu.machine()
            .deposit(player, new ItemStack(coin("cog")));
        player.getInventory()
            .add(new ItemStack(Items.IRON_INGOT));
        h.assertFalse(menu.canBuy(BREAD), "one ingot should not buy the bread");
        h.assertFalse(buy(menu, player), BUY_FAILS);
        h.assertTrue(menu.machine()
            .wallets()
            .total(player.getUUID()) == 64, "the wallet should keep the cog");
        h.assertTrue(count(player, Items.IRON_INGOT) == 1, "the ingot should stay");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tooFewCoinsKeepsItems(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = open(h, player, BREAD);
        player.getInventory()
            .add(new ItemStack(Items.IRON_INGOT, 2));
        h.assertFalse(menu.canBuy(BREAD), "ingots alone should not buy the bread");
        h.assertFalse(buy(menu, player), BUY_FAILS);
        h.assertTrue(count(player, Items.IRON_INGOT) == 2, "the ingots should stay");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tagPaysAndNonConsumedItemStays(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = open(h, player, DIAMOND);
        player.getInventory()
            .add(new ItemStack(Items.BIRCH_LOG, 2));
        h.assertFalse(buy(menu, player), "without the pickaxe the buy should fail");
        h.assertTrue(count(player, Items.BIRCH_LOG) == 2, "the logs should stay");
        player.getInventory()
            .add(new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(buy(menu, player), "birch logs count as logs, so the buy should succeed");
        h.assertTrue(count(player, Items.BIRCH_LOG) == 0, "the logs should be taken");
        h.assertTrue(count(player, Items.DIAMOND_PICKAXE) == 1, "the pickaxe is not consumed");
        h.succeed();
    }

    /** Item-priced trades for one output differ by what they take or hold; a coin-only key matches a saved one. */
    @GameTest(template = "empty")
    public static void favouritesTellItemPricesApart(GameTestHelper h) {
        Trade ironTrade = diamondFor(List.of(new ItemCost(holder(Items.IRON_INGOT), 9, Optional.empty())), List.of());
        Trade goldTrade = diamondFor(List.of(new ItemCost(holder(Items.GOLD_INGOT), 2, Optional.empty())), List.of());
        Trade heldTrade = diamondFor(List.of(), List.of(new ItemCost(holder(Items.IRON_INGOT), 9, Optional.empty())));
        List<String> favourites = WorldFavourites.toggle(List.of(), "SP_a", WorldFavourites.tradeKey(ironTrade));
        h.assertTrue(WorldFavourites.contains(favourites, "SP_a", WorldFavourites.tradeKey(ironTrade)),
            "iron is marked");
        h.assertFalse(WorldFavourites.contains(favourites, "SP_a", WorldFavourites.tradeKey(goldTrade)),
            "gold is not");
        h.assertFalse(WorldFavourites.contains(favourites, "SP_a", WorldFavourites.tradeKey(heldTrade)),
            "held iron is not");
        Trade coinBread = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");
        h.assertTrue(
            WorldFavourites.contains(List.of("SP_a|minecraft:bread 3 64"), "SP_a", WorldFavourites.tradeKey(coinBread)),
            "a coin-only trade keeps its saved key");
        h.succeed();
    }

    private static Trade diamondFor(List<ItemCost> takes, List<ItemCost> holds) {
        return new Trade(
            List.of(new ItemStack(Items.DIAMOND)),
            Optional.empty(),
            0,
            Optional.empty(),
            "raw",
            takes,
            holds,
            Trade.NO_GROUP,
            0);
    }

    @GameTest(template = "empty")
    public static void oneItemCannotBothPayAndStay(GameTestHelper h) {
        Trade trade = new Trade(
            List.of(new ItemStack(Items.DIAMOND)),
            Optional.empty(),
            0,
            Optional.empty(),
            "raw",
            List.of(new ItemCost(holder(Items.OAK_LOG), 1, Optional.empty())),
            List.of(new ItemCost(holder(Items.OAK_LOG), 1, Optional.empty())),
            Trade.NO_GROUP,
            0);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = open(h, player, trade);
        player.getInventory()
            .add(new ItemStack(Items.OAK_LOG));
        h.assertFalse(menu.canBuy(trade), "one oak log cannot be both paid and kept");
        h.assertFalse(buy(menu, player), BUY_FAILS);
        h.assertTrue(count(player, Items.OAK_LOG) == 1, "the log should stay");
        player.getInventory()
            .add(new ItemStack(Items.OAK_LOG));
        h.assertTrue(buy(menu, player), "two oak logs should pay and keep one");
        h.assertTrue(count(player, Items.OAK_LOG) == 1, "one log should be paid, one kept");
        h.succeed();
    }

    /** The exact item goes before tag items, from the last slot back, and a damaged item never pays. */
    @GameTest(template = "empty")
    public static void exactItemPaysFirstAndDamagedItemsNever(GameTestHelper h) {
        ItemCost oak = new ItemCost(holder(Items.OAK_LOG), 3, Optional.of(ItemTags.LOGS));
        List<ItemStack> slots = new ArrayList<>(
            List.of(new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.BIRCH_LOG, 4), new ItemStack(Items.OAK_LOG)));
        h.assertTrue(ItemCost.take(slots, List.of(oak), false), "three oak logs should pay");
        h.assertTrue(slots.get(0)
            .isEmpty() && slots.get(1)
                .getCount() == 4
            && slots.get(2)
                .isEmpty(),
            "oak logs should go before birch logs");
        ItemStack worn = new ItemStack(Items.DIAMOND_PICKAXE);
        worn.setDamageValue(1);
        ItemCost pickaxe = new ItemCost(holder(Items.DIAMOND_PICKAXE), 1, Optional.empty());
        h.assertFalse(ItemCost.take(new ArrayList<>(List.of(worn)), List.of(pickaxe), true), "worn tools do not pay");
        h.succeed();
    }
}
