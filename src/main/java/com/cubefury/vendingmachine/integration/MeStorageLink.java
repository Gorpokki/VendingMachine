package com.cubefury.vendingmachine.integration;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.util.Currency;
import com.cubefury.vendingmachine.util.Currency.Denomination;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;

/**
 * Only loaded when Applied Energistics 2 is present. Joins the machine to an adjacent AE2 network, so a purchase can
 * take coin items from that network's ME storage.
 */
public final class MeStorageLink implements IInWorldGridNodeHost {

    private final IManagedGridNode node;

    public MeStorageLink(VendingMachineBlockEntity machine) {
        node = GridHelper
            .createManagedNode(machine, (VendingMachineBlockEntity owner, IGridNode n) -> owner.setChanged())
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(0)
            .setVisualRepresentation(Registration.VENDING_MACHINE.get());
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            Registration.VENDING_MACHINE_BE.get(),
            (machine, context) -> machine.meStorageLink());
    }

    @Override
    public IGridNode getGridNode(Direction dir) {
        return node.getNode();
    }

    public void create(VendingMachineBlockEntity machine) {
        GridHelper.onFirstTick(machine, m -> node.create(m.getLevel(), m.getBlockPos()));
    }

    /**
     * Makes {@code player} the node's owner. Resolves the AE2 player id from the UUID, as AE2's own
     * {@code setOwningPlayer} does for a server player, so any server-side {@link Player} works.
     */
    public void setOwner(Player player) {
        IPlayerRegistry registry = IPlayerRegistry.getMapping(player.level());
        if (registry != null) node.setOwningPlayerId(registry.getPlayerId(player.getUUID()));
    }

    public void destroy() {
        node.destroy();
    }

    public void load(CompoundTag tag) {
        node.loadFromNBT(tag);
    }

    public void save(CompoundTag tag) {
        node.saveToNBT(tag);
    }

    /** The network's ME storage, or null when the machine is not on an active network. */
    public MEStorage storage() {
        var grid = node.getGrid();
        return node.isActive() && grid != null ? grid.getStorageService()
            .getInventory() : null;
    }

    /**
     * Takes coin items worth at least {@code amount} from ME storage and returns their value. Only the network's owner
     * may spend it. Simulates first and takes nothing, returning 0, unless the coins cover the whole amount.
     */
    public long take(Player player, long amount) {
        MEStorage storage = storage();
        List<Denomination> coins = Currency.denominations();
        if (storage == null || coins.isEmpty() || !isOwner(player)) return 0;
        IActionSource source = IActionSource.ofPlayer(player);
        return Currency.cover(amount, available(storage, coins, source))
            .map(take -> extract(storage, coins, take, source))
            .orElse(0L);
    }

    /** How many of each coin the storage holds, by a simulated extraction. */
    private static long[] available(MEStorage storage, List<Denomination> coins, IActionSource source) {
        long[] available = new long[coins.size()];
        for (int i = 0; i < available.length; i++) {
            available[i] = storage.extract(key(coins, i), Long.MAX_VALUE, Actionable.SIMULATE, source);
        }
        return available;
    }

    /** Extracts {@code take[i]} of each coin and returns the value of the coins that came out. */
    private static long extract(MEStorage storage, List<Denomination> coins, long[] take, IActionSource source) {
        long value = 0;
        for (int i = 0; i < take.length; i++) {
            if (take[i] == 0) continue;
            long got = storage.extract(key(coins, i), take[i], Actionable.MODULATE, source);
            value += got * coins.get(i)
                .value();
        }
        return value;
    }

    private boolean isOwner(Player player) {
        return player.getUUID()
            .equals(node.getNode()
                .getOwningPlayerProfileId());
    }

    private static AEItemKey key(List<Denomination> coins, int i) {
        return AEItemKey.of(coins.get(i)
            .item());
    }
}
