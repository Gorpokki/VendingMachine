package com.cubefury.vendingmachine.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import com.cubefury.vendingmachine.VMConfig;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.integration.NumismaticsCurrency;

/**
 * The coin items the machine accepts and what each is worth. Configured in the common config; by default the
 * Create: Numismatics coins, valued in spurs by Numismatics itself.
 */
public final class Currency {

    public record Denomination(Item item, int value) {

        public static final StreamCodec<RegistryFriendlyByteBuf, List<Denomination>> LIST_STREAM_CODEC = StreamCodec
            .composite(
                ByteBufCodecs.registry(Registries.ITEM),
                Denomination::item,
                ByteBufCodecs.VAR_INT,
                Denomination::value,
                Denomination::new)
            .apply(ByteBufCodecs.list());
    }

    private record Table(List<Denomination> denominations, boolean inSpurs) {}

    // The table is immutable; the volatile field publishes the one a config reload replaces.
    @SuppressWarnings("java:S3077")
    private static volatile Table table;

    private Currency() {}

    public static void invalidate() {
        table = null;
    }

    /** Denominations, largest value first. */
    public static List<Denomination> denominations() {
        return table().denominations();
    }

    /** Value of one item of this stack, or 0 if it is not currency. */
    public static int unitValue(ItemStack stack) {
        for (Denomination d : denominations()) {
            if (stack.is(d.item())) return d.value();
        }
        return 0;
    }

    public static long valueOf(ItemStack stack) {
        return (long) unitValue(stack) * stack.getCount();
    }

    /** Whether prices are counted in Numismatics spurs, so a Numismatics card can pay them. */
    public static boolean countsInSpurs() {
        return table().inSpurs();
    }

    /** Whether {@code amount} is a whole number of the smallest coin. Always true when no coins are accepted. */
    public static boolean payable(long amount) {
        List<Denomination> d = denominations();
        return d.isEmpty() || amount % d.get(d.size() - 1)
            .value() == 0;
    }

    /**
     * The denominations unchanged if each item is listed once and the smallest coin divides every other one,
     * otherwise none.
     */
    public static List<Denomination> usable(List<Denomination> denominations) {
        if (denominations.isEmpty()) return denominations;
        int unit = denominations.stream()
            .mapToInt(Denomination::value)
            .min()
            .getAsInt();
        Set<Item> items = new HashSet<>();
        for (Denomination d : denominations) {
            if (!accepted(d, unit, items)) return List.of();
        }
        return denominations;
    }

    /** Whether {@code d} is an item not yet in {@code items} and worth a multiple of {@code unit}. Logs why not. */
    private static boolean accepted(Denomination d, int unit, Set<Item> items) {
        if (!items.add(d.item())) {
            VendingMachine.LOG.error(
                "Coin {} is listed more than once; accepting no currency",
                BuiltInRegistries.ITEM.getKey(d.item()));
            return false;
        }
        if (d.value() % unit != 0) {
            VendingMachine.LOG.error(
                "Coin worth {} is not a multiple of the smallest coin ({}); accepting no currency",
                d.value(),
                unit);
            return false;
        }
        return true;
    }

    /**
     * Coin stacks worth as much of {@code amount} as the denominations can express, largest first. The caller keeps
     * whatever is left over.
     */
    public static List<ItemStack> makeChange(long amount) {
        List<Denomination> coins = denominations();
        long[] counts = split(amount, coins);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            out.addAll(stacks(coins.get(i)
                .item(), counts[i]));
        }
        return out;
    }

    /**
     * How many of each of {@code coins}, largest first, make up as much of {@code amount} as they can express, using
     * the fewest coins.
     */
    public static long[] split(long amount, List<Denomination> coins) {
        long[] counts = new long[coins.size()];
        long rest = amount;
        for (int i = 0; i < counts.length; i++) {
            long value = coins.get(i)
                .value();
            counts[i] = rest / value;
            rest -= counts[i] * value;
        }
        return counts;
    }

    /** {@code count} of {@code item} as full stacks and one partial stack. */
    public static List<ItemStack> stacks(Item item, long count) {
        List<ItemStack> out = new ArrayList<>();
        int max = item.getDefaultMaxStackSize();
        while (count > 0) {
            int n = (int) Math.min(count, max);
            out.add(new ItemStack(item, n));
            count -= n;
        }
        return out;
    }

    /**
     * How many of each coin to take from a store that holds {@code available[i]} of {@code denominations().get(i)},
     * so the coins taken are worth at least {@code amount}. Largest coins first; when they cannot match exactly, one
     * more coin covers the rest and the caller gives the overpaid part back as change. Empty when the store holds
     * too little.
     */
    public static Optional<long[]> cover(long amount, long[] available) {
        int[] values = denominations().stream()
            .mapToInt(Denomination::value)
            .toArray();
        return CoinMath.cover(values, available, amount);
    }

    public static long total(List<ItemStack> stacks) {
        return stacks.stream()
            .mapToLong(Currency::valueOf)
            .sum();
    }

    private static Table table() {
        Table t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    private static Table load() {
        List<String> configured = VMConfig.SPEC.isLoaded() ? List.copyOf(VMConfig.CURRENCY.get()) : List.of();
        boolean numismatics = configured.isEmpty() && ModList.get()
            .isLoaded("numismatics");
        List<Denomination> list = new ArrayList<>(
            numismatics ? NumismaticsCurrency.denominations() : parse(configured));
        list.sort(Comparator.comparingInt(Denomination::value)
            .reversed());
        List<Denomination> usable = usable(List.copyOf(list));
        return new Table(usable, numismatics && !usable.isEmpty());
    }

    /** The configured {@code item=value} entries. Unknown items are skipped with a warning. */
    private static List<Denomination> parse(List<String> configured) {
        List<Denomination> list = new ArrayList<>();
        for (String entry : configured) {
            String[] parts = entry.split("=");
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[0].trim()));
            if (item == Items.AIR) {
                VendingMachine.LOG.warn("Unknown currency item {}", parts[0]);
                continue;
            }
            list.add(new Denomination(item, Integer.parseInt(parts[1].trim())));
        }
        return list;
    }
}
