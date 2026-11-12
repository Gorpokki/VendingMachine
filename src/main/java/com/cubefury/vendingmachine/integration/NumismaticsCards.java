package com.cubefury.vendingmachine.integration;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.ithundxr.createnumismatics.content.backend.IDeductable;
import dev.ithundxr.createnumismatics.content.backend.ReasonHolder;

/** Only loaded when Create: Numismatics is present. */
public final class NumismaticsCards {

    private NumismaticsCards() {}

    /**
     * Takes {@code spurs} from the bank account behind a bound card, or the shared account behind an authorized card,
     * the same way the Numismatics Vendor does. Takes nothing and returns false when the player may not use the card
     * or the account has too little.
     */
    public static boolean pay(ItemStack card, Player player, int spurs) {
        IDeductable account = IDeductable.get(card, player, ReasonHolder.IGNORED);
        return account != null && account.deduct(spurs, ReasonHolder.IGNORED);
    }

    /**
     * Spurs the card can pay for {@code player}: the account balance behind a bound card, and for an authorized card
     * the sub-account's remaining limit capped by the shared account's balance. Zero when the player may not use the
     * card.
     */
    public static int balance(ItemStack card, Player player) {
        IDeductable account = IDeductable.get(card, player, ReasonHolder.IGNORED);
        return account == null ? 0 : account.getMaxWithdrawal();
    }
}
