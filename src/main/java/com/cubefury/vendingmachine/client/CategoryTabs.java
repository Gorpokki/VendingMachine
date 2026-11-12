package com.cubefury.vendingmachine.client;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The category tabs left of the panel, one icon each. The Favourites tab hides while this machine sells none of the
 * favourites; a tab
 * with a match for the search is highlighted; the list scrolls when the tabs do not fit.
 */
final class CategoryTabs {

    static final int LEFT = -29;
    static final int TOP = 40;
    static final int WIDTH = 32;
    private static final int TAB_HEIGHT = 28;
    private static final int BAR = 3;
    private static final int ICON_WIDTH = 20;
    private static final int ICON_HEIGHT = 16;

    private final TradeView view;
    private int x;
    private int y;
    private int height;
    private int scroll;
    private int selected;
    private int hovered = -1;

    CategoryTabs(TradeView view, int selected) {
        this.view = view;
        this.selected = selected;
    }

    /** Places the tab list with the tabs' top-left corner at {@code (left, top)}, at most {@code maxHeight} tall. */
    void place(int left, int top, int maxHeight) {
        x = left;
        y = top;
        height = Math.max(TAB_HEIGHT, maxHeight);
        scrollBy(0);
    }

    int selected() {
        return selected;
    }

    /** Moves from the Favourites tab to All once the Favourites tab hides, so it stays shut when it comes back. */
    void leaveHiddenFavourites() {
        if (selected == TradeView.FAVOURITES && !showsFavourites()) selected = TradeView.ALL;
    }

    private boolean showsFavourites() {
        return view.hasMatch(TradeView.FAVOURITES, "");
    }

    private List<Integer> visible() {
        return IntStream.range(0, view.tabCount())
            .filter(tab -> tab != TradeView.FAVOURITES || showsFavourites())
            .boxed()
            .toList();
    }

    private void scrollBy(int amount) {
        scroll = Mth.clamp(scroll + amount, 0, Math.max(0, visible().size() * TAB_HEIGHT - height));
    }

    /** Draws the unselected tabs; they sit behind the panel. */
    void renderBehind(GuiGraphics g, String search, int mouseX, int mouseY) {
        hovered = tabAt(mouseX, mouseY);
        g.enableScissor(x - BAR, y, x + WIDTH, y + height);
        List<Integer> tabs = visible();
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i) != selected) renderTab(g, tabs.get(i), y + i * TAB_HEIGHT - scroll, search, false);
        }
        g.disableScissor();
        Art.scrollBar(g, new Art.Box(x - BAR, y, BAR, height), tabs.size() * TAB_HEIGHT, scroll);
    }

    /** Draws the selected tab over the panel's edge, so it joins the panel. */
    void renderSelected(GuiGraphics g, String search) {
        int row = visible().indexOf(selected);
        g.enableScissor(x - BAR, y, x + WIDTH, y + height);
        renderTab(g, selected, y + row * TAB_HEIGHT - scroll, search, true);
        g.disableScissor();
    }

    private void renderTab(GuiGraphics g, int tab, int top, String search, boolean open) {
        Art.tint(1.0F);
        if (Art.present(Art.TABS)) {
            g.blit(Art.TABS, x, top, open ? WIDTH : 0, 0, WIDTH, TAB_HEIGHT, 2 * WIDTH, 3 * TAB_HEIGHT);
        } else {
            // The open tab runs on into the panel, so it has no right edge.
            Art.plain(g, new Art.Box(x, top, open ? WIDTH + 2 : WIDTH, TAB_HEIGHT), open ? 0xFFC6C6C6 : 0xFFA0A0A0);
        }
        Art.untint();
        if (!search.isEmpty() && view.hasMatch(tab, search)) {
            // Stretched to 20x20, as the original draws it.
            Art.draw(g, Art.FILTERED_TAB, new Art.Box(x + 6, top + 4, ICON_WIDTH, ICON_WIDTH));
        }
        // The whole icon file, whatever its size, fills the icon area; the hovered tab's icon moves 1 px left.
        int iconX = tab == hovered ? x + 6 : x + 7;
        Art.image(g, Art.icon(iconName(tab)), new Art.Box(iconX, top + 6, ICON_WIDTH, ICON_HEIGHT), glyph(tab));
    }

    /** The letter drawn when the icon texture is missing: a star for favourites, else the category's initial. */
    private String glyph(int tab) {
        if (tab == TradeView.FAVOURITES) return "*";
        String name = tab == TradeView.ALL ? "all"
            : view.category(tab)
                .name();
        return name.substring(0, 1)
            .toUpperCase(Locale.ROOT);
    }

    private String iconName(int tab) {
        if (tab == TradeView.FAVOURITES) return "favourites";
        if (tab == TradeView.ALL) return "all";
        return view.category(tab)
            .icon();
    }

    /** The tab under the mouse, or -1. */
    int tabAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) return -1;
        int row = (int) (mouseY - y + scroll) / TAB_HEIGHT;
        List<Integer> tabs = visible();
        return row < tabs.size() ? tabs.get(row) : -1;
    }

    boolean click(double mouseX, double mouseY) {
        int tab = tabAt(mouseX, mouseY);
        if (tab < 0) return false;
        selected = tab;
        ClientSettings.lastTab = tab;
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (mouseX < x - BAR || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) return false;
        scrollBy(-(int) Math.signum(amount) * TAB_HEIGHT);
        return true;
    }

    Component tooltip(int tab) {
        if (tab == TradeView.FAVOURITES) return Component.translatable("vendingmachine.category.favourites");
        if (tab == TradeView.ALL) return Component.translatable("vendingmachine.category.all");
        String name = view.category(tab)
            .name();
        return Component.translatableWithFallback("vendingmachine.category." + name, name);
    }
}
