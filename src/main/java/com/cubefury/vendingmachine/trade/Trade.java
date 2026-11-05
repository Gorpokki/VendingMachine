package com.cubefury.vendingmachine.trade;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One catalogue entry: pay {@code price} in currency units, receive {@code item}. {@code sellPrice} is what a selling
 * block may pay for the same stack. Every entry is open to every player, with no unlock and no limit.
 */
public record Trade(ItemStack item, int price, Optional<Integer> sellPrice, String category) {

    public static final Codec<Trade> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            ItemStack.CODEC.fieldOf("item")
                .forGetter(Trade::item),
            ExtraCodecs.POSITIVE_INT.fieldOf("price")
                .forGetter(Trade::price),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("sell_price")
                .forGetter(Trade::sellPrice),
            ExtraCodecs.NON_EMPTY_STRING.fieldOf("category")
                .forGetter(Trade::category))
            .apply(i, Trade::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Trade> STREAM_CODEC = StreamCodec.composite(
        ItemStack.STREAM_CODEC,
        Trade::item,
        ByteBufCodecs.VAR_INT,
        Trade::price,
        ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
        Trade::sellPrice,
        ByteBufCodecs.STRING_UTF8,
        Trade::category,
        Trade::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<Trade>> LIST_STREAM_CODEC = STREAM_CODEC
        .apply(ByteBufCodecs.list());
}
