package com.cubefury.vendingmachine.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;

@EventBusSubscriber(modid = VendingMachine.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.VENDING_MACHINE_MENU.get(), VendingMachineScreen::new);
    }
}
