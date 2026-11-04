package com.cubefury.vendingmachine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;

@Mod(VendingMachine.MODID)
public class VendingMachine {

    public static final String MODID = "vendingmachine";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public VendingMachine(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, VMConfig.SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, e -> Currency.invalidate());
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class,
            e -> e.addListener(new TradeDatabase(e.getRegistryAccess())));
    }
}
