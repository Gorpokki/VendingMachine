package com.cubefury.vendingmachine.client.viewer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.trade.StoreTrades;
import com.cubefury.vendingmachine.trade.StoreTrades.Entry;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;

/**
 * The EMI page of store trades. EMI finds it by its annotation, so it only loads when EMI is installed. With JEI also
 * installed, EMI skips the JEI page, since this category has the same id.
 */
@EmiEntrypoint
public final class EmiTrades implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory category = new EmiRecipeCategory(TradePage.ID, EmiStack.of(TradePage.machine())) {

            @Override
            public Component getName() {
                return TradePage.title();
            }
        };
        registry.addCategory(category);
        registry.addWorkstation(category, EmiStack.of(TradePage.machine()));
        List<Entry> entries = StoreTrades.synced();
        for (int i = 0; i < entries.size(); i++) {
            registry.addRecipe(new Trade(category, i, entries.get(i)));
        }
    }

    private static final class Trade implements EmiRecipe {

        private final EmiRecipeCategory category;
        private final ResourceLocation id;
        private final List<EmiIngredient> inputs;
        private final int keptFrom;
        private final List<EmiStack> outputs;

        Trade(EmiRecipeCategory category, int index, Entry entry) {
            this.category = category;
            // A leading slash marks an id EMI synthesised, not one from a data pack.
            this.id = ResourceLocation.fromNamespaceAndPath(VendingMachine.MODID, "/trade/" + index);
            List<EmiIngredient> slots = new ArrayList<>();
            entry.price()
                .forEach(coin -> slots.add(EmiStack.of(coin)));
            entry.items()
                .forEach(item -> slots.add(choices(item)));
            this.keptFrom = slots.size();
            entry.kept()
                .forEach(item -> slots.add(choices(item)));
            this.inputs = List.copyOf(slots);
            this.outputs = entry.outputs()
                .stream()
                .map(EmiStack::of)
                .toList();
        }

        private static EmiIngredient choices(List<ItemStack> stacks) {
            return EmiIngredient.of(
                stacks.stream()
                    .map(EmiStack::of)
                    .toList());
        }

        @Override
        public EmiRecipeCategory getCategory() {
            return category;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public List<EmiIngredient> getInputs() {
            return inputs;
        }

        @Override
        public List<EmiStack> getOutputs() {
            return outputs;
        }

        @Override
        public int getDisplayWidth() {
            return TradePage.width(outputs.size());
        }

        @Override
        public int getDisplayHeight() {
            return TradePage.height(inputs.size(), outputs.size());
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            for (int i = 0; i < inputs.size(); i++) {
                SlotWidget slot = widgets.addSlot(inputs.get(i), TradePage.inputX(i), TradePage.inputY(i));
                if (i >= keptFrom) slot.appendTooltip(TradePage.kept());
            }
            widgets.addTexture(EmiTexture.EMPTY_ARROW, TradePage.ARROW_X, TradePage.ARROW_Y);
            for (int i = 0; i < outputs.size(); i++) {
                widgets.addSlot(outputs.get(i), TradePage.outputX(i), TradePage.outputY(i))
                    .recipeContext(this);
            }
        }
    }
}
