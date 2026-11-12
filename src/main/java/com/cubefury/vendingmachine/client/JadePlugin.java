package com.cubefury.vendingmachine.client;

import net.minecraft.resources.ResourceLocation;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows the machine's structure status when looking at it, as the original's WAILA tooltip did. Jade finds this class
 * by its annotation, so nothing loads it when Jade is not installed.
 */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin, IBlockComponentProvider {

    private static final ResourceLocation UID = ResourceLocation
        .fromNamespaceAndPath(VendingMachine.MODID, "structure_status");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(this, VendingMachineBlock.class);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(VendingMachineBlock.structureStatus(accessor.getBlockState()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
