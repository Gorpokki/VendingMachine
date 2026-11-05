package com.cubefury.vendingmachine.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import com.cubefury.vendingmachine.util.Currency.Denomination;

import dev.ithundxr.createnumismatics.content.backend.Coin;
import dev.ithundxr.createnumismatics.content.coins.CoinItem;

/** Only loaded when Create: Numismatics is present. */
public final class NumismaticsCurrency {

    private NumismaticsCurrency() {}

    public static List<Denomination> denominations() {
        List<Denomination> list = new ArrayList<>();
        for (Coin coin : Coin.values()) {
            var item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                    "numismatics",
                    coin.name()
                        .toLowerCase(Locale.ROOT)));
            if (item instanceof CoinItem coinItem && coinItem.coin == coin) {
                list.add(new Denomination(item, coin.value));
            }
        }
        return list;
    }
}
