package com.cubefury.vendingmachine.gametest;

import net.minecraft.world.item.Item;

import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;

/** Test help for the machine's chute: purchases and ejected coins reach it through a queue, one stack per drop. */
final class Chute {

    private static final int MAX_TICKS = 10_000;

    private Chute() {}

    /** Ticks the machine until its queue is empty, as the server would. */
    static void drain(VendingMachineBlockEntity machine) {
        for (int i = 0; i < MAX_TICKS && machine.queued() > 0; i++) {
            machine.tick();
        }
    }

    /** How many of {@code item} the chute holds across its slots. */
    static int count(VendingMachineBlockEntity machine, Item item) {
        int count = 0;
        for (int i = 0; i < machine.output.getSlots(); i++) {
            if (machine.output.getStackInSlot(i)
                .is(item)) {
                count += machine.output.getStackInSlot(i)
                    .getCount();
            }
        }
        return count;
    }
}
