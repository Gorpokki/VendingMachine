package com.cubefury.vendingmachine.client.viewer;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.trade.StoreTrades;
import com.cubefury.vendingmachine.trade.StoreTrades.Entry;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

/** The JEI page of store trades. JEI finds it by its annotation, so it only loads when JEI is installed. */
@JeiPlugin
public final class JeiTrades implements IModPlugin {

    private static final RecipeType<Entry> TYPE = new RecipeType<>(TradePage.ID, Entry.class);

    @Override
    public ResourceLocation getPluginUid() {
        return TradePage.ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers()
            .getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, StoreTrades.synced());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(TradePage.machine(), TYPE);
    }

    private static final class Category implements IRecipeCategory<Entry> {

        private final IDrawable icon;
        private final IDrawable arrow;

        Category(IGuiHelper gui) {
            icon = gui.createDrawableItemStack(TradePage.machine());
            arrow = gui.getRecipeArrow();
        }

        @Override
        public RecipeType<Entry> getRecipeType() {
            return TYPE;
        }

        @Override
        public Component getTitle() {
            return TradePage.title();
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return TradePage.width(mostOutputs());
        }

        @Override
        public int getHeight() {
            return TradePage.height(
                StoreTrades.synced()
                    .stream()
                    .mapToInt(TradePage::inputs)
                    .max()
                    .orElse(0),
                mostOutputs());
        }

        private static int mostOutputs() {
            return StoreTrades.synced()
                .stream()
                .mapToInt(entry -> entry.outputs()
                    .size())
                .max()
                .orElse(1);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
            int slot = 0;
            for (ItemStack coin : entry.price()) {
                input(builder, slot).addItemStack(coin);
                slot++;
            }
            for (List<ItemStack> item : entry.items()) {
                input(builder, slot).addItemStacks(item);
                slot++;
            }
            for (List<ItemStack> item : entry.kept()) {
                input(builder, slot).addItemStacks(item)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(TradePage.kept()));
                slot++;
            }
            List<ItemStack> outputs = entry.outputs();
            for (int i = 0; i < outputs.size(); i++) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, TradePage.outputX(i) + 1, TradePage.outputY(i) + 1)
                    .addItemStack(outputs.get(i))
                    .setStandardSlotBackground();
            }
        }

        private static IRecipeSlotBuilder input(IRecipeLayoutBuilder builder, int slot) {
            return builder.addSlot(RecipeIngredientRole.INPUT, TradePage.inputX(slot) + 1, TradePage.inputY(slot) + 1)
                .setStandardSlotBackground();
        }

        @Override
        public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, TradePage.ARROW_X, TradePage.ARROW_Y);
        }
    }
}
