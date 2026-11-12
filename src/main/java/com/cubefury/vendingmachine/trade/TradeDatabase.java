package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.util.Currency;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/**
 * The catalogue. Each file in {@code data/<namespace>/vendingmachine/trades/} is a JSON array of entries. An entry is a
 * trade, or a trade group as in the original: an object with a {@code category} and a {@code trades} array, whose
 * trades take the group's category. A data pack reload replaces the whole catalogue, and menus opened on the old
 * catalogue close.
 */
public class TradeDatabase extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "vendingmachine/trades";

    private static final String GROUP_TRADES = "trades";

    private static volatile List<Trade> trades = List.of();

    private final HolderLookup.Provider registries;

    public TradeDatabase(HolderLookup.Provider registries) {
        super(new Gson(), DIRECTORY);
        this.registries = registries;
    }

    public static List<Trade> trades() {
        return trades;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        load(files, registries.createSerializationContext(JsonOps.INSTANCE));
    }

    /** Replaces the catalogue with the entries parsed from {@code files}. */
    public static void load(Map<ResourceLocation, JsonElement> files, DynamicOps<JsonElement> ops) {
        trades = parse(files, ops);
        VendingMachine.LOG.info("Loaded {} vending machine trades", trades.size());
    }

    /**
     * Parses files in id order and entries in file order; a broken file or entry is logged and skipped whole, so an
     * entry naming a missing item never loads with that item dropped.
     */
    public static List<Trade> parse(Map<ResourceLocation, JsonElement> files, DynamicOps<JsonElement> ops) {
        List<Trade> parsed = new ArrayList<>();
        new TreeMap<>(files).forEach((id, json) -> {
            if (!(json instanceof JsonArray entries)) {
                VendingMachine.LOG.error("Skipping trade file {}: expected a JSON array of entries", id);
                return;
            }
            for (int i = 0; i < entries.size(); i++) {
                String where = id + "[" + i + "]";
                if (entries.get(i) instanceof JsonObject group && group.has(GROUP_TRADES)) {
                    parsed.addAll(parseGroup(where, group, parsed.size(), ops));
                } else {
                    parseTrade(where, entries.get(i), ops).ifPresent(parsed::add);
                }
            }
        });
        return List.copyOf(parsed);
    }

    /**
     * The group's trades in order, each with the group's category and numbered {@code number}, the catalogue index of
     * its first trade; a broken trade is logged and skipped.
     */
    private static List<Trade> parseGroup(String where, JsonObject group, int number, DynamicOps<JsonElement> ops) {
        if (!(group.get(GROUP_TRADES) instanceof JsonArray entries)) {
            VendingMachine.LOG.error("Skipping trade group {}: expected a JSON array of trades", where);
            return List.of();
        }
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            JsonElement entry = entries.get(i)
                .deepCopy();
            if (entry instanceof JsonObject trade) trade.add("category", group.get("category"));
            int order = trades.size();
            parseTrade(where + ".trades[" + i + "]", entry, ops).map(trade -> trade.inGroup(number, order))
                .ifPresent(trades::add);
        }
        return trades;
    }

    private static Optional<Trade> parseTrade(String where, JsonElement entry, DynamicOps<JsonElement> ops) {
        DataResult<Trade> parsed = Trade.CODEC.parse(ops, entry);
        parsed.error()
            .ifPresent(err -> VendingMachine.LOG.error("Skipping trade {}: {}", where, err.message()));
        return parsed.result()
            .filter(trade -> costsSomething(where, trade) && payable(where, trade));
    }

    private static boolean costsSomething(String where, Trade trade) {
        boolean ok = trade.price() > 0 || !trade.fromItems()
            .isEmpty();
        if (!ok) VendingMachine.LOG.error("Skipping trade {}: it has neither a price above 0 nor from_items", where);
        return ok;
    }

    private static boolean payable(String where, Trade trade) {
        boolean ok = Currency.payable(trade.price()) && trade.sellPrice()
            .map(Currency::payable)
            .orElse(true);
        if (!ok) {
            VendingMachine.LOG.error("Skipping trade {}: a price is not a multiple of the smallest coin", where);
        }
        return ok;
    }
}
