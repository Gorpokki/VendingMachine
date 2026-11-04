package com.cubefury.vendingmachine;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class VMConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CURRENCY = BUILDER.comment(
        "Coin items the machine accepts, as \"<item id>=<value>\". Trade prices use the same unit.",
        "Leave empty to use Create: Numismatics coins with their own values (1 = one spur).")
        .defineListAllowEmpty("currency", List.of(), () -> "minecraft:emerald=1", VMConfig::isCurrencyEntry);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private VMConfig() {}

    private static boolean isCurrencyEntry(Object o) {
        if (!(o instanceof String s)) return false;
        String[] parts = s.split("=");
        if (parts.length != 2 || ResourceLocation.tryParse(parts[0].trim()) == null) return false;
        try {
            return Integer.parseInt(parts[1].trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
