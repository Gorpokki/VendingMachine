package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import com.cubefury.vendingmachine.VendingMachine;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The icon of each catalogue category, read from {@code data/<namespace>/vendingmachine/categories/}: each file a JSON
 * array of {@code {"name": "<category>", "icon": "<icon file name>"}}. Files are read in id order.
 */
public class Categories extends SimpleJsonResourceReloadListener {

    private static final String DIRECTORY = "vendingmachine/categories";
    private static final String UNKNOWN_ICON = "unknown";

    private static final Codec<String> ICON = ExtraCodecs.NON_EMPTY_STRING.validate(
        icon -> ResourceLocation.isValidPath(icon) ? DataResult.success(icon)
            : DataResult.error(() -> "Icon is not a valid file name: " + icon));

    /** A category tab: the category name and the icon file name under {@code textures/gui/icons/}. */
    public record Tab(String name, String icon) {

        private static final Codec<Tab> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                ExtraCodecs.NON_EMPTY_STRING.fieldOf("name")
                    .forGetter(Tab::name),
                ICON.fieldOf("icon")
                    .forGetter(Tab::icon))
                .apply(i, Tab::new));

        public static final StreamCodec<ByteBuf, List<Tab>> LIST_STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.STRING_UTF8, Tab::name, ByteBufCodecs.STRING_UTF8, Tab::icon, Tab::new)
            .apply(ByteBufCodecs.list());
    }

    // The list is immutable; the volatile field publishes the one a resource reload replaces.
    @SuppressWarnings("java:S3077")
    private static volatile List<Tab> listed = List.of();

    public Categories() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        publish(parse(files));
    }

    private static void publish(List<Tab> tabs) {
        listed = tabs;
    }

    /** Parses files in id order and entries in file order; a broken file or entry is logged and skipped. */
    private static List<Tab> parse(Map<ResourceLocation, JsonElement> files) {
        List<Tab> parsed = new ArrayList<>();
        new TreeMap<>(files).forEach((id, json) -> {
            if (!(json instanceof JsonArray entries)) {
                VendingMachine.LOG.error("Skipping category file {}: expected a JSON array of entries", id);
                return;
            }
            for (JsonElement entry : entries) {
                Tab.CODEC.parse(JsonOps.INSTANCE, entry)
                    .resultOrPartial(err -> VendingMachine.LOG.error("Skipping category in {}: {}", id, err))
                    .ifPresent(parsed::add);
            }
        });
        return List.copyOf(parsed);
    }

    /** The tabs for the categories in {@code trades}, using the loaded icon list. */
    public static List<Tab> tabs(List<Trade> trades) {
        return tabs(listed, trades);
    }

    /**
     * One tab per category in {@code trades}: listed categories first, in list order, then the others in catalogue
     * order with the unknown icon.
     */
    private static List<Tab> tabs(List<Tab> listed, List<Trade> trades) {
        Map<String, String> icons = new LinkedHashMap<>();
        listed.forEach(tab -> icons.putIfAbsent(tab.name(), tab.icon()));
        Map<String, Tab> tabs = new LinkedHashMap<>();
        icons.forEach((name, icon) -> tabs.put(name, null));
        for (Trade trade : trades) {
            tabs.put(trade.category(), new Tab(trade.category(), icons.getOrDefault(trade.category(), UNKNOWN_ICON)));
        }
        tabs.values()
            .removeIf(Objects::isNull);
        return List.copyOf(tabs.values());
    }
}
