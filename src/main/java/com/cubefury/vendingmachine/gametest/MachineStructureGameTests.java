package com.cubefury.vendingmachine.gametest;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.MachineStructure;
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.VendingMachineCasingBlock;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.Currency;

/** The original's 2x3 machine: it forms only with all five casings in place and sells nothing until then. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class MachineStructureGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos LEFT_MACHINE = new BlockPos(0, 1, 1);
    private static final BlockPos RIGHT_MACHINE = new BlockPos(2, 1, 1);
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");

    private MachineStructureGameTests() {}

    private static void placeCasings(GameTestHelper h, BlockPos controller, Direction facing, int count) {
        for (int i = 0; i < count; i++) {
            h.setBlock(MachineStructure.casing(controller, facing, i), Registration.VENDING_MACHINE_CASING.get());
        }
    }

    private static void assertCasings(GameTestHelper h, BlockPos controller, Direction facing, boolean shown) {
        for (int i = 0; i < MachineStructure.CASINGS; i++) {
            BlockState casing = h.getBlockState(MachineStructure.casing(controller, facing, i));
            if (!casing.is(Registration.VENDING_MACHINE_CASING.get())) continue;
            int slot = casing.getValue(VendingMachineCasingBlock.SLOT);
            h.assertTrue(slot == (shown ? i + 1 : 0), "casing " + i + " should show slot " + (shown ? i + 1 : 0)
                + ", shows " + slot);
            h.assertTrue(!shown || casing.getValue(VendingMachineCasingBlock.FACING) == facing,
                "casing " + i + " should face " + facing);
        }
    }

    @GameTest(template = "empty")
    public static void formsOnlyWithAllFiveCasings(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.controller(h, MACHINE, Direction.NORTH);
        placeCasings(h, MACHINE, Direction.NORTH, MachineStructure.CASINGS - 1);
        Machines.check(h, MACHINE);
        h.assertFalse(machine.formed(), "four casings should not form the machine");
        assertCasings(h, MACHINE, Direction.NORTH, false);
        placeCasings(h, MACHINE, Direction.NORTH, MachineStructure.CASINGS);
        Machines.check(h, MACHINE);
        h.assertTrue(machine.formed(), "all five casings should form the machine");
        assertCasings(h, MACHINE, Direction.NORTH, true);
        h.succeed();
    }

    /** The server forms a finished machine on its own, on a whole second's tick. */
    @GameTest(template = "empty", timeoutTicks = 2 * VendingMachineBlock.STRUCTURE_CHECK_TICKS)
    public static void serverChecksTheCasingsOnceASecond(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.controller(h, MACHINE, Direction.NORTH);
        placeCasings(h, MACHINE, Direction.NORTH, MachineStructure.CASINGS);
        long[] formedAt = { -1 };
        h.onEachTick(() -> {
            if (formedAt[0] < 0 && machine.formed()) formedAt[0] = h.getLevel()
                .getGameTime();
        });
        h.succeedWhen(() -> {
            h.assertTrue(formedAt[0] >= 0, "the server should form a finished machine");
            h.assertTrue(formedAt[0] % VendingMachineBlock.STRUCTURE_CHECK_TICKS == 0,
                "the casings are checked once a second");
        });
    }

    @GameTest(template = "empty")
    public static void formsFacingEastWithCasingsToTheSouth(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.build(h, MACHINE, Direction.EAST);
        h.assertTrue(machine.formed(), "should form facing east");
        h.assertBlockPresent(Registration.VENDING_MACHINE_CASING.get(), MACHINE.south()
            .above());
        assertCasings(h, MACHINE, Direction.EAST, true);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unformsWhenACasingIsRemoved(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.build(h, MACHINE);
        h.setBlock(MachineStructure.casing(MACHINE, Direction.NORTH, 4), Blocks.AIR);
        Machines.check(h, MACHINE);
        h.assertFalse(machine.formed(), "a missing casing should unform the machine");
        assertCasings(h, MACHINE, Direction.NORTH, false);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingTheControllerClearsTheCasings(GameTestHelper h) {
        Machines.build(h, MACHINE);
        h.setBlock(MACHINE, Blocks.AIR);
        assertCasings(h, MACHINE, Direction.NORTH, false);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void casingCannotBeMovedByPistons(GameTestHelper h) {
        h.assertTrue(
            Registration.VENDING_MACHINE_CASING.get()
                .defaultBlockState()
                .getPistonPushReaction() == PushReaction.BLOCK,
            "a casing carries its machine state, so pistons must not move it");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void casingBelongsToOneFormedMachine(GameTestHelper h) {
        VendingMachineBlockEntity first = Machines.controller(h, LEFT_MACHINE, Direction.NORTH);
        VendingMachineBlockEntity second = Machines.controller(h, RIGHT_MACHINE, Direction.SOUTH);
        placeCasings(h, LEFT_MACHINE, Direction.NORTH, MachineStructure.CASINGS);
        placeCasings(h, RIGHT_MACHINE, Direction.SOUTH, MachineStructure.CASINGS);
        Machines.check(h, LEFT_MACHINE);
        Machines.check(h, RIGHT_MACHINE);
        h.assertTrue(first.formed(), "the first machine should form");
        h.assertFalse(second.formed(), "a machine should not form on casings another formed machine uses");
        for (int i = 0; i < 3; i++) {
            Machines.check(h, LEFT_MACHINE);
            Machines.check(h, RIGHT_MACHINE);
            assertCasings(h, LEFT_MACHINE, Direction.NORTH, true);
            h.assertFalse(second.formed(), "the second machine should stay unformed on every check");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sellsAndDropsNothingWhileUnformed(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.controller(h, MACHINE, Direction.NORTH);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        machine.wallets()
            .add(player.getUUID(), Currency.makeChange(128));
        h.assertFalse(machine.buy(player, BREAD, ItemStack.EMPTY), "an unformed machine should not sell");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 128, "the wallet should keep its money");
        placeCasings(h, MACHINE, Direction.NORTH, MachineStructure.CASINGS);
        Machines.check(h, MACHINE);
        h.assertTrue(machine.buy(player, BREAD, ItemStack.EMPTY), "a formed machine should sell");
        h.setBlock(MachineStructure.casing(MACHINE, Direction.NORTH, 0), Blocks.AIR);
        Machines.check(h, MACHINE);
        h.runAfterDelay(VendingMachineBlock.STRUCTURE_CHECK_TICKS, () -> {
            h.assertTrue(Chute.count(machine, Items.BREAD) == 0 && machine.queued() == 1,
                "nothing should drop into the chute once unformed");
            h.succeed();
        });
    }
}
