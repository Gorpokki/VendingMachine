package com.cubefury.vendingmachine.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;

import com.cubefury.vendingmachine.trade.ItemCost;
import com.cubefury.vendingmachine.trade.Trade;

/**
 * Favourites kept per world, as in the original: each entry is {@code "<world>|<trade>"}. The world is
 * {@code SP_<save folder>} in single player, {@code LAN_<host>} for a private-network server (the port changes
 * each time a world is opened to the network, so it is left out) and {@code MP_<host:port>} for any other server.
 * Kept free of client screen code so a test can check it.
 */
// The lists come from NeoForge config values, which its API types as List<? extends String>.
@SuppressWarnings("java:S4968")
public final class WorldFavourites {

    private static final Pattern PRIVATE = Pattern
        .compile("(?i)localhost|127\\.0\\.0\\.1|192\\.168\\..*|10\\..*|172\\.(1[6-9]|2\\d|3[01])\\..*");
    private static final String SEPARATOR = "|";

    private WorldFavourites() {}

    /** The world's key from the single-player save folder, or else the server address. */
    public static String worldKey(String saveFolder, String serverAddress) {
        if (saveFolder != null) return sanitize("SP_" + saveFolder);
        String address = serverAddress == null ? "" : serverAddress;
        int colon = address.indexOf(':');
        String host = colon < 0 ? address : address.substring(0, colon);
        return sanitize(PRIVATE.matcher(host).matches() ? "LAN_" + host : "MP_" + address);
    }

    private static String sanitize(String key) {
        return key.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    public static boolean contains(List<? extends String> entries, String world, String trade) {
        return entries.contains(world + SEPARATOR + trade);
    }

    public static List<String> toggle(List<? extends String> entries, String world, String trade) {
        List<String> next = new ArrayList<>(entries);
        String entry = world + SEPARATOR + trade;
        if (!next.remove(entry)) next.add(entry);
        return next;
    }

    /** The favourites the earlier version kept for every world, moved to {@code world}. */
    public static List<String> adopt(List<? extends String> entries, List<? extends String> global, String world) {
        List<String> next = new ArrayList<>(entries);
        global.forEach(trade -> next.add(world + SEPARATOR + trade));
        return next;
    }

    /**
     * The key of a trade: its first stack, its price, then the items it takes or holds, which only join the key when
     * present so coin-only keys keep their old form.
     */
    // ponytail: extra output stacks, display item and NBT are left out, so such trades share one favourite.
    public static String tradeKey(Trade trade) {
        String key = BuiltInRegistries.ITEM.getKey(trade.item().getItem()) + " " + trade.item().getCount() + " "
            + trade.price();
        if (!trade.fromItems().isEmpty()) key += " takes " + costKeys(trade.fromItems());
        if (!trade.nonConsumedItems().isEmpty()) key += " holds " + costKeys(trade.nonConsumedItems());
        return key;
    }

    private static String costKeys(List<ItemCost> costs) {
        return costs.stream()
            .map(cost -> cost.count() + " " + BuiltInRegistries.ITEM.getKey(cost.item().value())
                + cost.tag().map(tag -> " #" + tag.location()).orElse(""))
            .collect(Collectors.joining(", "));
    }
}
