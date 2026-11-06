package com.cubefury.vendingmachine.util;

import java.util.Optional;

/** Coin arithmetic on plain arrays, with no Minecraft types, so it runs without a server. */
public final class CoinMath {

    private CoinMath() {}

    /**
     * How many of each coin to take from a store that holds {@code available[i]} coins worth {@code values[i]}, so the
     * coins taken are worth at least {@code amount}. {@code values} is ordered largest first. Largest coins first; when
     * they cannot match exactly, one more coin covers the rest and the coins that overpay alone go back. Empty when the
     * store holds too little.
     */
    public static Optional<long[]> cover(int[] values, long[] available, long amount) {
        long[] take = new long[values.length];
        long rest = takeLargestFirst(values, available, amount, take);
        rest = topUp(values, available, rest, take);
        if (rest > 0) return Optional.empty();
        giveBack(values, rest, take);
        return Optional.of(take);
    }

    /** Takes as many of each coin as fit in {@code amount}, largest first. Returns what is left to pay. */
    private static long takeLargestFirst(int[] values, long[] available, long amount, long[] take) {
        long rest = amount;
        for (int i = 0; i < take.length; i++) {
            take[i] = Math.min(available[i], rest / values[i]);
            rest -= take[i] * values[i];
        }
        return rest;
    }

    /** Adds one more of the smallest coin that covers the rest on its own. Returns the rest, negative when overpaid. */
    private static long topUp(int[] values, long[] available, long rest, long[] take) {
        for (int i = take.length - 1; rest > 0 && i >= 0; i--) {
            if (values[i] >= rest && available[i] > take[i]) {
                take[i]++;
                rest -= values[i];
            }
        }
        return rest;
    }

    /** Puts back, smallest first, every coin the overpaid {@code -rest} still covers. */
    private static void giveBack(int[] values, long rest, long[] take) {
        for (int i = take.length - 1; i >= 0; i--) {
            while (take[i] > 0 && values[i] <= -rest) {
                take[i]--;
                rest += values[i];
            }
        }
    }
}
