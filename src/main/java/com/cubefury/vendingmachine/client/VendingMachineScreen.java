package com.cubefury.vendingmachine.client;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import org.lwjgl.glfw.GLFW;

import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;

/**
 * The original Vending Machine screen: category tabs and option buttons on the left, the search bar, trades and coin
 * row in the panel, the player inventory at its bottom, and the side column with the IN slots, eject buttons and
 * output chute on the right. Positions follow the original; the panel grows with the window up to 320 pixels.
 */
public class VendingMachineScreen extends AbstractContainerScreen<VendingMachineMenu> {

    private static final int PANEL_WIDTH = 178;
    private static final int MAX_HEIGHT = 320;
    private static final int WINDOW_MARGIN = 69;
    private static final int TITLE_HEIGHT = 15;
    private static final int SEARCH_X = 7;
    private static final int SEARCH_Y = 9;
    private static final int SEARCH_HEIGHT = 14;
    private static final int TRADES_X = 7;
    private static final int TRADES_Y = 28;
    private static final int TRADES_BELOW = 176;
    private static final int TRADES_MAX_HEIGHT = 144;
    private static final int COINS_X = 10;
    private static final int COINS_BELOW = 143;
    private static final int INVENTORY_BELOW = 85;
    private static final int SIDE_X = 176;
    private static final int SIDE_TOP = 40;
    private static final int SIDE_WIDTH = 50;
    private static final int SIDE_HEIGHT = 214;
    private static final int CHUTE_X = 5;
    private static final int CHUTE_Y = 118;
    private static final int OPTIONS_X = -33;
    private static final int OPTIONS_Y = 1;
    private static final int OPTION_STEP = 16;
    private static final int OPTION_SIZE = 14;
    private static final int EJECT_SIZE = 18;
    private static final int HIDDEN = -10_000;
    private static final int REFRESH_TICKS = 20;

    private final TradeView view;
    private final TradeArea trades;
    private final CategoryTabs tabs;
    private final CoinRow coins;
    private final OutputChute chute;
    private EditBox search;
    private int sideY;
    private int ticksOpen;

    public VendingMachineScreen(VendingMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = PANEL_WIDTH;
        view = new TradeView(menu);
        Font font = Minecraft.getInstance().font;
        trades = new TradeArea(font, menu, view, this::buy);
        tabs = new CategoryTabs(view, Math.min(ClientSettings.lastTab, view.tabCount() - 1));
        coins = new CoinRow(font, menu);
        chute = new OutputChute(
            menu.slots.subList(
                VendingMachineMenu.FIRST_OUTPUT_SLOT,
                VendingMachineMenu.FIRST_PLAYER_SLOT));
    }

    @Override
    protected void init() {
        imageHeight = Math.min(MAX_HEIGHT, height - WINDOW_MARGIN);
        super.init();
        sideY = Math.min(SIDE_TOP, height - topPos - SIDE_HEIGHT);
        addSearchBar();
        addOptionButtons();
        addEjectButtons();
        layout();
    }

    private void addSearchBar() {
        search = addRenderableWidget(
            new EditBox(
                font,
                leftPos + SEARCH_X + 4,
                topPos + SEARCH_Y + 3,
                TradeArea.WIDTH - 8,
                font.lineHeight,
                Component.translatable("vendingmachine.gui.search")));
        search.setBordered(false);
        search.setTextColor(Art.TEXT);
        search.setValue(ClientSettings.lastSearch);
        search.setResponder(text -> {
            ClientSettings.lastSearch = text;
            trades.scrollToTop();
            refresh();
        });
    }

    private void addOptionButtons() {
        option(
            OPTIONS_X + OPTION_STEP,
            OPTIONS_Y,
            () -> ClientSettings.LIST_MODE.get() ? Art.MODE_LIST : Art.MODE_TILE,
            () -> Component.translatable("vendingmachine.gui.display_mode")
                .append(" ")
                .append(
                    Component.translatable(
                        ClientSettings.LIST_MODE.get() ? "vendingmachine.gui.display_mode_list"
                            : "vendingmachine.gui.display_mode_tile")),
            ClientSettings.LIST_MODE);
        option(
            OPTIONS_X,
            OPTIONS_Y + OPTION_STEP,
            () -> ClientSettings.SHOW_COINS.get() ? Art.SHOW_COINS : Art.HIDE_COINS,
            () -> null,
            ClientSettings.SHOW_COINS);
        option(
            OPTIONS_X + OPTION_STEP,
            OPTIONS_Y + OPTION_STEP,
            () -> ClientSettings.SORT_ALPHABETICAL.get() ? Art.SORT_ALPHABET : Art.SORT_SMART,
            () -> Component.translatable("vendingmachine.gui.display_sort")
                .append(" ")
                .append(
                    Component.translatable(
                        ClientSettings.SORT_ALPHABETICAL.get() ? "vendingmachine.gui.display_sort_alphabet"
                            : "vendingmachine.gui.display_sort_smart")),
            ClientSettings.SORT_ALPHABETICAL);
    }

    private void option(int x, int y, Supplier<Art.Texture> icon, Supplier<Component> tooltip,
        ModConfigSpec.BooleanValue value) {
        IconButton button = addRenderableWidget(new IconButton(OPTION_SIZE, 12, icon, tooltip, () -> {
            ClientSettings.toggle(value);
            layout();
        }));
        button.setPosition(leftPos + x, topPos + y);
    }

    private void addEjectButtons() {
        IconButton coinsOut = addRenderableWidget(
            new IconButton(
                EJECT_SIZE,
                16,
                () -> Art.EJECT_COINS,
                () -> Component.translatable("vendingmachine.gui.coin_eject"),
                () -> buttonClick(VendingMachineMenu.BUTTON_EJECT_COINS)) {

                @Override
                public void playDownSound(SoundManager sounds) {
                    // The machine plays the coin drop sound instead of the click.
                }
            });
        coinsOut.setPosition(leftPos + SIDE_X + 5, topPos + sideY + 98);
        IconButton itemsOut = addRenderableWidget(
            new IconButton(
                EJECT_SIZE,
                16,
                () -> Art.EJECT_ITEMS,
                () -> Component.translatable("vendingmachine.gui.item_eject"),
                () -> buttonClick(VendingMachineMenu.BUTTON_TAKE_OUTPUTS)));
        itemsOut.setPosition(leftPos + SIDE_X + 25, topPos + sideY + 98);
    }

    /** Places the trades, coins, tabs and every slot for the current window size and options. */
    private void layout() {
        int listHeight = Mth.clamp(imageHeight - TRADES_BELOW, 1, TRADES_MAX_HEIGHT)
            + (ClientSettings.SHOW_COINS.get() ? 0 : CoinRow.HEIGHT);
        trades.place(leftPos + TRADES_X, topPos + TRADES_Y, listHeight);
        coins.place(leftPos + COINS_X, topPos + imageHeight - COINS_BELOW);
        tabs.place(leftPos + CategoryTabs.LEFT, topPos + CategoryTabs.TOP, imageHeight - CategoryTabs.TOP - 4);
        placeSlots();
        int coinRow = ClientSettings.SHOW_COINS.get() ? VendingMachineMenu.BUTTON_COIN_ROW_SHOWN
            : VendingMachineMenu.BUTTON_COIN_ROW_HIDDEN;
        menu.clickMenuButton(minecraft.player, coinRow);
        buttonClick(coinRow);
        refresh();
    }

    private void placeSlots() {
        for (int i = 0; i < VendingMachineMenu.IN_SLOTS; i++) {
            place(i, SIDE_X + 7 + i % 2 * 18, sideY + 20 + i / 2 * 18);
        }
        // The card slot sits in the coin row and hides with it.
        boolean showCoins = ClientSettings.SHOW_COINS.get();
        place(
            VendingMachineMenu.CARD_SLOT,
            showCoins ? COINS_X + 2 * CoinRow.COLUMN_WIDTH + 1 : HIDDEN,
            imageHeight - COINS_BELOW + 1);
        for (int i = 0; i < VendingMachineBlockEntity.OUTPUT_SLOTS; i++) {
            place(
                VendingMachineMenu.FIRST_OUTPUT_SLOT + i,
                SIDE_X + CHUTE_X + OutputChute.restX(i) + 1,
                sideY + CHUTE_Y + OutputChute.FLOOR + 1);
        }
        for (int i = 0; i < 36; i++) {
            int row = i / 9;
            place(
                VendingMachineMenu.FIRST_PLAYER_SLOT + i,
                9 + i % 9 * 18,
                imageHeight - INVENTORY_BELOW + 1 + row * 18 + (row == 3 ? 4 : 0));
        }
    }

    private void place(int slot, int x, int y) {
        Slot s = menu.slots.get(slot);
        s.x = x;
        s.y = y;
    }

    private void refresh() {
        tabs.leaveHiddenFavourites();
        trades.show(view.shown(tabs.selected(), search.getValue()));
    }


    /**
     * Re-sorts once a second, as the original does, and holds the order while Shift or Ctrl is held so that a trade
     * does not move under the cursor between two clicks. The player's own changes refresh at once.
     */
    @Override
    protected void containerTick() {
        chute.tick();
        boolean held = Screen.hasShiftDown() || Screen.hasControlDown();
        if (ticksOpen % REFRESH_TICKS == 0 && !held) refresh();
        ticksOpen++;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        tabs.renderBehind(g, search.getValue(), mouseX, mouseY);
        renderTitle(g);
        Art.panel(g, leftPos, topPos, imageWidth, imageHeight);
        tabs.renderSelected(g, search.getValue());
        Art.drawTinted(
            g,
            Art.SEARCH_BAR,
            new Art.Box(leftPos + SEARCH_X, topPos + SEARCH_Y, TradeArea.WIDTH, SEARCH_HEIGHT));
        // The original's hint is drawn like its text, with no shadow; the edit box's own hint has a shadow.
        if (search.getValue()
            .isEmpty() && !search.isFocused()) g.drawString(
                font,
                Component.translatable("vendingmachine.gui.search"),
                leftPos + SEARCH_X + 4,
                topPos + SEARCH_Y + 3,
                Art.TEXT,
                false);
        trades.render(g, mouseX, mouseY);
        if (ClientSettings.SHOW_COINS.get()) coins.render(g);
        renderSideColumn(g);
        renderSlotBackgrounds(g);
    }

    /** The title tab; the panel, drawn next, covers its bottom edge so the two join. */
    private void renderTitle(GuiGraphics g) {
        int width = font.width(title) + 9;
        Art.title(g, leftPos, topPos - TITLE_HEIGHT, width, TITLE_HEIGHT + 3);
        g.drawString(font, title, leftPos + 6, topPos - TITLE_HEIGHT + 6, Art.TEXT, false);
    }

    private void renderSideColumn(GuiGraphics g) {
        int x = leftPos + SIDE_X;
        int y = topPos + sideY;
        Art.drawTinted(g, Art.SIDE_PANEL, new Art.Box(x, y, SIDE_WIDTH, SIDE_HEIGHT));
        Art.draw(g, Art.INPUT, x + 9, y + 7);
        Art.drawTinted(g, Art.DISPENSER, new Art.Box(x + CHUTE_X, y + CHUTE_Y, OutputChute.WIDTH, OutputChute.HEIGHT));
    }

    private void renderSlotBackgrounds(GuiGraphics g) {
        for (Slot slot : menu.slots) {
            if (!isOutput(slot)) Art.slot(g, leftPos + slot.x, topPos + slot.y);
        }
    }

    private static boolean isOutput(Slot slot) {
        return slot.index >= VendingMachineMenu.FIRST_OUTPUT_SLOT && slot.index < VendingMachineMenu.FIRST_PLAYER_SLOT;
    }

    /** Draws bought items at their place in the fall. */
    @Override
    protected void renderSlotContents(GuiGraphics g, ItemStack stack, Slot slot, String countString) {
        g.pose()
            .pushPose();
        g.pose()
            .translate(0, -chute.lift(slot), 0);
        super.renderSlotContents(g, stack, slot, countString);
        g.pose()
            .popPose();
    }

    /** Draws the IN label and the chute's overhang over the bought items; the panel has no other labels. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Component in = Component.translatable("vendingmachine.gui.in");
        g.drawString(font, in, SIDE_X + (SIDE_WIDTH - font.width(in)) / 2, sideY + 10, Art.TEXT, false);
        g.pose()
            .pushPose();
        g.pose()
            .translate(0, 0, 200);
        Art.tint(1.0F);
        Art.draw(g, Art.DISPENSER_OVERHANG, new Art.Box(SIDE_X + CHUTE_X, sideY + CHUTE_Y, OutputChute.WIDTH, 19));
        Art.untint();
        g.pose()
            .popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.hasItem()) renderTooltip(g, mouseX, mouseY);
        else renderOwnTooltip(g, mouseX, mouseY);
    }

    private void renderOwnTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int trade = trades.tradeAt(mouseX, mouseY);
        int coin = ClientSettings.SHOW_COINS.get() ? coins.coinAt(mouseX, mouseY) : -1;
        int tab = tabs.tabAt(mouseX, mouseY);
        if (trade >= 0) {
            g.renderComponentTooltipFromElements(
                font,
                trades.tooltip(view.trade(trade)),
                mouseX,
                mouseY,
                ItemStack.EMPTY);
        } else if (coin >= 0) {
            g.renderComponentTooltip(font, coins.tooltip(coin), mouseX, mouseY);
        } else if (tab >= 0) {
            g.renderTooltip(font, tabs.tooltip(tab), mouseX, mouseY);
        }
    }

    /** Keeps typed letters in the search box instead of closing the screen on the inventory key. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return button == 0 && clickOwnParts(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    /** The trade scroll thumb follows the mouse while it is held. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return trades.drag(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean clickOwnParts(double mouseX, double mouseY) {
        return clickTab(mouseX, mouseY) || clickCoin(mouseX, mouseY) || clickTradesOrChute(mouseX, mouseY);
    }

    private boolean clickTradesOrChute(double mouseX, double mouseY) {
        return trades.pressScrollBar(mouseX, mouseY) || clickTrade(mouseX, mouseY) || clickChute(mouseX, mouseY);
    }

    private boolean clickTab(double mouseX, double mouseY) {
        if (!tabs.click(mouseX, mouseY)) return false;
        trades.scrollToTop();
        refresh();
        return true;
    }

    /** A ctrl-click changes the favourites, so the tabs and the order follow at once. */
    private boolean clickTrade(double mouseX, double mouseY) {
        if (!trades.click(mouseX, mouseY)) return false;
        refresh();
        return true;
    }

    private boolean clickCoin(double mouseX, double mouseY) {
        int coin = ClientSettings.SHOW_COINS.get() ? coins.click(mouseX, mouseY) : -1;
        if (coin >= 0) buttonClick(VendingMachineMenu.ejectCoinButton(coin));
        return coin >= 0;
    }

    /** Shift-click in the chute, but not on a bought item, moves every bought item into the inventory. */
    private boolean clickChute(double mouseX, double mouseY) {
        boolean take = Screen.hasShiftDown() && hoveredSlot == null && inChute(mouseX, mouseY);
        if (take) buttonClick(VendingMachineMenu.BUTTON_TAKE_OUTPUTS);
        return take;
    }

    private boolean inChute(double mouseX, double mouseY) {
        return isHovering(
            SIDE_X + CHUTE_X,
            sideY + CHUTE_Y,
            OutputChute.WIDTH,
            OutputChute.HEIGHT,
            mouseX,
            mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        trades.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return tabs.scroll(mouseX, mouseY, scrollY) || trades.scroll(mouseX, mouseY, scrollY)
            || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Clicks on the side column, the tabs and the option buttons are inside the screen, not drops. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return super.hasClickedOutside(mouseX, mouseY, left, top, button)
            && !isHovering(SIDE_X, sideY, SIDE_WIDTH, SIDE_HEIGHT, mouseX, mouseY)
            && !isHovering(OPTIONS_X - 1, 0, -OPTIONS_X + 1, imageHeight, mouseX, mouseY);
    }

    private void buy(int trade) {
        buttonClick(VendingMachineMenu.BUTTON_FIRST_TRADE + trade);
    }

    private void buttonClick(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
