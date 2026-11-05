package com.cubefury.vendingmachine.trade;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One catalogue entry: pay {@code price} in currency units, receive every stack in {@code results}.
 */
public record Trade(int price, List<ItemStack> results) {

    public static final Codec<Trade> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("price")
                .forGetter(Trade::price),
            ExtraCodecs.nonEmptyList(ItemStack.CODEC.listOf())
                .fieldOf("results")
                .forGetter(Trade::results))
            .apply(i, Trade::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Trade> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        Trade::price,
        ItemStack.LIST_STREAM_CODEC,
        Trade::results,
        Trade::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<Trade>> LIST_STREAM_CODEC = STREAM_CODEC
        .apply(ByteBufCodecs.list());

    public ItemStack displayItem() {
        return results.get(0);
    }
}
