package com.cubefury.vendingmachine.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.client.ScrollThumb;
import com.cubefury.vendingmachine.client.WorldFavourites;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.Wallets;
import com.cubefury.vendingmachine.util.Currency;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/** Runs with Create and Create: Numismatics loaded, so the default currency is Numismatics coins. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class VendingMachineGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");
    /** A favourites entry for a trade: item id, count and price. */
    private static final String FAVOURITE = "minecraft:bread 3 5";

    private VendingMachineGameTests() {}

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name));
    }

    private static VendingMachineBlockEntity placeMachine(GameTestHelper h) {
        return Machines.build(h, MACHINE);
    }

    private static VendingMachineMenu openMenu(Player player, VendingMachineBlockEntity machine, Trade... trades) {
        return new VendingMachineMenu(0, player.getInventory(), machine, List.of(trades));
    }

    private static void insert(VendingMachineMenu menu, ItemStack coins) {
        menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .set(coins);
    }

    // The addresses are test inputs on either side of the private 172.16/12 range.
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @GameTest(template = "empty")
    public static void favouritesAreKeptPerWorld(GameTestHelper h) {
        h.assertTrue("SP_New World".equals(WorldFavourites.worldKey("New World", null)), "single player keys by save");
        h.assertTrue("LAN_192.168.1.5".equals(WorldFavourites.worldKey(null, "192.168.1.5:51234")), "LAN drops port");
        h.assertTrue("LAN_localhost".equals(WorldFavourites.worldKey(null, "localhost:25565")), "localhost is LAN");
        h.assertTrue("LAN_172.20.0.1".equals(WorldFavourites.worldKey(null, "172.20.0.1")), "172.16/12 is LAN");
        h.assertTrue("MP_172.32.0.1".equals(WorldFavourites.worldKey(null, "172.32.0.1")), "172.32 is not LAN");
        h.assertTrue("MP_play.example.net_25570".equals(WorldFavourites.worldKey(null, "play.example.net:25570")),
            "other servers keep host and port");
        List<String> one = WorldFavourites.toggle(List.of(), "SP_a", FAVOURITE);
        h.assertTrue(WorldFavourites.contains(one, "SP_a", FAVOURITE), "marked in its world");
        h.assertFalse(WorldFavourites.contains(one, "SP_b", FAVOURITE), "not in another world");
        h.assertTrue(WorldFavourites.toggle(one, "SP_a", FAVOURITE).isEmpty(), "toggling again clears it");
        List<String> adopted = WorldFavourites.adopt(List.of(), List.of(FAVOURITE), "MP_x");
        h.assertTrue(WorldFavourites.contains(adopted, "MP_x", FAVOURITE), "old list moves to the world");
        h.succeed();
    }

    /** A 144-high list of 288: the thumb is 72 long and travels 72 for a scroll range of 144. */
    @GameTest(template = "empty")
    public static void scrollThumbFollowsDragAndTrackClicks(GameTestHelper h) {
        int trackGrab = ScrollThumb.grab(144, 288, 0, 100);
        h.assertTrue(trackGrab == 36, "a track press should grab the thumb centre, got " + trackGrab);
        int jumped = ScrollThumb.scrollFor(144, 288, 100, trackGrab);
        h.assertTrue(ScrollThumb.top(144, 288, jumped) + 36 == 100, "the thumb centre should jump to the press");
        int thumbGrab = ScrollThumb.grab(144, 288, 0, 10);
        h.assertTrue(thumbGrab == 10, "a thumb press should keep its offset, got " + thumbGrab);
        h.assertTrue(ScrollThumb.scrollFor(144, 288, 46, thumbGrab) == 72, "dragging 36 down should scroll 72");
        h.assertTrue(ScrollThumb.scrollFor(144, 288, 500, thumbGrab) == 144, "dragging past the end should stop at it");
        h.assertTrue(ScrollThumb.scrollFor(144, 288, -50, thumbGrab) == 0, "dragging above the top should stop at it");
        h.assertTrue(ScrollThumb.scrollFor(144, 100, 50, 10) == 0, "a list shorter than the view should not scroll");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void currencyUsesNumismaticsCoinValues(GameTestHelper h) {
        h.assertTrue(Currency.unitValue(new ItemStack(coin("spur"))) == 1, "spur should be worth 1");
        h.assertTrue(Currency.unitValue(new ItemStack(coin("cog"))) == 64, "cog should be worth 64 spurs");
        h.assertTrue(Currency.unitValue(new ItemStack(Items.DIRT)) == 0, "dirt is not currency");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void insertedCoinsBecomeCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("cog"), 2));
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 128, "wallet should hold the value of two cogs, 128 spurs");
        h.assertTrue(
            menu.getSlot(VendingMachineMenu.COIN_SLOT)
                .getItem()
                .isEmpty(),
            "coin slot should be empty after insert");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buyWithEnoughCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 64));
        insert(menu, new ItemStack(coin("spur"), 6));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should succeed");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 6, "wallet should drop by the price");
        Chute.drain(machine);
        h.assertTrue(
            machine.output.getStackInSlot(0)
                .is(Items.BREAD)
                && machine.output.getStackInSlot(0)
                    .getCount() == 3,
            "output should hold the bought bread");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refuseWithTooLittleCredit(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 63));
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should be refused");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 63, "wallet should be unchanged");
        h.assertTrue(
            machine.output.getStackInSlot(0)
                .isEmpty(),
            "output should stay empty");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refuseWhenOutputIsFull(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        for (int i = 0; i < VendingMachineBlockEntity.OUTPUT_SLOTS; i++) {
            machine.output.setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 64));
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "buy should be refused");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 64, "no coins should be taken");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ejectReturnsWallet(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 5));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_EJECT_COINS), "eject should succeed");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 0, "wallet should be empty after eject");
        Chute.drain(machine);
        h.assertTrue(Chute.count(machine, coin("spur")) == 5, "eject should drop 5 spurs into the chute");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingKeepsWallet(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("spur"), 3));
        Wallets wallets = machine.wallets();
        h.destroyBlock(MACHINE);
        boolean dropped = h.getEntities(EntityType.ITEM, MACHINE, 2.0)
            .stream()
            .anyMatch(
                entity -> entity.getItem()
                    .is(coin("spur")));
        h.assertFalse(dropped, "breaking should drop no coins");
        h.assertTrue(wallets.total(player.getUUID()) == 3, "the wallet should keep the 3 spurs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void buySameEntryAgain(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        insert(menu, new ItemStack(coin("cog"), 2));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "first buy should succeed");
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "second buy should succeed");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 0, "both buys should take the price");
        Chute.drain(machine);
        h.assertTrue(Chute.count(machine, Items.BREAD) == 6, "the chute should hold bread from both buys");
        h.succeed();
    }

    private static DynamicOps<JsonElement> ops(GameTestHelper h) {
        return h.getLevel()
            .registryAccess()
            .createSerializationContext(JsonOps.INSTANCE);
    }

    @GameTest(template = "empty")
    public static void catalogueFileParses(GameTestHelper h) {
        var table = JsonParser.parseString("""
            [
                {"item": {"id": "minecraft:bread", "count": 3}, "price": 64, "sell_price": 8, "category": "food"},
                {"item": {"id": "minecraft:stone"}, "price": 1},
                {"item": {"id": "minecraft:apple"}, "price": 2, "category": "food"}
            ]""");
        var notATable = JsonParser
            .parseString("{\"item\": {\"id\": \"minecraft:dirt\"}, \"price\": 1, \"category\": \"x\"}");
        List<Trade> trades = TradeDatabase.parse(
            Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "food"),
                table,
                ResourceLocation.fromNamespaceAndPath("test", "single"),
                notATable),
            ops(h));
        h.assertTrue(trades.size() == 2, "bread and apple should load; stone has no category, single is no array");
        Trade bread = trades.get(0);
        h.assertTrue(bread.item()
            .is(Items.BREAD)
            && bread.item()
                .getCount() == 3,
            "bread item should match the file");
        h.assertTrue(bread.price() == 64
            && bread.sellPrice()
                .equals(Optional.of(8))
            && "food".equals(bread.category()),
            "bread fields should match the file");
        Trade apple = trades.get(1);
        h.assertTrue(apple.item()
            .is(Items.APPLE)
            && apple.sellPrice()
                .isEmpty(),
            "apple should load with no sell price");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadClosesOpenMenu(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(2.5, 1, 1.5)));
        var bread = JsonParser.parseString(
            "[{\"item\": {\"id\": \"minecraft:bread\"}, \"price\": 1, \"category\": \"food\"}]");
        TradeDatabase.load(Map.of(ResourceLocation.fromNamespaceAndPath("test", "reload"), bread), ops(h));
        VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, TradeDatabase.trades());
        h.assertTrue(menu.stillValid(player), "menu on the current catalogue should stay open");
        TradeDatabase.load(Map.of(), ops(h));
        h.assertFalse(menu.stillValid(player), "menu on a replaced catalogue should close");
        h.assertTrue(TradeDatabase.trades()
            .isEmpty(), "removed entries should be gone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void currencyWithUndividedCoinIsRefused(GameTestHelper h) {
        List<Currency.Denomination> even = List.of(
            new Currency.Denomination(coin("spur"), 1),
            new Currency.Denomination(coin("cog"), 64));
        h.assertTrue(Currency.usable(even).size() == 2, "spur 1 and cog 64 are both accepted");
        List<Currency.Denomination> uneven = List.of(
            new Currency.Denomination(coin("spur"), 4),
            new Currency.Denomination(coin("bevel"), 3));
        h.assertTrue(Currency.usable(uneven).isEmpty(), "a coin of 4 is refused when the smallest coin is 3");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void currencyListingOneItemTwiceIsRefused(GameTestHelper h) {
        List<Currency.Denomination> twice = List.of(
            new Currency.Denomination(coin("spur"), 9),
            new Currency.Denomination(coin("spur"), 1));
        h.assertTrue(Currency.usable(twice).isEmpty(), "one item listed at two values is refused");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void machineSoundsVaryInTheOriginalRanges(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        List<PlayLevelSoundEvent.AtPosition> sounds = machineSounds(() -> {
            insert(menu, new ItemStack(coin("spur"), 64));
            insert(menu, new ItemStack(coin("spur"), 6));
            menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
            Chute.drain(machine);
            menu.clickMenuButton(player, VendingMachineMenu.BUTTON_EJECT_COINS);
            Chute.drain(machine);
        });
        List<SoundEvent> played = sounds.stream()
            .map(
                sound -> sound.getSound()
                    .value())
            .toList();
        h.assertTrue(
            played.equals(
                List.of(
                    Registration.COIN_INSERT.get(),
                    Registration.COIN_INSERT.get(),
                    Registration.ITEM_DROP.get(),
                    Registration.COIN_DROP.get())),
            "expected two coin inserts, an item drop and a coin drop, got " + played);
        for (PlayLevelSoundEvent.AtPosition sound : sounds) {
            h.assertTrue(
                sound.getNewVolume() >= 0.6F && sound.getNewVolume() <= 1.0F,
                "volume " + sound.getNewVolume() + " should be from 0.6 to 1.0");
            h.assertTrue(
                sound.getNewPitch() >= 0.95F && sound.getNewPitch() <= 1.05F,
                "pitch " + sound.getNewPitch() + " should be from 0.95 to 1.05");
        }
        h.succeed();
    }

    /**
     * The sounds of this mod that {@code action} plays. Only the machine's own sounds; giving coins to the player also
     * plays the vanilla pickup sound.
     */
    private static List<PlayLevelSoundEvent.AtPosition> machineSounds(Runnable action) {
        List<PlayLevelSoundEvent.AtPosition> sounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = sound -> {
            if (sound.getSound()
                .value()
                .getLocation()
                .getNamespace()
                .equals(VendingMachine.MODID)) sounds.add(sound);
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            action.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return sounds;
    }
}
