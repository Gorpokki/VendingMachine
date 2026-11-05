package com.cubefury.vendingmachine.blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.integration.MeStorageLink;
import com.cubefury.vendingmachine.integration.NumismaticsCards;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.Currency;

/**
 * Holds each player's credit (inserted coin value) and the output slots that bought items go into.
 */
public class VendingMachineBlockEntity extends BlockEntity {

    public static final int OUTPUT_SLOTS = 8;

    private final Map<UUID, Long> credit = new HashMap<>();
    public final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Null unless Applied Energistics 2 is installed. */
    private final MeStorageLink meStorageLink;

    public VendingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.VENDING_MACHINE_BE.get(), pos, state);
        meStorageLink = ModList.get()
            .isLoaded("ae2") ? new MeStorageLink(this) : null;
    }

    public MeStorageLink meStorageLink() {
        return meStorageLink;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (meStorageLink != null) meStorageLink.create(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (meStorageLink != null) meStorageLink.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (meStorageLink != null) meStorageLink.destroy();
    }

    public MenuProvider menuProvider(List<Trade> trades) {
        return new MenuProvider() {

            @Override
            public Component getDisplayName() {
                return getBlockState().getBlock()
                    .getName();
            }

            @Override
            public VendingMachineMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv,
                Player player) {
                return new VendingMachineMenu(id, inv, VendingMachineBlockEntity.this, trades);
            }
        };
    }

    public long credit(UUID player) {
        return credit.getOrDefault(player, 0L);
    }

    public void deposit(UUID player, long amount) {
        if (amount <= 0) return;
        credit.merge(player, amount, Long::sum);
        setChanged();
    }

    /**
     * Buys a trade. A Numismatics card pays first, as at a Numismatics Vendor; the player's credit pays when there is
     * no card or the card cannot pay, and coins from a connected AE2 network's ME storage make up any shortfall. All or
     * nothing: with too little money, or no room in the output for the whole stack, nothing changes.
     */
    public boolean buy(Player player, Trade trade, ItemStack card) {
        if (!fitsInOutput(trade.item())) return false;
        if (!payByCard(card, player, trade.price())) {
            long have = credit(player.getUUID());
            if (have < trade.price() && meStorageLink != null) {
                // Coins taken from ME storage become credit first, so an overpaid coin stays refundable.
                have += meStorageLink.take(player, trade.price() - have);
                setCredit(player.getUUID(), have);
            }
            if (have < trade.price()) return false;
            setCredit(player.getUUID(), have - trade.price());
        }
        ItemHandlerHelper.insertItemStacked(output, trade.item()
            .copy(), false);
        return true;
    }

    private static boolean payByCard(ItemStack card, Player player, int price) {
        return !card.isEmpty() && Currency.countsInSpurs() && NumismaticsCards.pay(card, player, price);
    }

    /** Gives the player's credit back as coins. Credit the denominations cannot express stays. */
    public void refund(Player player) {
        List<ItemStack> coins = change(player.getUUID(), credit(player.getUUID()));
        setCredit(player.getUUID(), credit(player.getUUID()) - Currency.total(coins));
        for (ItemStack coin : coins) {
            ItemHandlerHelper.giveItemToPlayer(player, coin);
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        credit.forEach((player, amount) -> {
            for (ItemStack coin : change(player, amount)) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), coin);
            }
        });
        credit.clear();
        for (int i = 0; i < output.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output.getStackInSlot(i));
            output.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    private static List<ItemStack> change(UUID player, long amount) {
        List<ItemStack> coins = Currency.makeChange(amount);
        long unpaid = amount - Currency.total(coins);
        if (unpaid > 0) {
            VendingMachine.LOG.warn("Player {} has {} credit that the currency cannot pay out as coins", player, unpaid);
        }
        return coins;
    }

    private void setCredit(UUID player, long amount) {
        if (amount <= 0) credit.remove(player);
        else credit.put(player, amount);
        setChanged();
    }

    private boolean fitsInOutput(ItemStack item) {
        return ItemHandlerHelper.insertItemStacked(output, item.copy(), true)
            .isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", output.serializeNBT(registries));
        ListTag list = new ListTag();
        credit.forEach((uuid, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", uuid);
            entry.putLong("amount", amount);
            list.add(entry);
        });
        tag.put("credit", list);
        if (meStorageLink != null) meStorageLink.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        output.deserializeNBT(registries, tag.getCompound("output"));
        credit.clear();
        for (Tag t : tag.getList("credit", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            credit.put(entry.getUUID("player"), entry.getLong("amount"));
        }
        if (meStorageLink != null) meStorageLink.load(tag);
    }
}
