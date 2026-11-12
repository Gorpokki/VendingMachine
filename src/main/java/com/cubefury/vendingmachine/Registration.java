package com.cubefury.vendingmachine;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.cubefury.vendingmachine.blocks.ShippingBinBlock;
import com.cubefury.vendingmachine.blocks.ShippingBinBlockEntity;
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.VendingMachineCasingBlock;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;

public final class Registration {

    /** The registry path of the machine's block, item, block entity and menu. */
    private static final String MACHINE_ID = "vending_machine";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VendingMachine.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VendingMachine.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, VendingMachine.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
        .create(Registries.MENU, VendingMachine.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister
        .create(Registries.SOUND_EVENT, VendingMachine.MODID);

    public static final Supplier<SoundEvent> COIN_INSERT = sound("coin_insert");
    public static final Supplier<SoundEvent> COIN_DROP = sound("coin_drop");
    public static final Supplier<SoundEvent> ITEM_DROP = sound("item_drop");

    public static final DeferredBlock<VendingMachineBlock> VENDING_MACHINE = BLOCKS.register(
        MACHINE_ID,
        () -> new VendingMachineBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> VENDING_MACHINE_ITEM = ITEMS
        .registerSimpleBlockItem(VENDING_MACHINE, new Item.Properties());

    public static final DeferredBlock<VendingMachineCasingBlock> VENDING_MACHINE_CASING = BLOCKS.register(
        "vending_machine_casing",
        () -> new VendingMachineCasingBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> VENDING_MACHINE_CASING_ITEM = ITEMS
        .registerSimpleBlockItem(VENDING_MACHINE_CASING, new Item.Properties());

    public static final Supplier<BlockEntityType<VendingMachineBlockEntity>> VENDING_MACHINE_BE = BLOCK_ENTITIES
        .register(
            MACHINE_ID,
            () -> BlockEntityType.Builder.of(VendingMachineBlockEntity::new, VENDING_MACHINE.get())
                .build(null));

    public static final Supplier<MenuType<VendingMachineMenu>> VENDING_MACHINE_MENU = MENUS
        .register(MACHINE_ID, () -> IMenuTypeExtension.create(VendingMachineMenu::new));

    public static final DeferredBlock<ShippingBinBlock> SHIPPING_BIN = BLOCKS.register(
        "shipping_bin",
        () -> new ShippingBinBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD)));

    public static final DeferredItem<BlockItem> SHIPPING_BIN_ITEM = ITEMS
        .registerSimpleBlockItem(SHIPPING_BIN, new Item.Properties());

    public static final Supplier<BlockEntityType<ShippingBinBlockEntity>> SHIPPING_BIN_BE = BLOCK_ENTITIES.register(
        "shipping_bin",
        () -> BlockEntityType.Builder.of(ShippingBinBlockEntity::new, SHIPPING_BIN.get())
            .build(null));

    private Registration() {}

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(
            name,
            () -> SoundEvent
                .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(VendingMachine.MODID, name)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                e.accept(VENDING_MACHINE_ITEM);
                e.accept(VENDING_MACHINE_CASING_ITEM);
                e.accept(SHIPPING_BIN_ITEM);
            }
        });
    }
}
