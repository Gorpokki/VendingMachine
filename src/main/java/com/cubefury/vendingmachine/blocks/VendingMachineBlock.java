package com.cubefury.vendingmachine.blocks;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;
import com.mojang.serialization.MapCodec;

public class VendingMachineBlock extends BaseEntityBlock {

    public static final MapCodec<VendingMachineBlock> CODEC = simpleCodec(VendingMachineBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** True while all five casings are in place; until then the machine is dark and sells nothing. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** As in the original, the machine checks its casings once a second. */
    public static final int STRUCTURE_CHECK_TICKS = 20;

    /**
     * The item's tooltip lines after its name, as the original's: the machine type, the lore and a hint to hold Shift,
     * or with {@code structure} (Shift held) the structure. The original's uplink hatch line and its StructureLib
     * hologram projector line are left out, since this port has neither.
     */
    public static List<Component> tooltip(boolean structure) {
        List<String> keys = structure
            ? List.of(
                "vendingmachine.tooltip.structure.dimensions",
                "vendingmachine.tooltip.structure.title",
                "vendingmachine.tooltip.structure.controller",
                "vendingmachine.tooltip.structure.casing",
                "vendingmachine.tooltip.structure.blank",
                "vendingmachine.tooltip.structure.footer")
            : List.of(
                "vendingmachine.tooltip.machine_type",
                "vendingmachine.tooltip.info",
                "vendingmachine.tooltip.separator",
                "vendingmachine.tooltip.hold_shift");
        return keys.stream()
            .<Component>map(
                key -> Component.translatable(key)
                    .withStyle(ChatFormatting.GRAY))
            .toList();
    }

    /**
     * The original's WAILA line for the machine, shown by Jade when it is installed: green "Running Fine" while formed,
     * else red "** INCOMPLETE STRUCTURE **". The original's uplink queue lines are left out, since this port has no
     * uplink.
     */
    public static Component structureStatus(BlockState state) {
        return state.getValue(FORMED)
            ? Component.translatable("vendingmachine.jade.status.running_fine")
                .withStyle(ChatFormatting.GREEN)
            : Component.translatable("vendingmachine.jade.status.incomplete")
                .withStyle(ChatFormatting.RED);
    }

    public VendingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
            FACING,
            context.getHorizontalDirection()
                .getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VendingMachineBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof VendingMachineBlockEntity machine) {
            open(serverPlayer, machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** One player at a time, as in the original; anyone else is told the machine is busy. */
    private static void open(ServerPlayer player, VendingMachineBlockEntity machine) {
        if (!machine.mayUse(player)) {
            player.displayClientMessage(Component.translatable("vendingmachine.gui.error.player_using"), false);
            return;
        }
        List<Trade> trades = TradeDatabase.trades();
        player.openMenu(
            machine.menuProvider(trades),
            buf -> VendingMachineMenu.writeOpenData(buf, machine.getBlockPos(), trades));
        machine.startUsing(player);
    }

    /**
     * Sneaking with an item in hand normally skips the block's use; for coins on this machine it runs, so
     * {@link #useItemOn} can pay them in.
     */
    public static void allowSneakDeposit(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity()
            .isSecondaryUseActive() && Currency.unitValue(event.getItemStack()) > 0
            && event.getLevel()
                .getBlockState(event.getPos())
                .getBlock() instanceof VendingMachineBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    /**
     * Sneak-use with coins in hand pays the whole stack into the wallet without opening the screen; an unformed machine
     * takes nothing, as in the original.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() || Currency.unitValue(stack) <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && state.getValue(FORMED)
            && level.getBlockEntity(pos) instanceof VendingMachineBlockEntity machine) {
            machine.deposit(player, stack.copy());
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
        BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, Registration.VENDING_MACHINE_BE.get(), (l, p, s, m) -> tick(l, p, m));
    }

    /** Checks the casings once a second; an unformed machine does nothing else, as in the original. */
    private static void tick(Level level, BlockPos pos, VendingMachineBlockEntity machine) {
        if (level.getGameTime() % STRUCTURE_CHECK_TICKS == 0) MachineStructure.check(level, pos);
        if (machine.formed()) machine.tick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof VendingMachineBlockEntity machine) {
            machine.placedBy(player);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VendingMachineBlockEntity machine) {
            machine.dropContents(level, pos);
            MachineStructure.show(level, pos, state.getValue(FACING), false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
