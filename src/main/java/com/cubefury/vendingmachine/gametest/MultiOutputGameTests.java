package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.ShippingBinBlockEntity;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;

/** Trades that give several stacks, show a display item, or belong to a trade group, as in the original. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class MultiOutputGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** 3 bread and 2 apples for 1 spur, shown as a cake. */
    private static final Trade PICNIC = new Trade(
        List.of(new ItemStack(Items.BREAD, 3), new ItemStack(Items.APPLE, 2)),
        Optional.of(new ItemStack(Items.CAKE)),
        1,
        Optional.of(1),
        "food",
        List.of(),
        List.of(),
        Trade.NO_GROUP,
        0);

    private MultiOutputGameTests() {}

    private static Item spur() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", "spur"));
    }

    private static List<Trade> parse(GameTestHelper h, String json) {
        return TradeDatabase.parse(
            Map.of(ResourceLocation.fromNamespaceAndPath("test", "multi"), JsonParser.parseString(json)),
            h.getLevel()
                .registryAccess()
                .createSerializationContext(JsonOps.INSTANCE));
    }

    @GameTest(template = "empty")
    public static void entriesLoadItemsAndDisplayItems(GameTestHelper h) {
        List<Trade> trades = parse(h, """
            [
                {"item": {"id": "minecraft:bread", "count": 3}, "price": 1, "category": "food"},
                {"items": [{"id": "minecraft:bread"}, {"id": "minecraft:apple", "count": 2}],
                    "display_item": {"id": "minecraft:cake"}, "price": 2, "category": "food"},
                {"items": [], "price": 3, "category": "food"}
            ]""");
        h.assertTrue(trades.size() == 2, "two trades should load, got " + trades.size());
        Trade bread = trades.get(0);
        h.assertTrue(bread.items()
            .size() == 1
            && bread.item()
                .is(Items.BREAD),
            "a one-item entry should give its one stack");
        h.assertTrue(bread.displayItem()
            .isEmpty() && bread.display() == bread.item(), "a one-item entry should show its stack");
        h.assertTrue(bread.group() == Trade.NO_GROUP, "a top-level entry should be in no group");
        Trade picnic = trades.get(1);
        ItemStack apples = picnic.items()
            .get(1);
        h.assertTrue(picnic.items()
            .size() == 2 && apples.is(Items.APPLE) && apples.getCount() == 2, "an items entry should give every stack");
        h.assertTrue(picnic.display()
            .is(Items.CAKE), "an items entry should show its display item");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tradeWithAMissingStackIsSkippedWhole(GameTestHelper h) {
        List<Trade> trades = parse(h, """
            [
                {"items": [{"id": "minecraft:bread"}, {"id": "minecraft:no_such_item"}],
                    "price": 5, "category": "food"},
                {"items": [{"id": "minecraft:bread"}, {"id": "minecraft:apple"}],
                    "price": 2, "category": "food"}
            ]""");
        h.assertTrue(trades.size() == 1, "only the complete trade should load, got " + trades.size());
        h.assertTrue(trades.get(0)
            .items()
            .size() == 2 && trades.get(0)
                .price() == 2, "a trade with a missing stack should not load with the rest of its stacks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void groupsShareCategoryNumberAndOrder(GameTestHelper h) {
        List<Trade> trades = parse(h, """
            [
                {"category": "tools", "trades": [
                    {"item": {"id": "minecraft:stone_axe"}, "price": 3},
                    {"item": {"id": "minecraft:no_such_item"}, "price": 3},
                    {"item": {"id": "minecraft:stone_pickaxe"}, "price": 4, "category": "ignored"}
                ]},
                {"category": "food", "trades": [{"item": {"id": "minecraft:cookie"}, "price": 1}]},
                {"category": "food", "trades": "not an array"}
            ]""");
        h.assertTrue(trades.size() == 3, "three trades should load, got " + trades.size());
        Trade axe = trades.get(0);
        Trade pickaxe = trades.get(1);
        Trade cookie = trades.get(2);
        h.assertTrue("tools".equals(axe.category()) && "tools".equals(pickaxe.category()),
            "trades in a group should take the group's category");
        h.assertTrue(axe.group() != Trade.NO_GROUP && axe.group() == pickaxe.group() && cookie.group() != axe.group(),
            "trades should share their group's number, and groups should differ");
        h.assertTrue(axe.groupOrder() == 0 && pickaxe.groupOrder() == 1 && cookie.groupOrder() == 0,
            "trades should keep their order in the group, skipping broken trades");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tradesSurviveTheNetwork(GameTestHelper h) {
        Trade grouped = PICNIC.inGroup(4, 2);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            h.getLevel()
                .registryAccess());
        Trade.STREAM_CODEC.encode(buf, grouped);
        Trade read = Trade.STREAM_CODEC.decode(buf);
        h.assertTrue(read.items()
            .size() == 2
            && ItemStack.matches(
                read.items()
                    .get(1),
                grouped.items()
                    .get(1)),
            "every stack should cross the network");
        h.assertTrue(read.display()
            .is(Items.CAKE), "the display item should cross the network");
        h.assertTrue(read.price() == 1 && read.sellPrice()
            .equals(Optional.of(1)), "the prices should cross the network");
        h.assertTrue("food".equals(read.category()), "the category should cross the network");
        h.assertTrue(read.group() == 4 && read.groupOrder() == 2, "the group should cross the network");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buyingQueuesEveryStack(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.build(h, MACHINE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, List.of(PICNIC));
        machine.deposit(player, new ItemStack(spur(), 2));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should succeed");
        h.assertTrue(machine.queued() == 2, "both stacks should join the queue");
        machine.tick();
        machine.tick();
        h.assertTrue(machine.output.getStackInSlot(0)
            .is(Items.BREAD)
            && machine.output.getStackInSlot(0)
                .getCount() == 3
            && machine.output.getStackInSlot(1)
                .is(Items.APPLE)
            && machine.output.getStackInSlot(1)
                .getCount() == 2,
            "the stacks should drop in trade order");
        h.assertTrue(PICNIC.items()
            .get(0)
            .getCount() == 3, "buying should not change the catalogue's stacks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buyingNeedsRoomForEveryStack(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.build(h, MACHINE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < VendingMachineBlockEntity.OUTPUT_SLOTS - 1; i++) {
            machine.output.setStackInSlot(i, new ItemStack(Items.STONE));
        }
        VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, List.of(PICNIC));
        machine.deposit(player, new ItemStack(spur(), 1));
        h.assertFalse(
            menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE),
            "one free slot should not take two stacks");
        h.assertTrue(machine.queued() == 0, "nothing should be queued");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 1, "the refused purchase should cost nothing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shippingBinBuysBackOnlyOneStackTrades(GameTestHelper h) {
        h.setBlock(MACHINE, Registration.SHIPPING_BIN.get());
        ShippingBinBlockEntity bin = h.getBlockEntity(MACHINE);
        bin.setItem(0, new ItemStack(Items.BREAD, 3));
        h.assertFalse(bin.sell(List.of(PICNIC)), "a trade that gives several stacks should not buy them back");
        h.assertTrue(bin.countItem(Items.BREAD) == 3, "the bread should stay");
        h.succeed();
    }
}
