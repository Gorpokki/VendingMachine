package com.cubefury.vendingmachine.gametest;

import java.util.Arrays;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;

/** The machine and the bin craft from early vanilla items, through the server's own recipe manager. */
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

    @GameTest(template = "empty")
    public static void vendingMachineCraftsFromCopperGlassAndChest(GameTestHelper h) {
        Item c = Items.COPPER_INGOT;
        ItemStack result = craft(h, c, Items.GLASS_PANE, c, c, Items.CHEST, c, c, c, c);
        h.assertTrue(
            result.is(Registration.VENDING_MACHINE_ITEM.get()),
            "should craft a vending machine, got " + result);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fiveCasingsCraftFromIronAndCopper(GameTestHelper h) {
        Item i = Items.IRON_INGOT;
        Item c = Items.COPPER_INGOT;
        ItemStack result = craft(h, i, c, i, c, i, c, i, c, i);
        h.assertTrue(result.is(Registration.VENDING_MACHINE_CASING_ITEM.get()) && result.getCount() == 5,
            "should craft five vending machine casings, got " + result);
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
