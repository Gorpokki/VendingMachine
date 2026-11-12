package com.cubefury.vendingmachine.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.Item;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.command.VendingCommand;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.Wallets;
import com.cubefury.vendingmachine.util.Currency;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;

/** {@code /vending}, run through the server's command dispatcher as an operator would type it. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class VendingCommandGameTests {

    private static final String RESET_ALL = "vending reset all";

    private VendingCommandGameTests() {}

    /** Collects what a command tells its sender. */
    private static final class Feedback implements CommandSource {

        final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        boolean said(String key) {
            return messages.stream()
                .anyMatch(m -> has(m, key));
        }

        /** Whether a message with {@code key} reported {@code amount} as one of its arguments. */
        boolean said(String key, long amount) {
            return messages.stream()
                .anyMatch(
                    m -> m.getContents() instanceof TranslatableContents t && t.getKey()
                        .equals(key)
                        && Arrays.stream(t.getArgs())
                            .anyMatch(a -> a instanceof Number n && n.longValue() == amount));
        }

        private static boolean has(Component message, String key) {
            return message.getContents() instanceof TranslatableContents t && t.getKey()
                .equals(key)
                || message.getSiblings()
                    .stream()
                    .anyMatch(m -> has(m, key));
        }
    }

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name));
    }

    private static CommandSourceStack source(ServerPlayer player, int permission, Feedback feedback) {
        return player.createCommandSourceStack()
            .withSource(feedback)
            .withPermission(permission);
    }

    private static void run(CommandSourceStack source, String command) {
        source.getServer()
            .getCommands()
            .performPrefixedCommand(source, command);
    }

    private static Wallets wallets(GameTestHelper h) {
        return Wallets.get(
            h.getLevel()
                .getServer());
    }

    /**
     * A fake player in the player list, so commands can find it by name. A mock player with a real login would make
     * Numismatics and Create send it packets its embedded connection cannot carry; a fake player's connection drops
     * them.
     */
    private static ServerPlayer join(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = FakePlayerFactory.get(
            h.getLevel(),
            new GameProfile(
                id,
                "vm" + id.toString()
                    .substring(0, 8)));
        players(h).add(player);
        return player;
    }

    /** The player list's own list; NeoForge's getPlayers() is a read-only view of it. */
    private static List<ServerPlayer> players(GameTestHelper h) {
        return ObfuscationReflectionHelper.getPrivateValue(
            PlayerList.class,
            h.getLevel()
                .getServer()
                .getPlayerList(),
            "players");
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile()
            .getName();
    }

    /**
     * Call in a {@code finally}: a fake player left in the list after a failed assertion crashes the server once it
     * syncs the time to a connection with no channel.
     */
    private static void leave(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            players(h).remove(player);
        }
    }

    @GameTest(template = "empty")
    public static void addGivesAndRemovesCoins(GameTestHelper h) {
        ServerPlayer op = join(h);
        try {
            Feedback feedback = new Feedback();
            CommandSourceStack source = source(op, VendingCommand.PERMISSION, feedback);
            UUID id = op.getUUID();
            run(source, "vending add numismatics:cog 3");
            h.assertTrue(wallets(h).count(id, coin("cog")) == 3, "add should give the sender 3 cogs");
            h.assertTrue(feedback.said("vendingmachine.command.add.specific_currency"), "add should report the cogs");
            run(source, "vending add numismatics:cog -5");
            h.assertTrue(
                wallets(h).count(id, coin("cog")) == 0,
                "removing more than the wallet holds should stop at 0");
            h.assertTrue(
                feedback.said("vendingmachine.command.add.remove_specific_currency", 3),
                "a negative add should report the 3 cogs it removed, not the 5 asked for");
            h.succeed();
        } finally {
            leave(h, op);
        }
    }

    @GameTest(template = "empty")
    public static void addAllToNamedPlayer(GameTestHelper h) {
        ServerPlayer op = join(h);
        ServerPlayer target = join(h);
        try {
            Feedback feedback = new Feedback();
            run(source(op, VendingCommand.PERMISSION, feedback), "vending add " + name(target) + " all 2");
            for (Currency.Denomination d : Currency.denominations()) {
                h.assertTrue(wallets(h).count(target.getUUID(), d.item()) == 2, "the target should get 2 of each coin");
            }
            h.assertTrue(wallets(h).total(op.getUUID()) == 0, "the sender's wallet should be untouched");
            h.assertTrue(feedback.said("vendingmachine.command.add.all_currency"), "add all should report every coin");
            h.succeed();
        } finally {
            leave(h, op, target);
        }
    }

    @GameTest(template = "empty")
    public static void setReplacesCount(GameTestHelper h) {
        ServerPlayer op = join(h);
        ServerPlayer target = join(h);
        try {
            Feedback feedback = new Feedback();
            CommandSourceStack source = source(op, VendingCommand.PERMISSION, feedback);
            wallets(h).add(op.getUUID(), coin("spur"), 50);
            run(source, "vending set numismatics:spur 7");
            h.assertTrue(wallets(h).count(op.getUUID(), coin("spur")) == 7, "set should leave the sender 7 spurs");
            h.assertTrue(feedback.said("vendingmachine.command.set.specific_currency", 7), "set should report 7 spurs");
            run(source, "vending set numismatics:spur -4");
            h.assertTrue(wallets(h).count(op.getUUID(), coin("spur")) == 0, "a negative set should stop at 0");
            h.assertTrue(
                feedback.said("vendingmachine.command.set.specific_currency", 0),
                "a negative set should report the 0 it stored, not -4");
            run(source, "vending set " + name(target) + " all 1");
            for (Currency.Denomination d : Currency.denominations()) {
                h.assertTrue(
                    wallets(h).count(target.getUUID(), d.item()) == 1,
                    "the target should hold 1 of each coin");
            }
            h.assertTrue(feedback.said("vendingmachine.command.set.all_currency"), "set all should report every coin");
            h.succeed();
        } finally {
            leave(h, op, target);
        }
    }

    @GameTest(template = "empty")
    public static void resetEmptiesCoins(GameTestHelper h) {
        ServerPlayer op = join(h);
        try {
            Feedback feedback = new Feedback();
            CommandSourceStack source = source(op, VendingCommand.PERMISSION, feedback);
            UUID id = op.getUUID();
            wallets(h).add(id, coin("cog"), 4);
            wallets(h).add(id, coin("spur"), 5);
            run(source, "vending reset numismatics:cog");
            h.assertTrue(wallets(h).count(id, coin("cog")) == 0, "reset should take the cogs");
            h.assertTrue(wallets(h).count(id, coin("spur")) == 5, "reset of cogs should keep the spurs");
            h.assertTrue(feedback.said("vendingmachine.command.reset.specific_coin"), "reset should report the cogs");
            run(source, RESET_ALL);
            h.assertTrue(wallets(h).total(id) == 0, "reset all should empty the wallet");
            h.assertTrue(feedback.said("vendingmachine.command.reset.all_coins"), "reset all should report every coin");
            h.succeed();
        } finally {
            leave(h, op);
        }
    }

    /** A balance far too big to hold as item stacks still resets, without running out of memory. */
    @GameTest(template = "empty")
    public static void resetAllEmptiesHugeWallet(GameTestHelper h) {
        ServerPlayer op = join(h);
        try {
            Feedback feedback = new Feedback();
            CommandSourceStack source = source(op, VendingCommand.PERMISSION, feedback);
            run(source, "vending set all " + Integer.MAX_VALUE);
            run(source, RESET_ALL);
            h.assertTrue(wallets(h).total(op.getUUID()) == 0, "reset all should empty a huge wallet");
            h.assertTrue(feedback.said("vendingmachine.command.reset.all_coins"), "reset all should report every coin");
            h.succeed();
        } finally {
            leave(h, op);
        }
    }

    @GameTest(template = "empty")
    public static void badArgumentsChangeNothing(GameTestHelper h) {
        ServerPlayer op = join(h);
        try {
            Feedback feedback = new Feedback();
            CommandSourceStack source = source(op, VendingCommand.PERMISSION, feedback);
            run(source, "vending add numismatics:cog many");
            h.assertTrue(
                feedback.said("vendingmachine.command.error.integer_format"),
                "a bad amount should be refused");
            run(source, "vending add minecraft:dirt 1");
            h.assertTrue(
                feedback.said("vendingmachine.command.error.invalid_currency_with_list"),
                "dirt should be refused as a coin type");
            run(source, "vending add nobody_online numismatics:cog 1");
            h.assertTrue(
                feedback.said("vendingmachine.command.error.player_not_found"),
                "an offline player should be refused");
            // usage goes to the sender's player when there is one, so ask from the console
            run(
                h.getLevel()
                    .getServer()
                    .createCommandSourceStack()
                    .withSource(feedback),
                "vending add");
            h.assertTrue(feedback.said("vendingmachine.command.usage"), "a bare subcommand should show its usage");
            h.assertTrue(wallets(h).total(op.getUUID()) == 0, "refused commands should change no wallet");
            h.succeed();
        } finally {
            leave(h, op);
        }
    }

    @GameTest(template = "empty")
    public static void needsPermissionLevelFour(GameTestHelper h) {
        ServerPlayer player = join(h);
        try {
            Feedback feedback = new Feedback();
            CommandSourceStack below = source(player, VendingCommand.PERMISSION - 1, feedback);
            for (String command : List.of(
                "vending add numismatics:cog 5",
                "vending set numismatics:cog 5",
                RESET_ALL,
                "vending reload database")) {
                run(below, command);
            }
            h.assertTrue(wallets(h).total(player.getUUID()) == 0, "a level 3 sender should not change the wallet");
            h.assertFalse(
                feedback.said("vendingmachine.command.reset.reloaded_trade_db"),
                "a level 3 sender should not reload");
            h.assertFalse(
                below.getServer()
                    .getCommands()
                    .getDispatcher()
                    .getRoot()
                    .getChild("vending")
                    .canUse(below),
                "/vending should be hidden below level 4");
            run(source(player, VendingCommand.PERMISSION, feedback), "vending add numismatics:cog 5");
            h.assertTrue(wallets(h).count(player.getUUID(), coin("cog")) == 5, "a level 4 sender should add coins");
            h.succeed();
        } finally {
            leave(h, player);
        }
    }

    /** In its own batch: reloading data packs while other tests run would swap their recipes and structures. */
    @GameTest(template = "empty", batch = "vending_reload", timeoutTicks = 600)
    public static void reloadReplacesCatalogue(GameTestHelper h) {
        // from the console: a reload syncs data packs to every listed player, and a fake player has no channel
        Feedback feedback = new Feedback();
        // a catalogue no data pack holds, so the reload's catalogue is a new list even when the packs have no trades
        TradeDatabase.load(
            Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "before_reload"),
                JsonParser.parseString(
                    "[{\"item\": {\"id\": \"minecraft:bread\"}, \"price\": 1, \"category\": \"food\"}]")),
            h.getLevel()
                .registryAccess()
                .createSerializationContext(JsonOps.INSTANCE));
        List<?> before = TradeDatabase.trades();
        run(
            h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withSource(feedback),
            "vending reload database");
        h.succeedWhen(() -> {
            h.assertTrue(feedback.said("vendingmachine.command.reset.reloaded_trade_db"), "reload should finish");
            h.assertTrue(TradeDatabase.trades() != before, "reload should load the catalogue again");
        });
    }
}
