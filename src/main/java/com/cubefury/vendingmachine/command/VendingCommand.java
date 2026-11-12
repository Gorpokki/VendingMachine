package com.cubefury.vendingmachine.command;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.trade.Wallets;
import com.cubefury.vendingmachine.util.Currency;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

/**
 * {@code /vending add|set|reset|reload}, the original's admin commands. The wallet subcommands change one coin type, or
 * every coin with {@code all}, in the wallet of the sender or of a named online player; {@code reload database}
 * reloads the data packs, and with them the trade catalogue.
 */
public final class VendingCommand {

    /** The original command kept CommandBase's default permission level. */
    public static final int PERMISSION = 4;

    private static final String PLAYER = "player";
    private static final String COIN = "coin";
    private static final String AMOUNT = "amount";
    /** {@code all} parses as this id; no item has it. */
    private static final ResourceLocation ALL = ResourceLocation.withDefaultNamespace("all");
    private static final SuggestionProvider<CommandSourceStack> COINS = (c, builder) -> SharedSuggestionProvider
        .suggest(
            Stream.concat(
                Currency.denominations()
                    .stream()
                    .map(d -> id(d.item())),
                Stream.of("all")),
            builder);

    private VendingCommand() {}

    private interface WalletEdit {

        int run(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException;
    }

    private interface AmountEdit {

        void run(CommandSourceStack source, Wallets wallets, ServerPlayer target, Item coin, int amount);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("vending")
                .requires(s -> s.hasPermission(PERMISSION))
                .executes(c -> usage(c, "/vending <add|set|reset|reload> [...] "))
                .then(
                    targeted(
                        "add",
                        "/vending add [player] <coin type> <amount>",
                        true,
                        (c, target) -> withAmount(c, target, VendingCommand::add)))
                .then(
                    targeted(
                        "set",
                        "/vending set [player] <coin type> <amount>",
                        true,
                        (c, target) -> withAmount(c, target, VendingCommand::set)))
                .then(targeted("reset", "/vending reset [player] <coin type>", false, VendingCommand::reset))
                .then(
                    Commands.literal("reload")
                        .executes(c -> usage(c, "/vending reload database"))
                        .then(
                            Commands.literal("database")
                                .executes(VendingCommand::reload))));
    }

    /**
     * {@code name [player] <coin type> [amount]}. The branch without a player goes first, so input that reads both as
     * a coin id and as a player name is taken as the coin.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> targeted(String name, String usage, boolean withAmount,
        WalletEdit edit) {
        return Commands.literal(name)
            .executes(c -> usage(c, usage))
            .then(
                coin(
                    withAmount,
                    c -> edit.run(
                        c,
                        c.getSource()
                            .getPlayerOrException())))
            .then(
                Commands.argument(PLAYER, EntityArgument.player())
                    .then(coin(withAmount, c -> {
                        ServerPlayer target;
                        try {
                            target = EntityArgument.getPlayer(c, PLAYER);
                        } catch (CommandSyntaxException e) {
                            return fail(
                                c,
                                Component.translatable(
                                    "vendingmachine.command.error.player_not_found",
                                    input(c, PLAYER)));
                        }
                        return edit.run(c, target);
                    })));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> coin(boolean withAmount,
        Command<CommandSourceStack> command) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> coin = Commands
            .argument(COIN, ResourceLocationArgument.id())
            .suggests(COINS);
        return withAmount ? coin.then(
            Commands.argument(AMOUNT, StringArgumentType.word())
                .executes(command)) : coin.executes(command);
    }

    private static int withAmount(CommandContext<CommandSourceStack> c, ServerPlayer target, AmountEdit edit) {
        String entered = StringArgumentType.getString(c, AMOUNT);
        int amount;
        try {
            amount = Integer.parseInt(entered);
        } catch (NumberFormatException e) {
            return fail(c, Component.translatable("vendingmachine.command.error.integer_format", entered));
        }
        return forCoin(c, coin -> edit.run(c.getSource(), wallets(c), target, coin, amount));
    }

    /** Runs {@code action} with the coin argument's item, or with null for {@code all}. */
    private static int forCoin(CommandContext<CommandSourceStack> c, Consumer<Item> action) {
        ResourceLocation id = ResourceLocationArgument.getId(c, COIN);
        if (id.equals(ALL)) {
            action.accept(null);
            return 1;
        }
        for (Currency.Denomination d : Currency.denominations()) {
            if (BuiltInRegistries.ITEM.getKey(d.item())
                .equals(id)) {
                action.accept(d.item());
                return 1;
            }
        }
        return fail(
            c,
            Component.translatable(
                "vendingmachine.command.error.invalid_currency_with_list",
                input(c, COIN),
                currencyList()));
    }

    private static void add(CommandSourceStack source, Wallets wallets, ServerPlayer target, Item coin, int amount) {
        UUID id = target.getUUID();
        // the most coins of any one type actually added or removed
        long shown = 0;
        for (Item item : coinsOrAll(coin)) {
            // ponytail: a wallet holds real coins, so removing more than it has stops at zero instead of going negative
            long before = wallets.count(id, item);
            long after = Math.max(0, before + amount);
            wallets.set(id, item, after);
            shown = Math.max(shown, Math.abs(after - before));
        }
        String key = "vendingmachine.command.add." + (amount >= 0 ? "" : "remove_")
            + (coin == null ? "all_currency" : "specific_currency");
        success(
            source,
            coin == null ? Component.translatable(key, shown, target.getDisplayName())
                : Component.translatable(key, shown, coinName(coin), target.getDisplayName()));
    }

    private static void set(CommandSourceStack source, Wallets wallets, ServerPlayer target, Item coin, int entered) {
        int amount = Math.max(0, entered);
        for (Item item : coinsOrAll(coin)) {
            wallets.set(target.getUUID(), item, amount);
        }
        success(
            source,
            coin == null
                ? Component.translatable("vendingmachine.command.set.all_currency", amount, target.getDisplayName())
                : Component.translatable(
                    "vendingmachine.command.set.specific_currency",
                    coinName(coin),
                    amount,
                    target.getDisplayName()));
    }

    private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        return forCoin(c, coin -> {
            if (coin == null) {
                wallets(c).clear(target.getUUID());
                success(
                    c.getSource(),
                    Component.translatable("vendingmachine.command.reset.all_coins", target.getDisplayName()));
            } else {
                wallets(c).set(target.getUUID(), coin, 0);
                success(
                    c.getSource(),
                    Component.translatable(
                        "vendingmachine.command.reset.specific_coin",
                        coinName(coin),
                        target.getDisplayName()));
            }
        });
    }

    /** Reloads the selected data packs as {@code /reload} does, and reports once the new catalogue is in place. */
    private static int reload(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        MinecraftServer server = source.getServer();
        server.reloadResources(
            server.getPackRepository()
                .getSelectedIds())
            .thenRun(() -> success(source, Component.translatable("vendingmachine.command.reset.reloaded_trade_db")))
            .exceptionally(e -> {
                VendingMachine.LOG.warn("Failed to reload the trade database", e);
                source.sendFailure(Component.translatable("commands.reload.failure"));
                return null;
            });
        return 1;
    }

    private static List<Item> coinsOrAll(Item coin) {
        if (coin != null) return List.of(coin);
        return Currency.denominations()
            .stream()
            .map(Currency.Denomination::item)
            .toList();
    }

    private static Wallets wallets(CommandContext<CommandSourceStack> c) {
        return Wallets.get(
            c.getSource()
                .getServer());
    }

    private static int usage(CommandContext<CommandSourceStack> c, String usage) {
        c.getSource()
            .sendSystemMessage(Component.translatable("vendingmachine.command.usage", usage));
        return 0;
    }

    private static int fail(CommandContext<CommandSourceStack> c, Component message) {
        c.getSource()
            .sendFailure(message);
        return 0;
    }

    private static void success(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    /** What the sender typed for {@code argument}. */
    private static String input(CommandContext<CommandSourceStack> c, String argument) {
        for (ParsedCommandNode<CommandSourceStack> node : c.getNodes()) {
            if (node.getNode()
                .getName()
                .equals(argument)) {
                return node.getRange()
                    .get(c.getInput());
            }
        }
        return "";
    }

    /** The coin's name, showing the id this command accepts on hover. */
    private static MutableComponent coinName(Item coin) {
        return coin.getDescription()
            .copy()
            .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(id(coin)))));
    }

    /** The accepted coins' names, alternating aqua and green. */
    private static Component currencyList() {
        MutableComponent list = Component.empty();
        List<Currency.Denomination> coins = Currency.denominations();
        for (int i = 0; i < coins.size(); i++) {
            if (i > 0) list.append(", ");
            list.append(
                coinName(
                    coins.get(i)
                        .item()).withStyle(i % 2 == 0 ? ChatFormatting.AQUA : ChatFormatting.GREEN));
        }
        return list;
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item)
            .toString();
    }
}
