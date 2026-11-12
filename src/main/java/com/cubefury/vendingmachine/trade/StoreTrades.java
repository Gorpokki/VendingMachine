package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.util.Currency;
import com.cubefury.vendingmachine.util.Currency.Denomination;

/**
 * The store trades a recipe viewer lists, as the original's NEI page did: each trade's price in coins and its items.
 * The server sends the catalogue to each client whenever it syncs data packs, on login and after {@code /reload},
 * just before the recipes that make JEI and EMI reload.
 */
public final class StoreTrades {

    /** One page entry: the coins that pay the price, largest first, and every stack bought. */
    public record Entry(List<ItemStack> price, List<ItemStack> outputs) {}

    /** The catalogue and the coins that pay it, as the server has them. */
    public record Sync(List<Trade> trades, List<Denomination> coins) implements CustomPacketPayload {

        public static final Type<Sync> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VendingMachine.MODID, "store_trades"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
            Trade.LIST_STREAM_CODEC,
            Sync::trades,
            Denomination.LIST_STREAM_CODEC,
            Sync::coins,
            Sync::new);

        @Override
        public Type<Sync> type() {
            return ID;
        }
    }

    // The list is immutable; the volatile field publishes the one each sync replaces.
    @SuppressWarnings("java:S3077")
    private static volatile List<Entry> synced = List.of();

    private StoreTrades() {}

    /** The entries from the last catalogue the server sent this client. */
    public static List<Entry> synced() {
        return synced;
    }

    public static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToClient(Sync.ID, Sync.STREAM_CODEC, (sync, context) -> receive(sync));
    }

    public static void receive(Sync sync) {
        synced = entries(sync.trades(), sync.coins());
    }

    public static void send(OnDatapackSyncEvent event) {
        Sync sync = new Sync(TradeDatabase.trades(), Currency.denominations());
        event.getRelevantPlayers()
            .forEach(player -> PacketDistributor.sendToPlayer(player, sync));
    }

    /** One entry per trade, in catalogue order. */
    public static List<Entry> entries(List<Trade> trades, List<Denomination> coins) {
        return trades.stream()
            .map(
                trade -> new Entry(
                    price(trade.price(), coins),
                    trade.items()
                        .stream()
                        .map(ItemStack::copy)
                        .toList()))
            .toList();
    }

    /** {@code price} as the fewest coins, one stack per coin, largest first. */
    private static List<ItemStack> price(long price, List<Denomination> coins) {
        long[] counts = Currency.split(price, coins);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) stacks.add(
                new ItemStack(
                    coins.get(i)
                        .item(),
                    (int) counts[i]));
        }
        return List.copyOf(stacks);
    }
}
