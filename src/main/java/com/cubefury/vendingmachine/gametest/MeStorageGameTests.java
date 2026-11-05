package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;

/**
 * Paying from ME storage, with Applied Energistics 2 and Create: Numismatics loaded. Each test builds a network of the
 * machine, an ME drive with a 1k item cell, and a creative energy cell, then waits for the grid to boot.
 */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public class MeStorageGameTests {

    private static final BlockPos MACHINE = new BlockPos(0, 1, 1);
    private static final BlockPos DRIVE = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY = new BlockPos(2, 1, 1);
    private static final int BOOT_TICKS = 40;
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");

    private static Item item(String namespace, String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, name));
    }

    private static Item coin(String name) {
        return item("numismatics", name);
    }

    private static VendingMachineBlockEntity buildNetwork(GameTestHelper h, Player owner) {
        h.setBlock(MACHINE, Registration.VENDING_MACHINE.get());
        h.setBlock(DRIVE, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2", "drive")));
        h.setBlock(ENERGY,
            BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2", "creative_energy_cell")));
        DriveBlockEntity drive = h.getBlockEntity(DRIVE);
        drive.getInternalInventory()
            .addItems(new ItemStack(item("ae2", "item_storage_cell_1k")));
        VendingMachineBlockEntity machine = h.getBlockEntity(MACHINE);
        machine.placedBy(owner);
        return machine;
    }

    private static MEStorage storage(GameTestHelper h, VendingMachineBlockEntity machine) {
        MEStorage storage = machine.meStorageLink()
            .storage();
        h.assertTrue(storage != null, "machine should be on an active AE2 network");
        return storage;
    }

    private static void store(MEStorage storage, Item item, long count) {
        storage.insert(AEItemKey.of(item), count, Actionable.MODULATE, IActionSource.empty());
    }

    private static long stored(MEStorage storage, Item item) {
        return storage.extract(AEItemKey.of(item), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static boolean buy(Player player, VendingMachineMenu menu) {
        return menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void paysFromMeStorage(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = buildNetwork(h, player);
        h.runAfterDelay(BOOT_TICKS, () -> {
            MEStorage storage = storage(h, machine);
            store(storage, coin("spur"), 64);
            store(storage, coin("cog"), 1);
            VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, List.of(BREAD));
            h.assertTrue(buy(player, menu), "ME storage should pay");
            h.assertTrue(stored(storage, coin("cog")) == 0, "the cog (64 spurs) should pay the price of 64");
            h.assertTrue(stored(storage, coin("spur")) == 64, "the spurs should stay in ME storage");
            h.assertTrue(machine.credit(player.getUUID()) == 0, "no credit should be left over");
            h.assertTrue(
                machine.output.getStackInSlot(0)
                    .getCount() == 3,
                "output should hold bought bread");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void meStorageCoversCreditShortfall(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = buildNetwork(h, player);
        h.runAfterDelay(BOOT_TICKS, () -> {
            MEStorage storage = storage(h, machine);
            store(storage, coin("cog"), 1);
            VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, List.of(BREAD));
            menu.getSlot(VendingMachineMenu.COIN_SLOT)
                .set(new ItemStack(coin("spur"), 60));
            h.assertTrue(buy(player, menu), "credit and ME storage together should pay");
            h.assertTrue(stored(storage, coin("cog")) == 0, "the cog should cover the missing 4 spurs");
            h.assertTrue(machine.credit(player.getUUID()) == 60, "the overpaid 60 spurs should stay as credit");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void refuseWhenMeStorageLacksCoins(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = buildNetwork(h, player);
        h.runAfterDelay(BOOT_TICKS, () -> {
            MEStorage storage = storage(h, machine);
            store(storage, coin("spur"), 10);
            VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, List.of(BREAD));
            h.assertFalse(buy(player, menu), "buy should be refused");
            h.assertTrue(stored(storage, coin("spur")) == 10, "ME storage should be unchanged");
            h.assertTrue(machine.credit(player.getUUID()) == 0, "credit should stay 0");
            h.assertTrue(
                machine.output.getStackInSlot(0)
                    .isEmpty(),
                "output should stay empty");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void refuseMeStorageForPlayerWhoDoesNotOwnNetwork(GameTestHelper h) {
        Player owner = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = buildNetwork(h, owner);
        h.runAfterDelay(BOOT_TICKS, () -> {
            MEStorage storage = storage(h, machine);
            store(storage, coin("cog"), 1);
            Player stranger = h.makeMockPlayer(GameType.SURVIVAL);
            VendingMachineMenu menu = new VendingMachineMenu(0, stranger.getInventory(), machine, List.of(BREAD));
            h.assertFalse(
                buy(stranger, menu),
                "a player who does not own the network should not pay from its ME storage");
            h.assertTrue(stored(storage, coin("cog")) == 1, "ME storage should be unchanged");
            h.assertTrue(machine.credit(stranger.getUUID()) == 0, "stranger should get no credit");
            h.succeed();
        });
    }
}
