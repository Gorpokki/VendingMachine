package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.util.Currency;
import com.cubefury.vendingmachine.util.Currency.Denomination;

/**
 * Every player's wallet: how many of each coin item the player holds. One wallet per player serves every machine, kept
 * in the overworld's saved data. Coins of an item that is no longer currency stay in the wallet until taken out, and
 * coins of an item that is not registered, such as one from a removed mod, are kept as saved.
 */
public final class Wallets extends SavedData {

    private static final String NAME = VendingMachine.MODID + "_wallets";
    private static final SavedData.Factory<Wallets> FACTORY = new SavedData.Factory<>(
        Wallets::new,
        (tag, registries) -> load(tag),
        null);

    private final Map<UUID, Map<Item, Long>> byPlayer = new HashMap<>();
    /** Saved coins whose item is not registered, by player and item id. */
    private final Map<String, CompoundTag> unknown = new HashMap<>();

    public static Wallets get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(FACTORY, NAME);
    }

    public long count(UUID player, Item coin) {
        return wallet(player).getOrDefault(coin, 0L);
    }

    /** Value of the player's coins that are currency now. */
    public long total(UUID player) {
        long total = 0;
        for (Denomination d : Currency.denominations()) {
            total += count(player, d.item()) * d.value();
        }
        return total;
    }

    public void add(UUID player, Item coin, long count) {
        if (count <= 0) return;
        byPlayer.computeIfAbsent(player, p -> new HashMap<>())
            .merge(coin, count, Long::sum);
        setDirty();
    }

    public void add(UUID player, List<ItemStack> coins) {
        for (ItemStack coin : coins) {
            add(player, coin.getItem(), coin.getCount());
        }
    }

    /**
     * Pays {@code price}, largest coins first. When the coins cannot match it exactly, the overpaid part comes back as
     * change. Takes nothing and returns false when the wallet holds too little.
     */
    public boolean pay(UUID player, long price) {
        List<Denomination> coins = Currency.denominations();
        long[] available = new long[coins.size()];
        for (int i = 0; i < available.length; i++) {
            available[i] = count(player, coins.get(i)
                .item());
        }
        Optional<long[]> cover = Currency.cover(price, available);
        if (cover.isEmpty()) return false;
        long[] take = cover.get();
        long paid = 0;
        for (int i = 0; i < take.length; i++) {
            Denomination d = coins.get(i);
            set(player, d.item(), available[i] - take[i]);
            paid += take[i] * d.value();
        }
        add(player, Currency.makeChange(paid - price));
        return true;
    }

    /** Takes every coin of one item out of the wallet. */
    public List<ItemStack> takeOut(UUID player, Item coin) {
        List<ItemStack> out = Currency.stacks(coin, count(player, coin));
        set(player, coin, 0);
        return out;
    }

    /** Takes every coin out of the wallet. */
    public List<ItemStack> takeAll(UUID player) {
        List<ItemStack> out = new ArrayList<>();
        wallet(player).forEach((coin, count) -> out.addAll(Currency.stacks(coin, count)));
        clear(player);
        return out;
    }

    /** Empties the wallet without making its coins into item stacks, so any balance clears. */
    public void clear(UUID player) {
        if (byPlayer.remove(player) != null) setDirty();
    }

    private Map<Item, Long> wallet(UUID player) {
        return byPlayer.getOrDefault(player, Map.of());
    }

    /** Sets how many of {@code coin} the player holds; zero or less takes them all out. */
    public void set(UUID player, Item coin, long count) {
        Map<Item, Long> wallet = byPlayer.get(player);
        if (wallet == null) {
            add(player, coin, count);
            return;
        }
        if (count > 0) wallet.put(coin, count);
        else wallet.remove(coin);
        if (wallet.isEmpty()) byPlayer.remove(player);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        unknown.forEach((player, coins) -> tag.put(player, coins.copy()));
        byPlayer.forEach((player, wallet) -> {
            CompoundTag coins = tag.getCompound(player.toString());
            wallet.forEach(
                (coin, count) -> coins.putLong(
                    BuiltInRegistries.ITEM.getKey(coin)
                        .toString(),
                    count));
            tag.put(player.toString(), coins);
        });
        return tag;
    }

    public static Wallets load(CompoundTag tag) {
        Wallets data = new Wallets();
        for (String player : tag.getAllKeys()) {
            CompoundTag coins = tag.getCompound(player);
            for (String id : coins.getAllKeys()) {
                data.load(player, id, coins.getLong(id));
            }
        }
        return data;
    }

    private void load(String player, String id, long count) {
        Item coin = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        if (coin == Items.AIR) {
            VendingMachine.LOG
                .warn("Player {} has {} of unregistered coin {} in the wallet; keeping it", player, count, id);
            unknown.computeIfAbsent(player, p -> new CompoundTag())
                .putLong(id, count);
            return;
        }
        add(UUID.fromString(player), coin, count);
    }
}
