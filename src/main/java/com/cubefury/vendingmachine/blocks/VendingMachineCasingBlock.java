package com.cubefury.vendingmachine.blocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One of the five casings around a Vending Machine. While the machine is formed, the machine sets each casing's
 * {@link #SLOT} and {@link #FACING}, so the casing shows its part of the original's large machine front.
 */
public class VendingMachineCasingBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 0 while not part of a formed machine; otherwise 1 + the casing's position in {@link MachineStructure}. */
    public static final IntegerProperty SLOT = IntegerProperty.create("slot", 0, MachineStructure.CASINGS);

    public VendingMachineCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SLOT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SLOT);
    }
}
