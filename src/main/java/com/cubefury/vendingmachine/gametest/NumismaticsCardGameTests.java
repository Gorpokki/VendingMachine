package com.cubefury.vendingmachine.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.bank.AuthorizedCardItem;
import dev.ithundxr.createnumismatics.content.bank.CardItem;

/** Card payment, with Create: Numismatics loaded. Prices and balances are in spurs. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class NumismaticsCardGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 64, Optional.empty(), "food");

    private NumismaticsCardGameTests() {}

    private static ItemStack numismatics(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name)));
    }

    private static BankAccount account(UUID owner, int balance) {
        BankAccount account = Numismatics.BANK.getOrCreateAccount(owner, BankAccount.Type.PLAYER);
        account.setBalance(balance);
        return account;
    }

    private static ItemStack boundCard(UUID owner) {
        return CardItem.set(numismatics("white_card"), owner);
    }

    /** An authorized card for a new sub-account of {@code account}, usable by the {@code trusted} players only. */
    private static ItemStack authorizedCard(BankAccount account, UUID... trusted) {
        UUID authorization = account.addSubAccount("team");
        account.getSubAccountNoAuth(authorization)
            .getTrustList()
            .addAll(List.of(trusted));
        return AuthorizedCardItem
            .set(
                numismatics("white_authorized_card"),
                new AuthorizedCardItem.AuthorizationPair(account.id, authorization));
    }

    private record Setup(VendingMachineBlockEntity machine, Player player, VendingMachineMenu menu) {

        boolean buy() {
            return menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
        }

        void insert(int slot, ItemStack stack) {
            menu.getSlot(slot)
                .set(stack);
        }

        int bread() {
            Chute.drain(machine);
            return machine.output.getStackInSlot(0)
                .is(Items.BREAD)
                    ? machine.output.getStackInSlot(0)
                        .getCount()
                    : 0;
        }
    }

    private static Setup setup(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.build(h, MACHINE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        return new Setup(machine, player, new VendingMachineMenu(0, player.getInventory(), machine, List.of(BREAD)));
    }

    @GameTest(template = "empty")
    public static void boundCardPaysBeforeCoins(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount account = account(s.player()
            .getUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(account.id));
        s.insert(VendingMachineMenu.COIN_SLOT, new ItemStack(numismatics("spur").getItem(), 64));
        h.assertTrue(s.buy(), "card should pay");
        h.assertTrue(account.getBalance() == 36, "card account should drop by the price");
        h.assertTrue(s.machine()
            .wallets()
            .total(s.player()
                .getUUID()) == 64,
            "coins should not pay while the card can");
        h.assertTrue(s.bread() == 3, "output should hold bought bread");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coinsPayWhenCardBalanceIsLow(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount account = account(s.player()
            .getUUID(), 10);
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(account.id));
        s.insert(VendingMachineMenu.COIN_SLOT, new ItemStack(numismatics("spur").getItem(), 64));
        h.assertTrue(s.buy(), "coins should pay");
        h.assertTrue(account.getBalance() == 10, "card account should be unchanged");
        h.assertTrue(s.machine()
            .wallets()
            .total(s.player()
                .getUUID()) == 0,
            "wallet should drop by the price");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void authorizedCardPaysFromSharedAccount(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount shared = account(UUID.randomUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, authorizedCard(shared, s.player()
            .getUUID()));
        h.assertTrue(s.buy(), "authorized card should pay");
        h.assertTrue(shared.getBalance() == 36, "shared account should drop by the price");
        h.assertTrue(s.bread() == 3, "output should hold bought bread");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cardOfAnotherPlayerPaysNothing(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount shared = account(UUID.randomUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, authorizedCard(shared));
        h.assertFalse(s.buy(), "authorized card without trust should be refused");
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(shared.id));
        h.assertFalse(s.buy(), "card bound to another player should be refused");
        h.assertTrue(shared.getBalance() == 100, "account should be unchanged");
        h.assertTrue(s.bread() == 0, "output should stay empty");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullOutputLeavesCardBalance(GameTestHelper h) {
        Setup s = setup(h);
        for (int i = 0; i < VendingMachineBlockEntity.OUTPUT_SLOTS; i++) {
            s.machine().output.setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        BankAccount account = account(s.player()
            .getUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(account.id));
        h.assertFalse(s.buy(), "buy should be refused");
        h.assertTrue(account.getBalance() == 100, "card account should be unchanged");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void menuSendsBoundCardBalance(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount account = account(s.player()
            .getUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(account.id));
        h.assertTrue(s.menu()
            .cardBalance() == 100, "the menu should send the account balance");
        h.assertTrue(s.menu()
            .canPay(BREAD.price()), "a funded card should pay with an empty wallet");
        account.setBalance(10);
        h.assertTrue(s.menu()
            .cardBalance() == 10, "the menu should follow the account balance");
        h.assertFalse(s.menu()
            .canPay(BREAD.price()), "a card with 10 spurs should not pay 64");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void menuSendsAuthorizedCardLimit(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount shared = account(UUID.randomUUID(), 100);
        ItemStack card = authorizedCard(shared, s.player()
            .getUUID());
        UUID authorization = AuthorizedCardItem.get(card).authorizationID();
        shared.getSubAccountNoAuth(authorization)
            .getTotalLimit()
            .setLimit(30, false);
        s.insert(VendingMachineMenu.CARD_SLOT, card);
        h.assertTrue(s.menu()
            .cardBalance() == 30, "the sub-account limit should cap the balance");
        shared.setBalance(20);
        h.assertTrue(s.menu()
            .cardBalance() == 20, "the shared account's balance should cap the balance");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hiddenCoinRowKeepsShiftClickedCardOut(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount account = account(s.player()
            .getUUID(), 100);
        ItemStack card = boundCard(account.id);
        s.menu()
            .clickMenuButton(s.player(), VendingMachineMenu.BUTTON_COIN_ROW_HIDDEN);
        s.insert(VendingMachineMenu.FIRST_PLAYER_SLOT, card);
        h.assertTrue(
            s.menu()
                .quickMoveStack(s.player(), VendingMachineMenu.FIRST_PLAYER_SLOT)
                .isEmpty(),
            "shift-click should not move a card into the hidden card slot");
        h.assertFalse(s.menu()
            .getSlot(VendingMachineMenu.CARD_SLOT)
            .hasItem(), "the hidden card slot should stay empty");
        s.menu()
            .clickMenuButton(s.player(), VendingMachineMenu.BUTTON_COIN_ROW_SHOWN);
        s.menu()
            .quickMoveStack(s.player(), VendingMachineMenu.FIRST_PLAYER_SLOT);
        h.assertTrue(s.menu()
            .getSlot(VendingMachineMenu.CARD_SLOT)
            .hasItem(), "shift-click should move a card into the shown card slot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hiddenCoinRowKeepsCardInSlotPaying(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount account = account(s.player()
            .getUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(account.id));
        s.menu()
            .clickMenuButton(s.player(), VendingMachineMenu.BUTTON_COIN_ROW_HIDDEN);
        h.assertTrue(s.buy(), "a card already in the hidden card slot should still pay");
        h.assertTrue(account.getBalance() == 36, "the card account should drop by the price");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void menuSendsZeroForForeignCard(GameTestHelper h) {
        Setup s = setup(h);
        BankAccount shared = account(UUID.randomUUID(), 100);
        s.insert(VendingMachineMenu.CARD_SLOT, authorizedCard(shared));
        h.assertTrue(s.menu()
            .cardBalance() == 0, "an authorized card without trust should count as zero");
        s.insert(VendingMachineMenu.CARD_SLOT, boundCard(shared.id));
        h.assertTrue(s.menu()
            .cardBalance() == 0, "a card bound to another player should count as zero");
        h.succeed();
    }
}
