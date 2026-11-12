package com.cubefury.vendingmachine.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.cubefury.vendingmachine.Registration;

/**
 * The original's 2-wide, 3-tall, 1-deep machine: the controller sits middle right as seen from the front, and five
 * Vending Machine Casings fill the rest. Casing positions follow the original's overlay order:
 *
 * <pre>
 * 0 | 1
 * 2 | controller
 * 3 | 4
 * </pre>
 */
public final class MachineStructure {

    public static final int CASINGS = 5;
    /** Steps (to the viewer's left, up) from the controller, in overlay order. */
    private static final int[][] OFFSETS = { { 1, 1 }, { 0, 1 }, { 1, 0 }, { 1, -1 }, { 0, -1 } };

    private MachineStructure() {}

    /** Where casing {@code index} goes for a controller at {@code controller} whose front faces {@code facing}. */
    public static BlockPos casing(BlockPos controller, Direction facing, int index) {
        return controller.relative(facing.getClockWise(), OFFSETS[index][0])
            .above(OFFSETS[index][1]);
    }

    /** Forms the controller when all five casings are in place and unforms it when one is missing. */
    public static void check(Level level, BlockPos controller) {
        BlockState state = level.getBlockState(controller);
        Direction facing = state.getValue(VendingMachineBlock.FACING);
        boolean complete = complete(level, controller, facing);
        show(level, controller, facing, complete);
        if (complete != state.getValue(VendingMachineBlock.FORMED)) {
            level.setBlock(controller, state.setValue(VendingMachineBlock.FORMED, complete), Block.UPDATE_ALL);
        }
    }

    /** True when all five casings are in place and none already belongs to another formed machine. */
    private static boolean complete(Level level, BlockPos controller, Direction facing) {
        for (int i = 0; i < CASINGS; i++) {
            if (!available(level.getBlockState(casing(controller, facing, i)), facing, i)) return false;
        }
        return true;
    }

    /** Shows each casing's overlay while formed; when not, clears only the overlays this controller set. */
    public static void show(Level level, BlockPos controller, Direction facing, boolean formed) {
        for (int i = 0; i < CASINGS; i++) {
            BlockPos pos = casing(controller, facing, i);
            BlockState state = level.getBlockState(pos);
            BlockState shown = formed ? part(state, facing, i) : released(state, facing, i);
            if (shown != state) level.setBlock(pos, shown, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isCasing(BlockState state) {
        return state.is(Registration.VENDING_MACHINE_CASING.get());
    }

    private static boolean available(BlockState state, Direction facing, int index) {
        return isCasing(state)
            && (state.getValue(VendingMachineCasingBlock.SLOT) == 0 || state.equals(part(state, facing, index)));
    }

    private static BlockState part(BlockState state, Direction facing, int index) {
        if (!isCasing(state)) return state;
        return state.setValue(VendingMachineCasingBlock.FACING, facing)
            .setValue(VendingMachineCasingBlock.SLOT, index + 1);
    }

    private static BlockState released(BlockState state, Direction facing, int index) {
        if (!isCasing(state) || !state.equals(part(state, facing, index))) return state;
        return state.setValue(VendingMachineCasingBlock.SLOT, 0);
    }
}
