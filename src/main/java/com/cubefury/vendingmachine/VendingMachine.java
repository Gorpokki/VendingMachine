package com.cubefury.vendingmachine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cubefury.vendingmachine.blocks.VendingMachineBlock;
import com.cubefury.vendingmachine.command.VendingCommand;
import com.cubefury.vendingmachine.integration.MeStorageLink;
import com.cubefury.vendingmachine.trade.Categories;
import com.cubefury.vendingmachine.trade.StoreTrades;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Currency;

// NeoForge constructs the mod class through its public constructor.
@SuppressWarnings({ "PMD.InstantiableUtilityClass", "java:S1118" })
@Mod(VendingMachine.MODID)
public class VendingMachine {

    public static final String MODID = "vendingmachine";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public VendingMachine(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, VMConfig.SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, e -> Currency.invalidate());
        NeoForge.EVENT_BUS
            .addListener(PlayerInteractEvent.RightClickBlock.class, VendingMachineBlock::allowSneakDeposit);
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> {
            e.addListener(new TradeDatabase(e.getRegistryAccess()));
            e.addListener(new Categories());
        });
        modBus.addListener(RegisterPayloadHandlersEvent.class, StoreTrades::registerPayload);
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, StoreTrades::send);
        NeoForge.EVENT_BUS
            .addListener(RegisterCommandsEvent.class, e -> VendingCommand.register(e.getDispatcher()));
        if (ModList.get()
            .isLoaded("ae2")) {
            modBus.addListener(MeStorageLink::registerCapabilities);
        }
    }
}
