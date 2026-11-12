package com.cubefury.vendingmachine.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.VendingMachine;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * The screen's textures and drawing helpers. The mod ships none of the textures: a resource pack can supply each one
 * under {@code assets/vendingmachine/textures/gui/}, and without it the screen draws a plain bevel, fill or letter in
 * its place. Grey textures and the vanilla-style bevels are drawn in the theme tint, which turns the vanilla grey
 * {@code #C6C6C6} into {@code #A3ABC6}.
 */
final class Art {

    /**
     * A texture, its size and unstretched border, and what to draw without it: a {@code fallback} colour (a bevel when
     * opaque, a plain fill when translucent, nothing when clear) or, for an icon, a {@code glyph}.
     */
    record Texture(ResourceLocation location, int width, int height, int border, int fallback, String glyph) {

        Texture(String path, int width, int height, int border, int fallback) {
            this(gui(path), width, height, border, fallback, "");
        }

        /** An icon, drawn whole, and its stand-in letter. */
        Texture(String path, int size, String glyph) {
            this(gui(path), size, size, 0, 0, glyph);
        }
    }

    static final Texture TRADE_BACKGROUND = new Texture("background/trade_background", 61, 12, 4, 0xFF8B8B8B);
    static final Texture SIDE_PANEL = new Texture("background/panel_side", 50, 214, 4, 0xFFC6C6C6);
    static final Texture SEARCH_BAR = new Texture("background/text_field_light_gray", 61, 12, 1, 0xFFE0E0E0);
    static final Texture TILE = new Texture("background/trade_button_unpressed", 47, 25, 4, 0xFFC6C6C6);
    static final Texture TILE_PRESSED = new Texture("background/trade_button_pressed", 47, 25, 4, 0xFFA0A0A0);
    static final Texture LIST_ROW = new Texture("background/list_trade_button_unpressed", 153, 15, 4, 0xFFC6C6C6);
    static final Texture LIST_ROW_PRESSED = new Texture("background/list_trade_button_pressed", 153, 15, 4, 0xFFA0A0A0);
    static final Texture INPUT = new Texture("background/input", 30, 20, 0, 0);
    static final Texture DISPENSER = new Texture("background/dispenser_background", 39, 71, 0, 0xFF555555);
    static final Texture DISPENSER_OVERHANG = new Texture("background/dispenser_overhang", 40, 19, 0, 0);
    static final Texture TILE_TRADEABLE = new Texture("overlay/tile_tradeable", 47, 25, 4, 0x4040C040);
    static final Texture TILE_SELECTED = new Texture("overlay/tile_selected", 47, 25, 4, 0x40FFFFFF);
    static final Texture MODE_TILE = new Texture("overlay/mode_tile", 12, "#");
    static final Texture MODE_LIST = new Texture("overlay/mode_list", 12, "=");
    static final Texture SORT_SMART = new Texture("overlay/sort_smart", 12, "S");
    static final Texture SORT_ALPHABET = new Texture("overlay/sort_alphabet", 12, "A");
    static final Texture SHOW_COINS = new Texture("overlay/show_coins", 12, "$");
    static final Texture HIDE_COINS = new Texture("overlay/hide_coins", 12, "-");
    static final Texture EJECT_COINS = new Texture("overlay/coin_return", 16, "$");
    static final Texture EJECT_ITEMS = new Texture("overlay/slot_empty", 16, "v");
    static final Texture FILTERED_TAB = new Texture(gui("overlay/filtered_tab"), 20, 16, 0, 0x40FFFF55, "");
    static final Texture FAVOURITE = new Texture("icons/favourite_indicator", 16, "*");
    /** Two columns of 32x28 tabs: unselected, then selected. */
    static final ResourceLocation TABS = ResourceLocation
        .fromNamespaceAndPath(VendingMachine.MODID, "textures/gui/tabs_left.png");

    static final int PANEL_FILL = 0xFFA3ABC6;
    static final int LIGHT = 0xFFD2DCFF;
    static final int SHADOW = 0xFF464955;
    static final int SLOT_FILL = 0xFF72788B;
    static final int SLOT_SHADOW = 0xFF2D2F37;
    static final int TEXT = 0xFF404040;
    private static final int SCROLL_TRACK = 0xFF565A69;
    /** The vanilla scroll thumb greys in the theme tint. */
    private static final int THUMB = 0xFF8C92AA;
    private static final int THUMB_LIGHT = 0xFFC4CDEE;
    private static final int THUMB_SHADOW = 0xFF545866;
    private static final float TINT_RED = 0xA3 / (float) 0xC6;
    private static final float TINT_GREEN = 0xAB / (float) 0xC6;
    private static final int TITLE = 0xFF83899F;
    private static final int TITLE_LIGHT = 0xFFA9B1CD;
    private static final int TITLE_SHADOW = 0xFF383B44;
    private static final int BEVEL = 3;
    private static final Bevel PANEL_BEVEL = new Bevel(BEVEL, PANEL_FILL, LIGHT, SHADOW);
    private static final Bevel TITLE_BEVEL = new Bevel(BEVEL, TITLE, TITLE_LIGHT, TITLE_SHADOW);
    private static final Bevel BUTTON_BEVEL = new Bevel(1, SLOT_FILL, LIGHT, SLOT_SHADOW);
    private static final Bevel HOVERED_BUTTON_BEVEL = new Bevel(1, TITLE, LIGHT, SLOT_SHADOW);
    private static final Bevel THUMB_BEVEL = new Bevel(1, THUMB, THUMB_LIGHT, THUMB_SHADOW);

    private static final int GLYPH = 0xFFFFFFFF;
    /** Whether each texture is in the loaded resources; cleared on every resource reload. */
    private static final Map<ResourceLocation, Boolean> PRESENT = new ConcurrentHashMap<>();

    private Art() {}

    private static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath(VendingMachine.MODID, "textures/gui/" + path + ".png");
    }

    static ResourceLocation icon(String name) {
        return gui("icons/" + name);
    }

    static boolean present(ResourceLocation location) {
        return PRESENT.computeIfAbsent(
            location,
            l -> Minecraft.getInstance()
                .getResourceManager()
                .getResource(l)
                .isPresent());
    }

    static void forget() {
        PRESENT.clear();
    }

    /** Draws the whole image at {@code location} stretched over {@code area}, else {@code glyph} centred in it. */
    static void image(GuiGraphics g, ResourceLocation location, Box area, String glyph) {
        if (present(location)) {
            RenderSystem.enableBlend();
            g.blit(location, area.x(), area.y(), area.width(), area.height(), 0, 0, 1, 1, 1, 1);
            RenderSystem.disableBlend();
            return;
        }
        Font font = Minecraft.getInstance().font;
        g.drawString(
            font,
            glyph,
            area.x() + (area.width() - font.width(glyph) + 1) / 2,
            area.y() + (area.height() - font.lineHeight + 2) / 2,
            GLYPH);
    }

    /** Draws an icon texture whole over {@code area}, or its glyph. */
    static void image(GuiGraphics g, Texture texture, Box area) {
        if (texture.glyph()
            .isEmpty()) draw(g, texture, area);
        else image(g, texture.location(), area, texture.glyph());
    }

    /** A bevel in the theme's edges round {@code fill}, for art that is missing. */
    static void plain(GuiGraphics g, Box area, int fill) {
        bevel(g, area, new Bevel(1, fill, LIGHT, SHADOW));
    }

    /** Draws {@code texture} stretched to the area, keeping its border unstretched. */
    static void draw(GuiGraphics g, Texture texture, Box area) {
        if (!present(texture.location())) {
            int fill = texture.fallback();
            if (fill >>> 24 == 0xFF) plain(g, area, fill);
            else if (fill != 0) g.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), fill);
            return;
        }
        int b = texture.border();
        int x = area.x();
        int y = area.y();
        int width = area.width();
        int height = area.height();
        int innerU = texture.width() - 2 * b;
        int innerV = texture.height() - 2 * b;
        int right = texture.width() - b;
        int bottom = texture.height() - b;
        part(g, texture, new Box(x, y, b, b), new Box(0, 0, b, b));
        part(g, texture, new Box(x + width - b, y, b, b), new Box(right, 0, b, b));
        part(g, texture, new Box(x, y + height - b, b, b), new Box(0, bottom, b, b));
        part(g, texture, new Box(x + width - b, y + height - b, b, b), new Box(right, bottom, b, b));
        part(g, texture, new Box(x + b, y, width - 2 * b, b), new Box(b, 0, innerU, b));
        part(g, texture, new Box(x + b, y + height - b, width - 2 * b, b), new Box(b, bottom, innerU, b));
        part(g, texture, new Box(x, y + b, b, height - 2 * b), new Box(0, b, b, innerV));
        part(g, texture, new Box(x + width - b, y + b, b, height - 2 * b), new Box(right, b, b, innerV));
        part(g, texture, new Box(x + b, y + b, width - 2 * b, height - 2 * b), new Box(b, b, innerU, innerV));
    }

    static void draw(GuiGraphics g, Texture texture, int x, int y) {
        draw(g, texture, new Box(x, y, texture.width(), texture.height()));
    }

    /** Draws {@code texture} in the theme tint. */
    static void drawTinted(GuiGraphics g, Texture texture, Box area) {
        tint(1.0F);
        draw(g, texture, area);
        untint();
    }

    /**
     * Draws the {@code from} area of {@code t} stretched over {@code to}. Blended, so translucent art such as the
     * tradeable overlay lets the tile show through.
     */
    private static void part(GuiGraphics g, Texture t, Box to, Box from) {
        if (to.width() <= 0 || to.height() <= 0) return;
        RenderSystem.enableBlend();
        g.blit(
            t.location(),
            to.x(),
            to.y(),
            to.width(),
            to.height(),
            from.x(),
            from.y(),
            from.width(),
            from.height(),
            t.width(),
            t.height());
        RenderSystem.disableBlend();
    }

    /** Tints what is drawn next by the theme colour, darkened by {@code shade}. */
    static void tint(float shade) {
        RenderSystem.setShaderColor(TINT_RED * shade, TINT_GREEN * shade, shade, 1.0F);
    }

    static void untint() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The main panel: light top and left edges, dark bottom and right edges. */
    static void panel(GuiGraphics g, int x, int y, int width, int height) {
        bevel(g, new Box(x, y, width, height), PANEL_BEVEL);
    }

    /** The original's button: light top and left edges, dark bottom and right edges round a slot-coloured face. */
    static void button(GuiGraphics g, int x, int y, int size, boolean hovered) {
        bevel(g, new Box(x, y, size, size), hovered ? HOVERED_BUTTON_BEVEL : BUTTON_BEVEL);
    }

    /** The title tab above the panel, sized to its text. */
    static void title(GuiGraphics g, int x, int y, int width, int height) {
        bevel(g, new Box(x, y, width, height), TITLE_BEVEL);
    }

    private static void bevel(GuiGraphics g, Box area, Bevel style) {
        int x = area.x();
        int y = area.y();
        int right = x + area.width();
        int bottom = y + area.height();
        int edge = style.edge();
        g.fill(x, y, right, bottom, style.fill());
        g.fill(x, y, right - edge, y + edge, style.light());
        g.fill(x, y, x + edge, bottom - edge, style.light());
        g.fill(x + edge, bottom - edge, right, bottom, style.shadow());
        g.fill(right - edge, y + edge, right, bottom, style.shadow());
    }

    /** {@code item} drawn {@code size} pixels square with its top-left corner at {@code (x, y)}. */
    static void scaledItem(GuiGraphics g, ItemStack item, int x, int y, int size) {
        g.pose()
            .pushPose();
        g.pose()
            .translate(x, y, 0);
        g.pose()
            .scale(size / 16.0F, size / 16.0F, 1.0F);
        g.renderItem(item, 0, 0);
        g.pose()
            .popPose();
    }

    /** {@code text} drawn at the pen's scale times its size with its top-left corner at {@code (x, y)}. */
    static void scaledText(GuiGraphics g, Pen pen, String text, int x, int y) {
        g.pose()
            .pushPose();
        g.pose()
            .translate(x, y, 0);
        g.pose()
            .scale(pen.scale(), pen.scale(), 1.0F);
        g.drawString(pen.font(), text, 0, 0, pen.color(), false);
        g.pose()
            .popPose();
    }

    /**
     * The original's scroll bar over {@code track}: a dark track and a grey bevelled thumb, for a view as tall as the
     * track that shows part of {@code content} pixels, scrolled down by {@code scroll}. Nothing when everything fits.
     */
    static void scrollBar(GuiGraphics g, Box track, int content, int scroll) {
        int height = track.height();
        if (content <= height) return;
        int thumb = ScrollThumb.length(height, content);
        int top = track.y() + ScrollThumb.top(height, content, scroll);
        g.fill(track.x(), track.y(), track.x() + track.width(), track.y() + height, SCROLL_TRACK);
        bevel(g, new Box(track.x(), top, track.width(), thumb), THUMB_BEVEL);
    }

    /**
     * A vanilla-style 18x18 slot around the item position {@code (x, y)}: dark top and left, light bottom and
     * right.
     */
    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_FILL);
        g.fill(x - 1, y - 1, x + 16, y, SLOT_SHADOW);
        g.fill(x - 1, y, x, y + 16, SLOT_SHADOW);
        g.fill(x, y + 16, x + 17, y + 17, LIGHT);
        g.fill(x + 16, y, x + 17, y + 16, LIGHT);
    }

    /** An area of the screen or of a texture. */
    record Box(int x, int y, int width, int height) {}

    /** A filled rectangle with {@code edge}-pixel light top and left edges and shadowed bottom and right edges. */
    private record Bevel(int edge, int fill, int light, int shadow) {}

    /** How {@link #scaledText} draws: the font, the text's scale and its colour. */
    record Pen(Font font, float scale, int color) {}
}
