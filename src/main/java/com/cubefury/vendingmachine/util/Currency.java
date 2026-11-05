package com.cubefury.vendingmachine.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
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

    public record Denomination(Item item, int value) {}

    private record Table(List<Denomination> denominations, boolean inSpurs) {}

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

    /** Whether credit is counted in Numismatics spurs, so a Numismatics card can pay it. */
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
            if (!items.add(d.item())) {
                VendingMachine.LOG.error(
                    "Coin {} is listed more than once; accepting no currency",
                    BuiltInRegistries.ITEM.getKey(d.item()));
                return List.of();
            }
            if (d.value() % unit != 0) {
                VendingMachine.LOG.error(
                    "Coin worth {} is not a multiple of the smallest coin ({}); accepting no currency",
                    d.value(),
                    unit);
                return List.of();
            }
        }
        return denominations;
    }

    /**
     * Coin stacks worth as much of {@code amount} as the denominations can express, largest first. The caller keeps
     * whatever is left over.
     */
    public static List<ItemStack> makeChange(long amount) {
        List<ItemStack> out = new ArrayList<>();
        for (Denomination d : denominations()) {
            long count = amount / d.value();
            amount -= count * d.value();
            int max = d.item()
                .getDefaultMaxStackSize();
            while (count > 0) {
                int n = (int) Math.min(count, max);
                out.add(new ItemStack(d.item(), n));
                count -= n;
            }
        }
        return out;
    }

    /**
     * How many of each coin to take from a store that holds {@code available[i]} of {@code denominations().get(i)},
     * so the coins taken are worth at least {@code amount}. Largest coins first; when they cannot match exactly, one
     * more coin covers the rest and the caller keeps the overpaid part as credit. Null when the store holds too little.
     */
    public static long[] cover(long amount, long[] available) {
        List<Denomination> d = denominations();
        long[] take = new long[d.size()];
        long rest = amount;
        for (int i = 0; i < take.length; i++) {
            take[i] = Math.min(available[i], rest / d.get(i)
                .value());
            rest -= take[i] * d.get(i)
                .value();
        }
        for (int i = take.length - 1; rest > 0 && i >= 0; i--) {
            if (d.get(i)
                .value() >= rest && available[i] > take[i]) {
                take[i]++;
                rest -= d.get(i)
                    .value();
            }
        }
        if (rest > 0) return null;
        for (int i = take.length - 1; i >= 0; i--) {
            long value = d.get(i)
                .value();
            while (take[i] > 0 && value <= -rest) {
                take[i]--;
                rest += value;
            }
        }
        return take;
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
        List<Denomination> list = new ArrayList<>();
        List<? extends String> configured = VMConfig.SPEC.isLoaded() ? VMConfig.CURRENCY.get() : List.of();
        if (!configured.isEmpty()) {
            for (String entry : configured) {
                String[] parts = entry.split("=");
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[0].trim()));
                if (item == Items.AIR) {
                    VendingMachine.LOG.warn("Unknown currency item {}", parts[0]);
                    continue;
                }
                list.add(new Denomination(item, Integer.parseInt(parts[1].trim())));
            }
        } else if (ModList.get()
            .isLoaded("numismatics")) {
                list.addAll(NumismaticsCurrency.denominations());
            }
        list.sort(Comparator.comparingInt(Denomination::value)
            .reversed());
        List<Denomination> usable = usable(List.copyOf(list));
        boolean inSpurs = configured.isEmpty() && !usable.isEmpty()
            && ModList.get()
                .isLoaded("numismatics");
        return new Table(usable, inSpurs);
    }
}
