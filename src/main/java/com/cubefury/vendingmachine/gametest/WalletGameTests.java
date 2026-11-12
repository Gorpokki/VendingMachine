package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.Wallets;
import com.cubefury.vendingmachine.util.Currency;

/** The per-player wallet, with Create: Numismatics coins: spur 1, bevel 8, sprocket 16, cog 64, crown 512, sun 4096. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class WalletGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos OTHER_MACHINE = new BlockPos(3, 1, 1);
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");
    private static final Trade APPLE = new Trade(new ItemStack(Items.APPLE), 8, Optional.empty(), "food");

    private WalletGameTests() {}

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name));
    }

    private static VendingMachineBlockEntity placeMachine(GameTestHelper h, BlockPos pos) {
        return Machines.build(h, pos);
    }

    private static VendingMachineMenu openMenu(Player player, VendingMachineBlockEntity machine, Trade... trades) {
        return new VendingMachineMenu(0, player.getInventory(), machine, List.of(trades));
    }

    private static int coinIndex(Item coin) {
        List<Currency.Denomination> coins = Currency.denominations();
        for (int i = 0; i < coins.size(); i++) {
            if (coins.get(i)
                .item() == coin) return i;
        }
        throw new IllegalArgumentException("not a coin: " + coin);
    }

    @GameTest(template = "empty")
    public static void walletIsSharedByEveryMachine(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity first = placeMachine(h, MACHINE);
        VendingMachineBlockEntity second = placeMachine(h, OTHER_MACHINE);
        openMenu(player, first, BREAD).getSlot(VendingMachineMenu.COIN_SLOT)
            .set(new ItemStack(coin("cog"), 1));
        h.assertTrue(
            openMenu(player, second, BREAD).clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE),
            "coins put into one machine should pay at another");
        h.assertTrue(
            second.wallets()
                .total(player.getUUID()) == 0,
            "the purchase should spend the wallet");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyInSlotTakesCoinsOnly(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = placeMachine(h, MACHINE);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        for (int i = 0; i < VendingMachineMenu.IN_SLOTS; i++) {
            h.assertFalse(
                menu.getSlot(i)
                    .mayPlace(new ItemStack(Items.DIRT)),
                "IN slot " + i + " should refuse dirt");
            menu.getSlot(i)
                .set(new ItemStack(coin("spur"), 1));
            h.assertTrue(
                menu.getSlot(i)
                    .getItem()
                    .isEmpty(),
                "IN slot " + i + " should empty into the wallet");
        }
        h.assertTrue(
            machine.wallets()
                .total(player.getUUID()) == VendingMachineMenu.IN_SLOTS,
            "each IN slot should add its spur to the wallet");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changeStaysInWallet(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = placeMachine(h, MACHINE);
        VendingMachineMenu menu = openMenu(player, machine, APPLE);
        menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .set(new ItemStack(coin("cog"), 1));
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "the cog should pay 8");
        Wallets wallets = machine.wallets();
        UUID id = player.getUUID();
        h.assertTrue(wallets.total(id) == 56, "the 56 spurs of change should stay in the wallet");
        h.assertTrue(wallets.count(id, coin("cog")) == 0, "the cog should be spent");
        h.assertTrue(
            wallets.count(id, coin("sprocket")) == 3 && wallets.count(id, coin("bevel")) == 1,
            "change should come as the fewest coins: three sprockets and a bevel");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void takeOutOneCoinType(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = placeMachine(h, MACHINE);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        menu.getSlot(0)
            .set(new ItemStack(coin("cog"), 2));
        menu.getSlot(1)
            .set(new ItemStack(coin("spur"), 5));
        h.assertTrue(
            menu.clickMenuButton(player, VendingMachineMenu.ejectCoinButton(coinIndex(coin("cog")))),
            "taking cogs out should succeed");
        Chute.drain(machine);
        h.assertTrue(Chute.count(machine, coin("cog")) == 2, "both cogs should drop into the chute");
        h.assertTrue(
            machine.wallets()
                .total(player.getUUID()) == 5,
            "the spurs should stay in the wallet");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void takeOutputsMovesBoughtItemsToInventory(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = placeMachine(h, MACHINE);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .set(new ItemStack(coin("cog"), 2));
        menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
        menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
        Chute.drain(machine);
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_TAKE_OUTPUTS), "take should succeed");
        h.assertTrue(
            player.getInventory()
                .countItem(Items.BREAD) == 6,
            "all bought bread should reach the inventory");
        h.assertTrue(
            machine.output.getStackInSlot(0)
                .isEmpty(),
            "the chute should be empty");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyCreditMovesToWallet(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h, MACHINE);
        UUID owner = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("player", owner);
        entry.putLong("amount", 70);
        ListTag credit = new ListTag();
        credit.add(entry);
        CompoundTag saved = new CompoundTag();
        saved.put("credit", credit);
        machine.loadWithComponents(
            saved,
            h.getLevel()
                .registryAccess());
        machine.onLoad();
        h.assertTrue(
            machine.wallets()
                .total(owner) == 70,
            "the machine's 70 credit should reach the owner's wallet");
        machine.onLoad();
        h.assertTrue(
            machine.wallets()
                .total(owner) == 70,
            "the credit should move only once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void walletKeepsUnregisteredCoins(GameTestHelper h) {
        String owner = UUID.randomUUID()
            .toString();
        CompoundTag coins = new CompoundTag();
        coins.putLong("removedmod:coin", 5);
        coins.putLong("numismatics:cog", 2);
        CompoundTag saved = new CompoundTag();
        saved.put(owner, coins);
        HolderLookup.Provider registries = h.getLevel()
            .registryAccess();
        Wallets wallets = Wallets.load(saved);
        h.assertTrue(wallets.total(UUID.fromString(owner)) == 128, "the 2 cogs should load");
        CompoundTag resaved = wallets.save(new CompoundTag(), registries)
            .getCompound(owner);
        h.assertTrue(
            resaved.getLong("removedmod:coin") == 5 && resaved.getLong("numismatics:cog") == 2,
            "saving again should keep the unregistered coins next to the cogs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void menuSendsWalletCounts(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VendingMachineBlockEntity machine = placeMachine(h, MACHINE);
        VendingMachineMenu menu = openMenu(player, machine, BREAD);
        menu.getSlot(0)
            .set(new ItemStack(coin("cog"), 3));
        h.assertTrue(menu.walletCount(coinIndex(coin("cog"))) == 3, "the menu should send 3 cogs");
        h.assertTrue(menu.walletCount(coinIndex(coin("spur"))) == 0, "the menu should send no spurs");
        h.assertTrue(menu.canPay(BREAD.price()), "three cogs should pay for bread");
        h.assertFalse(menu.canPay(193), "three cogs should not pay 193");
        h.succeed();
    }
}
