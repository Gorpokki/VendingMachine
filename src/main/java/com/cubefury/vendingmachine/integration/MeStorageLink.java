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
     * Takes coin items worth at least {@code amount} from ME storage and returns their value. Simulates first and takes
     * nothing, returning 0, unless the coins cover the whole amount.
     */
    public long take(Player player, long amount) {
        MEStorage storage = storage();
        List<Denomination> coins = Currency.denominations();
        if (storage == null || coins.isEmpty()) return 0;
        IActionSource source = IActionSource.ofPlayer(player);
        long[] available = new long[coins.size()];
        for (int i = 0; i < available.length; i++) {
            available[i] = storage.extract(key(coins, i), Long.MAX_VALUE, Actionable.SIMULATE, source);
        }
        long[] take = Currency.cover(amount, available);
        if (take == null) return 0;
        long value = 0;
        for (int i = 0; i < take.length; i++) {
            if (take[i] == 0) continue;
            long got = storage.extract(key(coins, i), take[i], Actionable.MODULATE, source);
            value += got * coins.get(i)
                .value();
        }
        return value;
    }

    private static AEItemKey key(List<Denomination> coins, int i) {
        return AEItemKey.of(coins.get(i)
            .item());
    }
}
