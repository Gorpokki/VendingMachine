package com.cubefury.vendingmachine.client;

import java.util.List;

import net.minecraft.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The output chute's items, as in the original: each output slot rests at its own spot on the chute's floor, and a new
 * item falls into it with a bounce.
 */
final class OutputChute {

    static final int WIDTH = 41;
    static final int HEIGHT = 90;
    /** How far an item falls: from just above the chute to the floor, where the slot rests. */
    static final int FLOOR = 72;
    private static final int SPREAD = 24;
    private static final long FALL_MILLIS = 1000;
    private static final float BOUNCE = 2.75F;
    private static final double GOLDEN_RATIO_CONJUGATE = (Math.sqrt(5) - 1) / 2;

    private final List<Slot> slots;
    private final ItemStack[] seen;
    private final long[] landed;
    private boolean primed;

    OutputChute(List<Slot> slots) {
        this.slots = slots;
        seen = new ItemStack[slots.size()];
        landed = new long[slots.size()];
    }

    /** Where slot {@code i} rests across the chute: each new slot fills the largest gap left by the earlier ones. */
    static int restX(int i) {
        return (int) (i * GOLDEN_RATIO_CONJUGATE % 1.0 * SPREAD);
    }

    /**
     * Starts a fall for each slot that received a different item. The first tick only records the items, so the ones
     * already in the chute when the screen opens do not fall again.
     */
    void tick() {
        for (int i = 0; i < seen.length; i++) {
            ItemStack item = slots.get(i)
                .getItem();
            if (primed && !item.isEmpty() && !ItemStack.isSameItemSameComponents(item, seen[i])) {
                landed[i] = Util.getMillis();
            }
            seen[i] = item.copy();
        }
        primed = true;
    }

    /** How far above its resting place slot {@code i}'s item is drawn now. */
    int lift(Slot slot) {
        int i = slots.indexOf(slot);
        if (i < 0) return 0;
        float t = Math.min(1.0F, (Util.getMillis() - landed[i]) / (float) FALL_MILLIS);
        return Math.round((1 - bounceOut(t)) * (FLOOR + 1));
    }

    /** The standard bounce-out easing curve, rising from 0 to 1. */
    static float bounceOut(float t) {
        if (t < 1 / BOUNCE) return arc(t, 0);
        if (t < 2 / BOUNCE) return arc(t - 1.5F / BOUNCE, 0.75F);
        if (t < 2.5F / BOUNCE) return arc(t - 2.25F / BOUNCE, 0.9375F);
        return arc(t - 2.625F / BOUNCE, 0.984375F);
    }

    private static float arc(float t, float base) {
        return 7.5625F * t * t + base;
    }
}
