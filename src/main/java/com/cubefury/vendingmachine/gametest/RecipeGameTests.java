package com.cubefury.vendingmachine.gametest;

import java.util.Arrays;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;

/** The machine, its casing and the bin craft through the server's own recipe manager. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {

    private RecipeGameTests() {}

    private static ItemStack craft(GameTestHelper h, Item... grid) {
        List<ItemStack> items = Arrays.stream(grid)
            .map(item -> item == null ? ItemStack.EMPTY : new ItemStack(item))
            .toList();
        CraftingInput input = CraftingInput.of(3, 3, items);
        return h.getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, h.getLevel())
            .map(
                recipe -> recipe.value()
                    .assemble(input, h.getLevel()
                        .registryAccess()))
            .orElse(ItemStack.EMPTY);
    }

    /** The recipe loads as a shaped 3x3 recipe whose slots hold exactly {@code slots}, row by row (null = empty). */
    private static void assertSlots(GameTestHelper h, String recipeId, Item... slots) {
        RecipeHolder<?> holder = h.getLevel()
            .getRecipeManager()
            .byKey(ResourceLocation.parse(recipeId))
            .orElse(null);
        h.assertTrue(holder != null && holder.value() instanceof ShapedRecipe, recipeId + " should load as shaped");
        ShapedRecipe recipe = (ShapedRecipe) holder.value();
        h.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3, recipeId + " should be 3x3");
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < 9; i++) {
            Item want = slots[i];
            ItemStack[] got = ingredients.get(i)
                .getItems();
            boolean ok = want == null ? ingredients.get(i)
                .isEmpty() : got.length == 1 && got[0].is(want);
            h.assertTrue(ok, recipeId + " slot " + i + " should be " + want + ", got " + Arrays.toString(got));
        }
    }

    @GameTest(template = "empty")
    public static void vendingMachineCraftsFromButtonsIngotsCasingAndHopper(GameTestHelper h) {
        Item b = Items.STONE_BUTTON;
        Item i = Items.IRON_INGOT;
        Item casing = Registration.VENDING_MACHINE_CASING_ITEM.get();
        Item[] grid = { b, i, b, i, casing, i, null, Items.HOPPER, null };
        assertSlots(h, "vendingmachine:vending_machine", grid);
        ItemStack result = craft(h, grid);
        h.assertTrue(
            result.is(Registration.VENDING_MACHINE_ITEM.get()) && result.getCount() == 1,
            "should craft one vending machine, got " + result);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void casingCraftsFromIronIngotsNuggetsAndCopperIngot(GameTestHelper h) {
        Item i = Items.IRON_INGOT;
        Item n = Items.IRON_NUGGET;
        Item[] grid = { i, n, i, n, Items.COPPER_INGOT, n, i, n, i };
        assertSlots(h, "vendingmachine:vending_machine_casing", grid);
        ItemStack result = craft(h, grid);
        h.assertTrue(
            result.is(Registration.VENDING_MACHINE_CASING_ITEM.get()) && result.getCount() == 1,
            "should craft one vending machine casing, got " + result);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shippingBinCraftsFromPlanksSlabsAndChest(GameTestHelper h) {
        Item p = Items.OAK_PLANKS;
        Item s = Items.SPRUCE_SLAB;
        ItemStack result = craft(h, s, null, s, p, Items.CHEST, p, Items.BIRCH_PLANKS, p, p);
        h.assertTrue(result.is(Registration.SHIPPING_BIN_ITEM.get()), "should craft a shipping bin, got " + result);
        h.succeed();
    }
}
