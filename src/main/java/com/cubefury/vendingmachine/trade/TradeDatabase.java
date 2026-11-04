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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/**
 * The catalogue. Each file in {@code data/<namespace>/vendingmachine/trades/} is one trade. A data pack reload
 * replaces the whole catalogue.
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
        trades = parse(files, registries.createSerializationContext(JsonOps.INSTANCE));
        VendingMachine.LOG.info("Loaded {} vending machine trades", trades.size());
    }

    /** Parses trade files in id order; a broken file is logged and skipped. */
    public static List<Trade> parse(Map<ResourceLocation, JsonElement> files, DynamicOps<JsonElement> ops) {
        List<Trade> parsed = new ArrayList<>();
        new TreeMap<>(files).forEach(
            (id, json) -> Trade.CODEC.parse(ops, json)
                .resultOrPartial(err -> VendingMachine.LOG.error("Skipping trade {}: {}", id, err))
                .ifPresent(parsed::add));
        return List.copyOf(parsed);
    }
}
