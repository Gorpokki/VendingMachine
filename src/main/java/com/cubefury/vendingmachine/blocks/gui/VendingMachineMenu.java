package com.cubefury.vendingmachine.blocks.gui;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;

/**
 * Button ids: {@link #BUTTON_REFUND} returns credit as coins; {@code BUTTON_FIRST_TRADE + i} buys trade {@code i}.
 * The catalogue is sent once when the menu opens.
 */
public class VendingMachineMenu extends AbstractContainerMenu {

    public static final int BUTTON_REFUND = 0;
    public static final int BUTTON_FIRST_TRADE = 1;
    public static final int COIN_SLOT = 0;
    public static final int CARD_SLOT = 1;
    private static final TagKey<Item> CARDS = ItemTags
        .create(ResourceLocation.fromNamespaceAndPath("numismatics", "cards"));
    private static final TagKey<Item> AUTHORIZED_CARDS = ItemTags
        .create(ResourceLocation.fromNamespaceAndPath("numismatics", "authorized_cards"));

    private final Player player;
    private final VendingMachineBlockEntity machine; // null on the client
    private final ContainerLevelAccess access;
    private final List<Trade> trades;
    private final DataSlot creditLow;
    private final DataSlot creditHigh;
    private final Container coinInput = new SimpleContainer(1) {

        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    /** Held only while the menu is open; given back on close. */
    private final Container cardInput = new SimpleContainer(1);

    public VendingMachineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, buf.readBlockPos(), Trade.LIST_STREAM_CODEC.decode(buf));
    }

    public VendingMachineMenu(int id, Inventory inv, VendingMachineBlockEntity machine, List<Trade> trades) {
        this(id, inv, machine, machine.getBlockPos(), trades);
    }

    private VendingMachineMenu(int id, Inventory inv, VendingMachineBlockEntity machine, BlockPos pos,
        List<Trade> trades) {
        super(Registration.VENDING_MACHINE_MENU.get(), id);
        this.player = inv.player;
        this.machine = machine;
        this.trades = trades;
        this.access = machine == null ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(machine.getLevel(), pos);
        addCoinAndCardSlots();
        addOutputSlots(machine == null ? new ItemStackHandler(VendingMachineBlockEntity.OUTPUT_SLOTS) : machine.output);
        addPlayerSlots(inv);
        creditLow = addDataSlot(machine == null ? DataSlot.standalone() : new CreditSlot(0));
        creditHigh = addDataSlot(machine == null ? DataSlot.standalone() : new CreditSlot(16));
    }

    private void addCoinAndCardSlots() {
        addSlot(new Slot(coinInput, 0, 8, 114) {

            @Override
            public boolean mayPlace(ItemStack stack) {
                return Currency.unitValue(stack) > 0;
            }
        });
        addSlot(new Slot(cardInput, 0, 80, 95) {

            @Override
            public boolean mayPlace(ItemStack stack) {
                return isCard(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
    }

    /** The machine's output slots; a client menu shows empty stand-ins that the server fills through sync. */
    private void addOutputSlots(ItemStackHandler output) {
        for (int i = 0; i < output.getSlots(); i++) {
            addSlot(new SlotItemHandler(output, i, 30 + i * 18, 114) {

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public void setChanged() {
                    if (machine != null) machine.setChanged();
                }
            });
        }
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    public List<Trade> trades() {
        return trades;
    }

    /** Credit as shown on the client; capped at {@link Integer#MAX_VALUE}. */
    public long credit() {
        return (creditLow.get() & 0xFFFFL) | ((creditHigh.get() & 0xFFFFL) << 16);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (machine == null || container != coinInput) return;
        ItemStack coins = coinInput.getItem(0);
        long value = Currency.valueOf(coins);
        if (value > 0) {
            coinInput.setItem(0, ItemStack.EMPTY);
            machine.deposit(player.getUUID(), value);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) return false;
        if (id == BUTTON_REFUND) {
            machine.refund(player);
            return true;
        }
        int index = id - BUTTON_FIRST_TRADE;
        if (index < 0 || index >= trades.size()) return false;
        return machine.buy(player, trades.get(index), cardInput.getItem(0));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!move(stack, index)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** Moves a machine slot's stack to the player, or a player's coin or card to its slot. Returns whether any did. */
    private boolean move(ItemStack stack, int index) {
        int playerStart = 2 + VendingMachineBlockEntity.OUTPUT_SLOTS;
        if (index < playerStart) return moveItemStackTo(stack, playerStart, slots.size(), true);
        if (Currency.unitValue(stack) > 0) return moveItemStackTo(stack, COIN_SLOT, COIN_SLOT + 1, false);
        return isCard(stack) && moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false);
    }

    /** Closes the menu when a reload replaced the catalogue it was opened with. */
    @Override
    public boolean stillValid(Player player) {
        return (machine == null || trades == TradeDatabase.trades())
            && stillValid(access, player, Registration.VENDING_MACHINE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, coinInput);
        clearContainer(player, cardInput);
    }

    private static boolean isCard(ItemStack stack) {
        return stack.is(CARDS) || stack.is(AUTHORIZED_CARDS);
    }

    private class CreditSlot extends DataSlot {

        private final int shift;

        CreditSlot(int shift) {
            this.shift = shift;
        }

        @Override
        public int get() {
            long shown = Math.min(machine.credit(player.getUUID()), Integer.MAX_VALUE);
            return (int) ((shown >> shift) & 0xFFFF);
        }

        @Override
        public void set(int value) {
            // Credit lives in the machine; the server reads it through get() and never takes a value from here.
        }
    }
}
