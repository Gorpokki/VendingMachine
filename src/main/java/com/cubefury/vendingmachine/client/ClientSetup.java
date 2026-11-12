package com.cubefury.vendingmachine.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;

// NeoForge constructs the client mod class through its public constructor.
@SuppressWarnings("java:S1118")
@Mod(value = VendingMachine.MODID, dist = Dist.CLIENT)
public final class ClientSetup {

    public ClientSetup(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientSettings.SPEC);
        modBus.addListener(RegisterMenuScreensEvent.class, ClientSetup::registerScreens);
        modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, ClientSetup::registerTooltips);
        modBus.addListener(
            RegisterClientReloadListenersEvent.class,
            e -> e.registerReloadListener((ResourceManagerReloadListener) resources -> Art.forget()));
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, ClientSetup::machineTooltip);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.VENDING_MACHINE_MENU.get(), VendingMachineScreen::new);
    }

    /** The machine item's lines go right under its name, as the original's do. */
    private static void machineTooltip(ItemTooltipEvent event) {
        if (event.getItemStack()
            .is(Registration.VENDING_MACHINE_ITEM)) {
            event.getToolTip()
                .addAll(1, VendingMachineBlock.tooltip(Screen.hasShiftDown()));
        }
    }

    private static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PriceLine.class, PriceLine.Client::new);
    }
}
