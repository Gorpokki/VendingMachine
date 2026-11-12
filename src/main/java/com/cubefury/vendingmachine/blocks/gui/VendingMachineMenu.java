package com.cubefury.vendingmachine.blocks.gui;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

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
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.integration.NumismaticsCards;
import com.cubefury.vendingmachine.trade.Categories;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;
import com.cubefury.vendingmachine.util.Currency.Denomination;

/**
 * Slots: {@link #IN_SLOTS} coin slots that empty into the player's wallet, the card slot, the output slots, then the
 * player inventory. The screen places every slot. Button ids: {@link #BUTTON_EJECT_COINS},
 * {@link #BUTTON_TAKE_OUTPUTS}, {@link #BUTTON_COIN_ROW_SHOWN} and {@link #BUTTON_COIN_ROW_HIDDEN} tell whether the
 * card slot takes a card, {@code BUTTON_FIRST_TRADE + i} buys trade {@code i}, and {@link #ejectCoinButton} takes one
 * coin type out. The catalogue, coins and category tabs are sent once when the menu opens; the player's
 * paying power is synced while it is open.
 */
public class VendingMachineMenu extends AbstractContainerMenu {

    public static final int BUTTON_EJECT_COINS = 0;
    public static final int BUTTON_TAKE_OUTPUTS = 1;
    public static final int BUTTON_COIN_ROW_SHOWN = 2;
    public static final int BUTTON_COIN_ROW_HIDDEN = 3;
    public static final int BUTTON_FIRST_TRADE = 4;
    public static final int IN_SLOTS = 8;
    public static final int COIN_SLOT = 0;
    public static final int CARD_SLOT = IN_SLOTS;
    public static final int FIRST_OUTPUT_SLOT = CARD_SLOT + 1;
    public static final int FIRST_PLAYER_SLOT = FIRST_OUTPUT_SLOT + VendingMachineBlockEntity.OUTPUT_SLOTS;
    private static final TagKey<Item> CARDS = ItemTags
        .create(ResourceLocation.fromNamespaceAndPath("numismatics", "cards"));
    private static final TagKey<Item> AUTHORIZED_CARDS = ItemTags
        .create(ResourceLocation.fromNamespaceAndPath("numismatics", "authorized_cards"));

    private final Player player;
    private final VendingMachineBlockEntity machine; // null on the client
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final List<Trade> trades;
    private final List<Denomination> coins;
    private final List<Categories.Tab> tabs;
    private final IntSupplier cardBalance;
    private final IntSupplier[] walletCounts;
    private final IntSupplier[] meCounts;
    private final Container coinInput = new SimpleContainer(IN_SLOTS) {

        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    /** Held only while the menu is open; given back on close. */
    private final Container cardInput = new SimpleContainer(1);
    /** False while the client hides the coin row; the card slot then takes no card. */
    private boolean cardSlotOpen = true;

    public VendingMachineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, buf.readBlockPos(), Catalogue.read(buf));
    }

    public VendingMachineMenu(int id, Inventory inv, VendingMachineBlockEntity machine, List<Trade> trades) {
        this(id, inv, machine, machine.getBlockPos(), Catalogue.of(trades));
    }

    private VendingMachineMenu(int id, Inventory inv, VendingMachineBlockEntity machine, BlockPos pos,
        Catalogue catalogue) {
        super(Registration.VENDING_MACHINE_MENU.get(), id);
        this.player = inv.player;
        this.machine = machine;
        this.trades = catalogue.trades();
        this.coins = catalogue.coins();
        this.tabs = catalogue.tabs();
        this.pos = pos;
        this.access = machine == null ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(machine.getLevel(), pos);
        addMachineSlots(
            machine == null ? new ItemStackHandler(VendingMachineBlockEntity.OUTPUT_SLOTS) : machine.output);
        addPlayerSlots(inv);
        cardBalance = syncedValue(this::serverCardBalance);
        walletCounts = new IntSupplier[coins.size()];
        meCounts = new IntSupplier[coins.size()];
        for (int i = 0; i < coins.size(); i++) {
            Item coin = coins.get(i)
                .item();
            walletCounts[i] = syncedValue(() -> machine.wallets()
                .count(player.getUUID(), coin));
            meCounts[i] = syncedValue(() -> machine.meCoins(player, coin));
        }
    }

    /** Writes what the client menu reads when it opens: the machine, the catalogue, the coins and the tabs. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, List<Trade> trades) {
        buf.writeBlockPos(pos);
        Catalogue.of(trades).write(buf);
    }

    private void addMachineSlots(ItemStackHandler output) {
        for (int i = 0; i < IN_SLOTS; i++) {
            addSlot(new InSlot(coinInput, i));
        }
        addSlot(new CardSlot(cardInput));
        for (int i = 0; i < output.getSlots(); i++) {
            addSlot(new OutputSlot(output, i));
        }
    }

    private void addPlayerSlots(Inventory inv) {
        for (int i = 9; i < 36; i++) {
            addSlot(new Slot(inv, i, 0, 0));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(inv, i, 0, 0));
        }
    }

    /**
     * Syncs a non-negative value to the client as two 16-bit data slots, capped at {@link Integer#MAX_VALUE}. The
     * supplier runs on the server only.
     */
    private IntSupplier syncedValue(LongSupplier serverValue) {
        DataSlot low = addDataSlot(sixteenBits(serverValue, 0));
        DataSlot high = addDataSlot(sixteenBits(serverValue, 16));
        return () -> (low.get() & 0xFFFF) | (high.get() & 0xFFFF) << 16;
    }

    /** A data slot for the 16 bits of the capped server value that start at bit {@code shift}. */
    private DataSlot sixteenBits(LongSupplier serverValue, int shift) {
        if (machine == null) return DataSlot.standalone();
        return new DataSlot() {

            @Override
            public int get() {
                return (int) (Math.min(serverValue.getAsLong(), Integer.MAX_VALUE) >> shift & 0xFFFF);
            }

            @Override
            public void set(int value) {
                // Only the server holds this slot; it reads the value through get() and never takes one from here.
            }
        };
    }

    private long serverCardBalance() {
        ItemStack card = cardInput.getItem(0);
        return card.isEmpty() || !Currency.countsInSpurs() ? 0 : NumismaticsCards.balance(card, player);
    }

    /** The machine on the server; null on the client. */
    public VendingMachineBlockEntity machine() {
        return machine;
    }

    /**
     * Whether the machine's structure is complete. Until it is, as in the original, the screen lists no trades and
     * the machine takes no coins and gives nothing out.
     */
    public boolean formed() {
        return player.level()
            .getBlockState(pos)
            .getOptionalValue(VendingMachineBlock.FORMED)
            .orElse(false);
    }

    public List<Trade> trades() {
        return trades;
    }

    /** The coins the machine accepts, largest first. */
    public List<Denomination> coins() {
        return coins;
    }

    public List<Categories.Tab> tabs() {
        return tabs;
    }

    /** How many of {@code coins().get(coin)} the player's wallet holds. */
    public long walletCount(int coin) {
        return walletCounts[coin].getAsInt();
    }

    /** How many of {@code coins().get(coin)} the machine may take from ME storage for the player. */
    public long meCount(int coin) {
        return meCounts[coin].getAsInt();
    }

    /** What the card in the card slot can pay. */
    public long cardBalance() {
        return cardBalance.getAsInt();
    }

    /** Whether a purchase can pay {@code price}: by card alone, or from the wallet and ME storage together. */
    public boolean canPay(long price) {
        long coinValue = 0;
        for (int i = 0; i < coins.size(); i++) {
            coinValue += (walletCount(i) + meCount(i)) * coins.get(i)
                .value();
        }
        return cardBalance() >= price || coinValue >= price;
    }

    /** Whether the player can buy {@code trade} now: pay its price and hold its items. */
    public boolean canBuy(Trade trade) {
        return canPay(trade.price()) && trade.itemsHeld(player.getInventory().items);
    }

    /**
     * The button id that takes every coin of {@code coins().get(coin)} out of the wallet. The mapping is its own
     * inverse, so it also turns a button id back into the coin.
     */
    public static int ejectCoinButton(int coin) {
        return -1 - coin;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (machine == null || container != coinInput || !formed()) return;
        for (int i = 0; i < IN_SLOTS; i++) {
            ItemStack stack = coinInput.getItem(i);
            if (Currency.unitValue(stack) > 0) {
                coinInput.setItem(i, ItemStack.EMPTY);
                machine.deposit(player, stack);
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_COIN_ROW_SHOWN || id == BUTTON_COIN_ROW_HIDDEN) {
            cardSlotOpen = id == BUTTON_COIN_ROW_SHOWN;
            return true;
        }
        if (machine == null || !formed()) return false;
        if (id < 0) return ejectCoin(player, ejectCoinButton(id));
        if (id == BUTTON_EJECT_COINS) machine.ejectCoins(player);
        else if (id == BUTTON_TAKE_OUTPUTS) machine.takeOutputs(player);
        else return buy(player, id - BUTTON_FIRST_TRADE);
        return true;
    }

    private boolean ejectCoin(Player player, int coin) {
        if (coin >= coins.size()) return false;
        machine.ejectCoins(
            player,
            coins.get(coin)
                .item());
        return true;
    }

    private boolean buy(Player player, int index) {
        return index < trades.size() && machine.buy(player, trades.get(index), cardInput.getItem(0));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveToTarget(stack, index)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** Machine slots go to the player inventory; coins go to the IN slots and a card to the card slot. */
    private boolean moveToTarget(ItemStack stack, int from) {
        if (from < FIRST_PLAYER_SLOT) return moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true);
        if (Currency.unitValue(stack) > 0) return moveItemStackTo(stack, COIN_SLOT, COIN_SLOT + IN_SLOTS, false);
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

    /** Takes coins only. */
    private static class InSlot extends Slot {

        InSlot(Container container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Currency.unitValue(stack) > 0;
        }
    }

    private class CardSlot extends Slot {

        CardSlot(Container container) {
            super(container, 0, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return cardSlotOpen && isCard(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Bought items: take only, and shown only while it holds an item. */
    private class OutputSlot extends SlotItemHandler {

        OutputSlot(ItemStackHandler output, int index) {
            super(output, index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return hasItem();
        }

        @Override
        public void setChanged() {
            if (machine != null) machine.setChanged();
        }
    }

    /** The trades, coins and tabs the client menu reads when it opens, after the machine's position. */
    private record Catalogue(List<Trade> trades, List<Denomination> coins, List<Categories.Tab> tabs) {

        static Catalogue of(List<Trade> trades) {
            return new Catalogue(trades, Currency.denominations(), Categories.tabs(trades));
        }

        static Catalogue read(RegistryFriendlyByteBuf buf) {
            return new Catalogue(
                Trade.LIST_STREAM_CODEC.decode(buf),
                Denomination.LIST_STREAM_CODEC.decode(buf),
                Categories.Tab.LIST_STREAM_CODEC.decode(buf));
        }

        void write(RegistryFriendlyByteBuf buf) {
            Trade.LIST_STREAM_CODEC.encode(buf, trades);
            Denomination.LIST_STREAM_CODEC.encode(buf, coins);
            Categories.Tab.LIST_STREAM_CODEC.encode(buf, tabs);
        }
    }
}
