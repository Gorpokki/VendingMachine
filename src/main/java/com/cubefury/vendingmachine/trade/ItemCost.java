package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Items a trade asks for besides coins: {@code count} of {@code item}, or, as the original's ore dictionary
 * alternative, of any item in {@code tag}. Item data is ignored, as the original ignored NBT, and a damaged item never
 * counts.
 */
public record ItemCost(Holder<Item> item, int count, Optional<TagKey<Item>> tag) {

    public static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id")
                .forGetter(ItemCost::item),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1)
                .forGetter(ItemCost::count),
            TagKey.codec(Registries.ITEM)
                .optionalFieldOf("tag")
                .forGetter(ItemCost::tag))
            .apply(i, ItemCost::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemCost> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.holderRegistry(Registries.ITEM),
        ItemCost::item,
        ByteBufCodecs.VAR_INT,
        ItemCost::count,
        ByteBufCodecs.optional(
            ResourceLocation.STREAM_CODEC.map(id -> TagKey.create(Registries.ITEM, id), TagKey::location)),
        ItemCost::tag,
        ItemCost::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemCost>> LIST_STREAM_CODEC = STREAM_CODEC
        .apply(ByteBufCodecs.list());

    /** One of the item, for showing the cost. */
    public ItemStack display() {
        return new ItemStack(item);
    }

    /**
     * Takes every cost in {@code costs} out of {@code slots}, all or nothing. As in the original, each cost takes the
     * exact item first, from the last slot back, then items of its tag. Returns false, changing nothing, when the
     * slots fall short; with {@code simulate} it only checks.
     */
    public static boolean take(List<ItemStack> slots, List<ItemCost> costs, boolean simulate) {
        if (costs.isEmpty()) return true;
        List<ItemStack> copy = new ArrayList<>(slots.size());
        slots.forEach(stack -> copy.add(stack.copy()));
        for (ItemCost cost : costs) {
            int missing = takeFrom(copy, cost.count, stack -> stack.is(cost.item));
            if (cost.tag.isPresent()) missing = takeFrom(copy, missing, stack -> stack.is(cost.tag.get()));
            if (missing > 0) return false;
        }
        if (simulate) return true;
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i)
                .setCount(
                    copy.get(i)
                        .getCount());
        }
        return true;
    }

    /** Takes up to {@code wanted} matching undamaged items from {@code slots}; returns how many are still missing. */
    private static int takeFrom(List<ItemStack> slots, int wanted, Predicate<ItemStack> matches) {
        for (int i = slots.size() - 1; i >= 0 && wanted > 0; i--) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || stack.isDamaged() || !matches.test(stack)) continue;
            int taken = Math.min(wanted, stack.getCount());
            stack.shrink(taken);
            wanted -= taken;
        }
        return wanted;
    }
}
