package com.cubefury.vendingmachine.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Categories;
import com.cubefury.vendingmachine.trade.Trade;

/**
 * Which trades each tab shows for the current search, in the chosen order. Tab 0 holds the favourites, tab 1 every
 * trade, and each further tab one category of the menu's tab list.
 */
final class TradeView {

    static final int FAVOURITES = 0;
    static final int ALL = 1;
    static final int FIRST_CATEGORY = 2;

    private final VendingMachineMenu menu;

    TradeView(VendingMachineMenu menu) {
        this.menu = menu;
    }

    int tabCount() {
        return FIRST_CATEGORY + menu.tabs()
            .size();
    }

    /** The category tab behind {@code tab}, or null for Favourites and All. */
    Categories.Tab category(int tab) {
        return tab < FIRST_CATEGORY ? null
            : menu.tabs()
                .get(tab - FIRST_CATEGORY);
    }

    Trade trade(int index) {
        return menu.trades()
            .get(index);
    }

    /** Indices of the trades in {@code tab} that match {@code search}, in display order. */
    List<Integer> shown(int tab, String search) {
        return IntStream.range(
            0,
            menu.trades()
                .size())
            .filter(i -> inTab(i, tab) && matches(i, search))
            .boxed()
            .sorted(order())
            .toList();
    }

    boolean hasMatch(int tab, String search) {
        return IntStream.range(
            0,
            menu.trades()
                .size())
            .anyMatch(i -> inTab(i, tab) && matches(i, search));
    }

    private boolean inTab(int index, int tab) {
        if (tab == FAVOURITES) return ClientSettings.isFavourite(trade(index));
        return tab == ALL || trade(index).category()
            .equals(
                category(tab).name());
    }

    /** As in the original, the search matches the shown item or any stack the trade gives. */
    private boolean matches(int index, String search) {
        String lower = search.toLowerCase(Locale.ROOT);
        return name(index).contains(lower) || trade(index).items()
            .stream()
            .anyMatch(
                item -> name(item).contains(lower));
    }

    private String name(int index) {
        return name(
            trade(index).display());
    }

    private static String name(ItemStack item) {
        return item.getHoverName()
            .getString()
            .toLowerCase(Locale.ROOT);
    }

    /**
     * Favourites first; then by name, or the original smart order: payable first, then by shown item, then by place in
     * the trade group, then catalogue.
     */
    private Comparator<Integer> order() {
        Comparator<Integer> favouritesFirst = Comparator.comparing(i -> !ClientSettings.isFavourite(trade(i)));
        if (ClientSettings.SORT_ALPHABETICAL.get()) return favouritesFirst.thenComparing(this::name);
        return favouritesFirst.thenComparing(i -> !menu.canBuy(trade(i)))
            .thenComparingInt(
                i -> BuiltInRegistries.ITEM.getId(
                    trade(i).display()
                        .getItem()))
            .thenComparingInt(i -> trade(i).groupOrder())
            .thenComparingInt(i -> i);
    }
}
