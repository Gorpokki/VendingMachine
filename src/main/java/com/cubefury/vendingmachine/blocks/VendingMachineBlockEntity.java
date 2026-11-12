package com.cubefury.vendingmachine.blocks;

import java.util.ArrayDeque;
import java.util.Deque;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.integration.MeStorageLink;
import com.cubefury.vendingmachine.integration.NumismaticsCards;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.Wallets;
import com.cubefury.vendingmachine.util.Currency;

/**
 * Holds the chute that bought items and ejected coins drop into through a saved queue. Players pay from their wallet
 * ({@link Wallets}), a Numismatics card or the ME storage of a connected AE2 network.
 */
public class VendingMachineBlockEntity extends BlockEntity {

    public static final int OUTPUT_SLOTS = 100;
    private static final int BASE_DELAY = 10;

    /**
     * Per-player credit saved by older versions, moved into the players' wallets as coins when the machine loads.
     * Credit the current coins cannot express stays here and is saved again, so it is never lost.
     */
    private final Map<UUID, Long> legacyCredit = new HashMap<>();
    public final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Stacks waiting to drop into the chute, one at a time, as in the original. */
    private final Deque<ItemStack> queue = new ArrayDeque<>();
    private boolean newInQueue;
    private int ticksSinceDrop;
    /** The player with the screen open; others are refused while that player still has it. */
    private ServerPlayer user;

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
    public void onLoad() {
        super.onLoad();
        if (legacyCredit.isEmpty() || level == null || level.getServer() == null) return;
        Wallets wallets = Wallets.get(level.getServer());
        legacyCredit.replaceAll((player, amount) -> moveToWallet(wallets, player, amount));
        legacyCredit.values()
            .removeIf(amount -> amount == 0);
        setChanged();
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

    /** True when {@code player} may open the screen: nobody else is still using the machine. */
    public boolean mayUse(Player player) {
        return user == null || user == player || !isUsing(user);
    }

    private boolean isUsing(ServerPlayer player) {
        return !player.isRemoved() && player.isAlive()
            && player.containerMenu instanceof VendingMachineMenu menu
            && menu.machine() == this;
    }

    /** The user while still using the machine, who hears its sounds at their own position; null for everyone else. */
    private Player activeUser() {
        return user != null && isUsing(user) ? user : null;
    }

    public void startUsing(ServerPlayer player) {
        user = player;
    }

    /** Makes the placing player the owner of the machine's AE2 node, the only player who may pay from its network. */
    public void placedBy(Player player) {
        if (meStorageLink != null) meStorageLink.setOwner(player);
    }

    public Wallets wallets() {
        return Wallets.get(level.getServer());
    }

    /** Puts coins into the player's wallet. */
    public void deposit(Player player, ItemStack coins) {
        wallets().add(player.getUUID(), List.of(coins));
        playSound(Registration.COIN_INSERT.get(), activeUser());
    }

    /**
     * Buys a trade. A Numismatics card pays first, as at a Numismatics Vendor; the player's wallet pays when there is
     * no card or the card cannot pay, and coins from a connected AE2 network's ME storage make up any shortfall. The
     * trade's item price comes out of the player's inventory. Every bought stack joins the queue to the chute. All or
     * nothing: with too little money, too few items, or no room left in the chute for everything already queued plus
     * every bought stack, nothing changes. An unformed machine sells nothing, as in the original.
     */
    public boolean buy(Player player, Trade trade, ItemStack card) {
        if (!formed() || stacksHeld() + trade.items()
            .size() > OUTPUT_SLOTS) return false;
        if (!trade.pay(player.getInventory().items,
            price -> payByCard(card, player, price) || payFromWallet(player, price))) return false;
        enqueue(
            trade.items()
                .stream()
                .map(ItemStack::copy)
                .toList());
        return true;
    }

    /** Stacks in the chute plus stacks queued for it; each queued stack will take a chute slot. */
    private int stacksHeld() {
        int held = queue.size();
        for (int i = 0; i < output.getSlots(); i++) {
            if (!output.getStackInSlot(i)
                .isEmpty()) held++;
        }
        return held;
    }

    private void enqueue(List<ItemStack> stacks) {
        if (stacks.isEmpty()) return;
        queue.addAll(stacks);
        newInQueue = true;
        setChanged();
    }

    /**
     * Drops the next queued stack into the first empty chute slot: at once when new stacks arrive, then every 10 ticks,
     * or every 10/ln(queue size) ticks once ln reaches 1, so a long queue drains faster. A full chute makes it wait.
     */
    public void tick() {
        if (newInQueue || !queue.isEmpty() && ticksSinceDrop % dropDelay() == 0) {
            dropNext();
            ticksSinceDrop = 0;
        }
        ticksSinceDrop = newInQueue ? 0 : ticksSinceDrop + 1;
        newInQueue = false;
    }

    public boolean formed() {
        return getBlockState().getValue(VendingMachineBlock.FORMED);
    }

    /** Ticks between drops for the current queue length. */
    public static int dropDelay(int queued) {
        double acceleration = Math.log(queued);
        return acceleration < 1 ? BASE_DELAY : (int) (BASE_DELAY / acceleration);
    }

    private int dropDelay() {
        return dropDelay(queue.size());
    }

    private void dropNext() {
        int slot = firstEmptySlot();
        if (queue.isEmpty() || slot < 0) return;
        ItemStack stack = queue.poll();
        output.setStackInSlot(slot, stack);
        SoundEvent sound = Currency.unitValue(stack) > 0 ? Registration.COIN_DROP.get() : Registration.ITEM_DROP.get();
        playSound(sound, activeUser());
    }

    private int firstEmptySlot() {
        for (int i = 0; i < output.getSlots(); i++) {
            if (output.getStackInSlot(i)
                .isEmpty()) return i;
        }
        return -1;
    }

    /** How many stacks wait to drop into the chute. */
    public int queued() {
        return queue.size();
    }

    private static boolean payByCard(ItemStack card, Player player, int price) {
        return !card.isEmpty() && Currency.countsInSpurs() && NumismaticsCards.pay(card, player, price);
    }

    /** Coins taken from ME storage go into the wallet first, so an overpaid coin stays in the wallet as change. */
    private boolean payFromWallet(Player player, int price) {
        Wallets wallets = wallets();
        long missing = price - wallets.total(player.getUUID());
        if (missing > 0 && meStorageLink != null) wallets.add(player.getUUID(), meStorageLink.take(player, missing));
        return wallets.pay(player.getUUID(), price);
    }

    /** Coins of each type the player may spend from the connected ME storage; 0 without AE2. */
    public long meCoins(Player player, Item coin) {
        return meStorageLink == null ? 0 : meStorageLink.available(player, coin);
    }

    /** Queues every coin in the player's wallet for the chute. */
    public void ejectCoins(Player player) {
        enqueue(wallets().takeAll(player.getUUID()));
    }

    /** Queues every coin of one type in the player's wallet for the chute. */
    public void ejectCoins(Player player, Item coin) {
        enqueue(wallets().takeOut(player.getUUID(), coin));
    }

    /** Moves every bought item into the player's inventory, as far as it has room. */
    public void takeOutputs(Player player) {
        PlayerMainInvWrapper inventory = new PlayerMainInvWrapper(player.getInventory());
        for (int i = 0; i < output.getSlots(); i++) {
            output.setStackInSlot(i, ItemHandlerHelper.insertItemStacked(inventory, output.getStackInSlot(i), false));
        }
    }

    /**
     * As in the original, each play varies: volume from 0.6 to 1.0 and pitch from 0.95 to 1.05. The machine's user
     * hears it where they stand; everyone else hears it at the machine.
     */
    private void playSound(SoundEvent sound, Player user) {
        RandomSource random = level.getRandom();
        float volume = 0.6F + 0.4F * random.nextFloat();
        float pitch = 0.95F + 0.1F * random.nextFloat();
        level.playSound(user, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        if (user != null) user.playNotifySound(sound, SoundSource.BLOCKS, volume, pitch);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < output.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output.getStackInSlot(i));
            output.setStackInSlot(i, ItemStack.EMPTY);
        }
        queue.forEach(stack -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack));
        queue.clear();
    }

    /** Puts {@code amount} into the wallet as coins and returns what the coins cannot express. */
    private static long moveToWallet(Wallets wallets, UUID player, long amount) {
        List<ItemStack> coins = Currency.makeChange(amount);
        wallets.add(player, coins);
        long left = amount - Currency.total(coins);
        if (left > 0) {
            VendingMachine.LOG
                .warn("Player {} has {} credit that the currency cannot pay out as coins", player, left);
        }
        return left;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", output.serializeNBT(registries));
        tag.put("queue", queueTag(registries));
        if (!legacyCredit.isEmpty()) tag.put("credit", creditTag());
        if (meStorageLink != null) meStorageLink.save(tag);
    }

    private ListTag queueTag(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        queue.forEach(stack -> list.add(stack.save(registries)));
        return list;
    }

    private ListTag creditTag() {
        ListTag list = new ListTag();
        legacyCredit.forEach((player, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putLong("amount", amount);
            list.add(entry);
        });
        return list;
    }

    /** Reads the chute; a chute saved with fewer slots by an older version keeps its items in the first slots. */
    private void loadOutput(CompoundTag saved, HolderLookup.Provider registries) {
        ItemStackHandler old = new ItemStackHandler();
        old.deserializeNBT(registries, saved);
        for (int i = 0; i < output.getSlots(); i++) {
            output.setStackInSlot(i, i < old.getSlots() ? old.getStackInSlot(i) : ItemStack.EMPTY);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadOutput(tag.getCompound("output"), registries);
        queue.clear();
        for (Tag t : tag.getList("queue", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t)
                .ifPresent(queue::add);
        }
        legacyCredit.clear();
        for (Tag t : tag.getList("credit", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            legacyCredit.merge(entry.getUUID("player"), entry.getLong("amount"), Long::sum);
        }
        if (meStorageLink != null) meStorageLink.load(tag);
    }
}
