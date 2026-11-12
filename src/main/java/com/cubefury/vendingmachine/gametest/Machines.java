package com.cubefury.vendingmachine.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.blocks.MachineStructure;
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;

/** Test help: places a whole machine, the controller and its five casings, and forms it. */
final class Machines {

    private Machines() {}

    static VendingMachineBlockEntity build(GameTestHelper h, BlockPos pos) {
        return build(h, pos, Direction.NORTH);
    }

    static VendingMachineBlockEntity build(GameTestHelper h, BlockPos pos, Direction facing) {
        VendingMachineBlockEntity machine = controller(h, pos, facing);
        for (int i = 0; i < MachineStructure.CASINGS; i++) {
            h.setBlock(MachineStructure.casing(pos, facing, i), Registration.VENDING_MACHINE_CASING.get());
        }
        check(h, pos);
        return machine;
    }

    /** Runs the controller's structure check at once, as the server does once a second. */
    static void check(GameTestHelper h, BlockPos pos) {
        MachineStructure.check(h.getLevel(), h.absolutePos(pos));
    }

    /** The controller alone, without casings. */
    static VendingMachineBlockEntity controller(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(
            pos,
            Registration.VENDING_MACHINE.get()
                .defaultBlockState()
                .setValue(VendingMachineBlock.FACING, facing));
        return h.getBlockEntity(pos);
    }
}
