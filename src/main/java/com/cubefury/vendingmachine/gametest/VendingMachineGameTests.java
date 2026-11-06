package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/** Runs with Create and Create: Numismatics loaded, so the default currency is Numismatics coins. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class VendingMachineGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");

    private VendingMachineGameTests() {}

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name));
    }

    private static VendingMachineBlockEntity placeMachine(GameTestHelper h) {
        h.setBlock(MACHINE, Registration.VENDING_MACHINE.get());
        return h.getBlockEntity(MACHINE);
    }

    private static VendingMachineMenu openMenu(Player player, VendingMachineBlockEntity machine, Trade... trades) {
        return new VendingMachineMenu(0, player.getInventory(), machine, List.of(trades));
    }

    private static void insert(VendingMachineMenu menu, ItemStack coins) {
        menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .set(coins);
    }

    private static int countInInventory(Player player, Item item) {
        return player.getInventory()
            .countItem(item);
    }

    @GameTest(template = "empty")
    public static void currencyUsesNumismaticsCoinValues(GameTestHelper h) {
        h.assertTrue(Currency.unitValue(new ItemStack(coin("spur"))) == 1, "spur should be worth 1");
        h.assertTrue(Currency.unitValue(new ItemStack(coin("cog"))) == 64, "cog should be worth 64 spurs");
        h.assertTrue(Currency.unitValue(new ItemStack(Items.DIRT)) == 0, "dirt is not currency");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void insertedCoinsBecomeCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("cog"), 2));
        h.assertTrue(machine.credit(player.getUUID()) == 128, "credit should be the value of two cogs, 128 spurs");
        h.assertTrue(
            menu.getSlot(VendingMachineMenu.COIN_SLOT)
                .getItem()
                .isEmpty(),
            "coin slot should be empty after insert");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buyWithEnoughCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 64));
        insert(menu, new ItemStack(coin("spur"), 6));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should succeed");
        h.assertTrue(machine.credit(player.getUUID()) == 6, "credit should drop by the price");
        h.assertTrue(
            machine.output.getStackInSlot(0)
                .is(Items.BREAD)
                && machine.output.getStackInSlot(0)
                    .getCount() == 3,
            "output should hold the bought bread");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refuseWithTooLittleCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 63));
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should be refused");
        h.assertTrue(machine.credit(player.getUUID()) == 63, "credit should be unchanged");
        h.assertTrue(
            machine.output.getStackInSlot(0)
                .isEmpty(),
            "output should stay empty");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refuseWhenOutputIsFull(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        for (int i = 0; i < VendingMachineBlockEntity.OUTPUT_SLOTS; i++) {
            machine.output.setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 64));
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should be refused");
        h.assertTrue(machine.credit(player.getUUID()) == 64, "no coins should be taken");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refundReturnsCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 5));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_REFUND), "refund should succeed");
        h.assertTrue(machine.credit(player.getUUID()) == 0, "credit should be zero after refund");
        h.assertTrue(countInInventory(player, coin("spur")) == 5, "refund should return 5 spurs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingDropsCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 3));
        h.destroyBlock(MACHINE);
        int dropped = h.getEntities(EntityType.ITEM, MACHINE, 2.0)
            .stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.is(coin("spur")))
            .mapToInt(ItemStack::getCount)
            .sum();
        h.assertTrue(dropped == 3, "breaking should drop all 3 spurs of credit, dropped " + dropped);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buySameEntryAgain(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("cog"), 2));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "first buy should succeed");
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "second buy should succeed");
        h.assertTrue(machine.credit(player.getUUID()) == 0, "both buys should take the price");
        h.assertTrue(
            machine.output.getStackInSlot(0)
                .getCount() == 6,
            "output should hold bread from both buys");
        h.succeed();
    }

    private static DynamicOps<JsonElement> ops(GameTestHelper h) {
        return h.getLevel()
            .registryAccess()
            .createSerializationContext(JsonOps.INSTANCE);
    }

    @GameTest(template = "empty")
    public static void catalogueFileParses(GameTestHelper h) {
        var table = JsonParser.parseString("""
            [
                {"item": {"id": "minecraft:bread", "count": 3}, "price": 64, "sell_price": 8, "category": "food"},
                {"item": {"id": "minecraft:stone"}, "price": 1},
                {"item": {"id": "minecraft:apple"}, "price": 2, "category": "food"}
            ]""");
        var notATable = JsonParser
            .parseString("{\"item\": {\"id\": \"minecraft:dirt\"}, \"price\": 1, \"category\": \"x\"}");
        List<Trade> trades = TradeDatabase.parse(
            Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "food"),
                table,
                ResourceLocation.fromNamespaceAndPath("test", "single"),
                notATable),
            ops(h));
        h.assertTrue(trades.size() == 2, "bread and apple should load; stone has no category, single is no array");
        Trade bread = trades.get(0);
        h.assertTrue(bread.item()
            .is(Items.BREAD)
            && bread.item()
                .getCount() == 3,
            "bread item should match the file");
        h.assertTrue(bread.price() == 64
            && bread.sellPrice()
                .equals(Optional.of(8))
            && "food".equals(bread.category()),
            "bread fields should match the file");
        Trade apple = trades.get(1);
        h.assertTrue(apple.item()
            .is(Items.APPLE)
            && apple.sellPrice()
                .isEmpty(),
            "apple should load with no sell price");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadClosesOpenMenu(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(2.5, 1, 1.5)));
        var bread = JsonParser.parseString(
            "[{\"item\": {\"id\": \"minecraft:bread\"}, \"price\": 1, \"category\": \"food\"}]");
        TradeDatabase.load(Map.of(ResourceLocation.fromNamespaceAndPath("test", "reload"), bread), ops(h));
        VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, TradeDatabase.trades());
        h.assertTrue(menu.stillValid(player), "menu on the current catalogue should stay open");
        TradeDatabase.load(Map.of(), ops(h));
        h.assertFalse(menu.stillValid(player), "menu on a replaced catalogue should close");
        h.assertTrue(TradeDatabase.trades()
            .isEmpty(), "removed entries should be gone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void currencyWithUndividedCoinIsRefused(GameTestHelper h) {
        List<Currency.Denomination> even = List.of(
            new Currency.Denomination(coin("spur"), 1),
            new Currency.Denomination(coin("cog"), 64));
        h.assertTrue(Currency.usable(even).size() == 2, "spur 1 and cog 64 are both accepted");
        List<Currency.Denomination> uneven = List.of(
            new Currency.Denomination(coin("spur"), 4),
            new Currency.Denomination(coin("bevel"), 3));
        h.assertTrue(Currency.usable(uneven).isEmpty(), "a coin of 4 is refused when the smallest coin is 3");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void currencyListingOneItemTwiceIsRefused(GameTestHelper h) {
        List<Currency.Denomination> twice = List.of(
            new Currency.Denomination(coin("spur"), 9),
            new Currency.Denomination(coin("spur"), 1));
        h.assertTrue(Currency.usable(twice).isEmpty(), "one item listed at two values is refused");
        h.succeed();
    }
}
