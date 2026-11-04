package com.cubefury.vendingmachine.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private static volatile List<Denomination> denominations;

    private Currency() {}

    public static void invalidate() {
        denominations = null;
    }

    /** Denominations, largest value first. */
    public static List<Denomination> denominations() {
        List<Denomination> d = denominations;
        if (d == null) {
            d = load();
            denominations = d;
        }
        return d;
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

    public static long total(List<ItemStack> stacks) {
        return stacks.stream()
            .mapToLong(Currency::valueOf)
            .sum();
    }

    private static List<Denomination> load() {
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
        return List.copyOf(list);
    }
}
