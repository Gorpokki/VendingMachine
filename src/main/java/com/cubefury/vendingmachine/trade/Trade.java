package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

/**
 * One catalogue entry: pay {@code price} in currency units and hand over {@code fromItems}, receive every stack in
 * {@code items}, as the original's {@code toItems}. The buyer must also hold {@code nonConsumedItems}, which the trade
 * does not take. {@code displayItem} stands in for the first stack on the store tile and list row, as the original's
 * {@code displayItem}. {@code sellPrice} is what a selling block may pay for the first stack of a one-stack trade.
 * {@code group} numbers the trade group the entry belongs to, or is {@link #NO_GROUP}, and {@code groupOrder} is its
 * place in that group. Every entry is open to every player, with no unlock and no limit.
 */
public record Trade(List<ItemStack> items, Optional<ItemStack> displayItem, int price, Optional<Integer> sellPrice,
    String category, List<ItemCost> fromItems, List<ItemCost> nonConsumedItems, int group, int groupOrder) {

    public static final int NO_GROUP = -1;

    public Trade {
        items = List.copyOf(items);
    }

    /** A one-stack, coins-only trade outside any group. */
    public Trade(ItemStack item, int price, Optional<Integer> sellPrice, String category) {
        this(List.of(item), Optional.empty(), price, sellPrice, category, List.of(), List.of(), NO_GROUP, 0);
    }

    /** The first stack bought. */
    public ItemStack item() {
        return items.get(0);
    }

    /** The stack the store shows for this trade: the display item, or else the first stack bought. */
    public ItemStack display() {
        return displayItem.orElse(item());
    }

    public Trade inGroup(int newGroup, int order) {
        return new Trade(
            items,
            displayItem,
            price,
            sellPrice,
            category,
            fromItems,
            nonConsumedItems,
            newGroup,
            order);
    }

    /** One stack under {@code item}, or several under {@code items}. */
    private static final MapCodec<List<ItemStack>> STACKS = Codec
        .mapEither(
            ItemStack.CODEC.fieldOf("item"),
            ExtraCodecs.nonEmptyList(ItemStack.CODEC.listOf())
                .fieldOf("items"))
        .xmap(
            either -> either.map(List::of, list -> list),
            list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));

    /** One file entry; a trade read from it is in no group until the database places it in one. */
    public static final Codec<Trade> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            STACKS.forGetter(Trade::items),
            ItemStack.CODEC.optionalFieldOf("display_item")
                .forGetter(Trade::displayItem),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("price", 0)
                .forGetter(Trade::price),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("sell_price")
                .forGetter(Trade::sellPrice),
            ExtraCodecs.NON_EMPTY_STRING.fieldOf("category")
                .forGetter(Trade::category),
            ItemCost.CODEC.listOf()
                .optionalFieldOf("from_items", List.of())
                .forGetter(Trade::fromItems),
            ItemCost.CODEC.listOf()
                .optionalFieldOf("non_consumed_items", List.of())
                .forGetter(Trade::nonConsumedItems))
            .apply(
                i,
                (items, displayItem, price, sellPrice, category, fromItems, nonConsumedItems) -> new Trade(
                    items,
                    displayItem,
                    price,
                    sellPrice,
                    category,
                    fromItems,
                    nonConsumedItems,
                    NO_GROUP,
                    0)));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> STACKS_STREAM = ItemStack.STREAM_CODEC
        .apply(ByteBufCodecs.list());

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemStack>> DISPLAY_STREAM = ByteBufCodecs
        .optional(ItemStack.STREAM_CODEC);

    private static final StreamCodec<ByteBuf, Optional<Integer>> SELL_STREAM = ByteBufCodecs
        .optional(ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, Trade> STREAM_CODEC = StreamCodec.of((buf, trade) -> {
        STACKS_STREAM.encode(buf, trade.items());
        DISPLAY_STREAM.encode(buf, trade.displayItem());
        buf.writeVarInt(trade.price());
        SELL_STREAM.encode(buf, trade.sellPrice());
        buf.writeUtf(trade.category());
        ItemCost.LIST_STREAM_CODEC.encode(buf, trade.fromItems());
        ItemCost.LIST_STREAM_CODEC.encode(buf, trade.nonConsumedItems());
        buf.writeVarInt(trade.group());
        buf.writeVarInt(trade.groupOrder());
    },
        buf -> new Trade(
            STACKS_STREAM.decode(buf),
            DISPLAY_STREAM.decode(buf),
            buf.readVarInt(),
            SELL_STREAM.decode(buf),
            buf.readUtf(),
            ItemCost.LIST_STREAM_CODEC.decode(buf),
            ItemCost.LIST_STREAM_CODEC.decode(buf),
            buf.readVarInt(),
            buf.readVarInt()));

    /**
     * Whether {@code inventory} holds both the items the trade takes and those it only asks to see. The taken items are
     * counted first, as the buy takes them, so one stack cannot both pay and stay.
     */
    public boolean itemsHeld(List<ItemStack> inventory) {
        List<ItemCost> costs = new ArrayList<>(fromItems);
        costs.addAll(nonConsumedItems);
        return ItemCost.take(inventory, costs, true);
    }

    /**
     * Pays the trade, all or nothing: checks the items in {@code inventory}, then pays a price above 0 through
     * {@code payCoins}, then takes {@code fromItems}. Returns false, taking nothing, when the items fall short or the
     * coins cannot pay; a price of 0 never calls {@code payCoins}.
     */
    public boolean pay(List<ItemStack> inventory, IntPredicate payCoins) {
        if (!itemsHeld(inventory) || price > 0 && !payCoins.test(price)) return false;
        ItemCost.take(inventory, fromItems, false);
        return true;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, List<Trade>> LIST_STREAM_CODEC = STREAM_CODEC
        .apply(ByteBufCodecs.list());
}
