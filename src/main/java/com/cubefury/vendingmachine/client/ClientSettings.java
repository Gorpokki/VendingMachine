package com.cubefury.vendingmachine.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.ModConfigSpec;

import com.cubefury.vendingmachine.trade.Trade;

/**
 * The screen choices each player keeps: the client config holds the display mode, sort mode, coin row and favourites
 * per world; the last search and open tab last until the game closes, as in the original.
 */
public final class ClientSettings {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.BooleanValue LIST_MODE = BUILDER.comment("Show trades as a list instead of tiles.")
        .define("list_mode", false);
    static final ModConfigSpec.BooleanValue SORT_ALPHABETICAL = BUILDER
        .comment("Sort trades by name instead of the smart order.")
        .define("sort_alphabetical", false);
    static final ModConfigSpec.BooleanValue SHOW_COINS = BUILDER.comment("Show the wallet's coins under the trades.")
        .define("show_coins", true);
    // NeoForge's config API types a string list as List<? extends String>.
    @SuppressWarnings("java:S4968")
    private static final ModConfigSpec.ConfigValue<List<? extends String>> GLOBAL_FAVOURITES = BUILDER
        .comment("Favourites from before they were kept per world; they move to the first world opened.")
        .defineListAllowEmpty("favourites", List.of(), () -> "", String.class::isInstance);
    // NeoForge's config API types a string list as List<? extends String>.
    @SuppressWarnings("java:S4968")
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FAVOURITES = BUILDER
        .comment("Favourite trades per world, as \"<world>|<item id> <count> <price>\".")
        .defineListAllowEmpty("world_favourites", List.of(), () -> "", String.class::isInstance);

    public static final ModConfigSpec SPEC = BUILDER.build();

    static String lastSearch = "";
    static int lastTab = TradeView.ALL;

    private ClientSettings() {}

    static void toggle(ModConfigSpec.BooleanValue value) {
        value.set(!value.get());
        value.save();
    }

    static boolean isFavourite(Trade trade) {
        String world = world();
        return WorldFavourites.contains(FAVOURITES.get(), world, WorldFavourites.tradeKey(trade));
    }

    static void toggleFavourite(Trade trade) {
        String world = world();
        FAVOURITES.set(WorldFavourites.toggle(FAVOURITES.get(), world, WorldFavourites.tradeKey(trade)));
        FAVOURITES.save();
    }

    /** The current world's key; the first call after the update moves the old global favourites to this world. */
    // ponytail: worked out on every lookup; cache it per joined world if sorting large catalogues gets slow.
    private static String world() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        String folder = server == null ? null
            : server.getWorldPath(LevelResource.ROOT)
                .normalize()
                .getFileName()
                .toString();
        String address = mc.getCurrentServer() == null ? null : mc.getCurrentServer().ip;
        String world = WorldFavourites.worldKey(folder, address);
        if (!GLOBAL_FAVOURITES.get()
            .isEmpty()) {
            FAVOURITES.set(WorldFavourites.adopt(FAVOURITES.get(), GLOBAL_FAVOURITES.get(), world));
            GLOBAL_FAVOURITES.set(List.of());
            GLOBAL_FAVOURITES.save();
        }
        return world;
    }
}
