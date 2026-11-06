package com.cubefury.vendingmachine.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class CoinMathTest {

    private static final int[] VALUES = { 64, 8, 1 };

    private static long[] cover(long amount, long... available) {
        return CoinMath.cover(VALUES, available, amount)
            .orElseThrow();
    }

    @Test
    void takesLargestCoinsFirstForAnExactAmount() {
        assertArrayEquals(new long[] { 1, 2, 3 }, cover(83, 5, 5, 5));
    }

    @Test
    void takesSmallerCoinsWhenLargerOnesRunOut() {
        assertArrayEquals(new long[] { 0, 2, 4 }, cover(20, 0, 2, 9));
    }

    @Test
    void overpaysWithTheSmallestCoinThatCoversTheRest() {
        assertArrayEquals(new long[] { 0, 1, 0 }, cover(3, 0, 4, 0));
    }

    @Test
    void givesBackSmallCoinsTheOverpayingCoinAlreadyCovers() {
        assertArrayEquals(new long[] { 1, 0, 0 }, cover(20, 1, 0, 5));
    }

    @Test
    void paysExactlyWithSmallCoinsBeforeReachingForALargerOne() {
        assertArrayEquals(new long[] { 0, 0, 1 }, cover(1, 0, 1, 1));
    }

    @Test
    void keepsTheOverpayingCoinAfterGivingBackSmallerOnes() {
        assertArrayEquals(new long[] { 0, 1, 0 }, cover(2, 0, 1, 1));
    }

    @Test
    void isEmptyWhenNoCoinIsLeftToCoverTheRest() {
        assertEquals(Optional.empty(), CoinMath.cover(VALUES, new long[] { 0, 0, 0 }, 1));
    }

    @Test
    void isEmptyWhenTheStoreHoldsTooLittle() {
        assertEquals(Optional.empty(), CoinMath.cover(VALUES, new long[] { 0, 1, 7 }, 16));
    }

    @Test
    void takesNothingForANothingAmount() {
        assertArrayEquals(new long[] { 0, 0, 0 }, cover(0, 1, 1, 1));
    }

    @Test
    void isEmptyWithNoCoinsForAPositiveAmount() {
        assertEquals(Optional.empty(), CoinMath.cover(new int[0], new long[0], 1));
    }
}
