package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/**
 * The catalogue. Each file in {@code data/<namespace>/vendingmachine/trades/} is a JSON array of entries. A data pack
 * reload replaces the whole catalogue, and menus opened on the old catalogue close.
 */
public class TradeDatabase extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "vendingmachine/trades";

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

    /** Parses files in id order and entries in file order; a broken file or entry is logged and skipped. */
    public static List<Trade> parse(Map<ResourceLocation, JsonElement> files, DynamicOps<JsonElement> ops) {
        List<Trade> parsed = new ArrayList<>();
        new TreeMap<>(files).forEach((id, json) -> {
            if (!(json instanceof JsonArray entries)) {
                VendingMachine.LOG.error("Skipping trade file {}: expected a JSON array of entries", id);
                return;
            }
            for (int i = 0; i < entries.size(); i++) {
                String where = id + "[" + i + "]";
                Trade.CODEC.parse(ops, entries.get(i))
                    .resultOrPartial(err -> VendingMachine.LOG.error("Skipping trade {}: {}", where, err))
                    .filter(trade -> payable(where, trade))
                    .ifPresent(parsed::add);
            }
        });
        return List.copyOf(parsed);
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
