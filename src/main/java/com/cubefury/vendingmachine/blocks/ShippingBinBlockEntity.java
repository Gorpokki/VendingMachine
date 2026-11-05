package com.cubefury.vendingmachine.blocks;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;

/**
 * A chest-sized bin that buys every item with a sell price in the catalogue and pays in coin items placed in the bin.
 * Items with no sell price, and coins, stay where they are.
 */
public class ShippingBinBlockEntity extends BaseContainerBlockEntity {

    public static final int SLOTS = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean changed = true;

    public ShippingBinBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.SHIPPING_BIN_BE.get(), pos, state);
    }

    /** Server tick: sells once after the contents change. */
    public void tick() {
        if (!changed) return;
        changed = false;
        sell(TradeDatabase.trades());
    }

    /**
     * Sells every stack that has a sell price in {@code catalogue}, in whole multiples of the entry's stack size, and
     * puts the coins in the bin. All or nothing: when the coins do not fit, nothing changes. Returns whether a sale
     * happened.
     */
    public boolean sell(List<Trade> catalogue) {
        SimpleContainer after = new SimpleContainer(SLOTS);
        long value = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.get(i);
            Trade trade = sellable(stack, catalogue);
            int lots = trade == null ? 0 : stack.getCount() / trade.item()
                .getCount();
            if (lots > 0) {
                value += (long) lots * trade.sellPrice()
                    .get();
                stack = stack.copyWithCount(stack.getCount() - lots * trade.item()
                    .getCount());
            }
            after.setItem(i, stack.copy());
        }
        List<ItemStack> coins = Currency.makeChange(value);
        if (value == 0 || Currency.total(coins) != value) return false;
        for (ItemStack coin : coins) {
            if (!after.addItem(coin)
                .isEmpty()) return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            items.set(i, after.getItem(i));
        }
        setChanged();
        return true;
    }

    /** The first catalogue entry with a sell price for this exact item, or null. Coins are never sold. */
    private static Trade sellable(ItemStack stack, List<Trade> catalogue) {
        if (stack.isEmpty() || Currency.unitValue(stack) > 0) return null;
        for (Trade trade : catalogue) {
            if (trade.sellPrice()
                .isPresent() && ItemStack.isSameItemSameComponents(stack, trade.item())) return trade;
        }
        return null;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        changed = true;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.vendingmachine.shipping_bin");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
