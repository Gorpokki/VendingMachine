package com.cubefury.vendingmachine.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.cubefury.vendingmachine.Registration;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.VendingMachineBlock;
import com.cubefury.vendingmachine.blocks.VendingMachineBlockEntity;
import com.cubefury.vendingmachine.blocks.gui.VendingMachineMenu;
import com.cubefury.vendingmachine.trade.Trade;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

/** The machine's behaviour at the block, as in the original: one user, sneak deposit, the chute queue, sounds. */
@GameTestHolder(VendingMachine.MODID)
@PrefixGameTestTemplate(false)
public final class MachineBehaviourGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final Trade BREAD = new Trade(new ItemStack(Items.BREAD, 3), 1, Optional.empty(), "food");

    private MachineBehaviourGameTests() {}

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", name));
    }

    private static VendingMachineBlockEntity placeMachine(GameTestHelper h) {
        return Machines.build(h, MACHINE);
    }

    private static BlockHitResult hit(GameTestHelper h) {
        return new BlockHitResult(Vec3.atCenterOf(h.absolutePos(MACHINE)), Direction.NORTH, h.absolutePos(MACHINE),
            false);
    }

    private static void use(GameTestHelper h, ServerPlayer player) {
        h.getBlockState(MACHINE)
            .useWithoutItem(h.getLevel(), player, hit(h));
    }

    private static boolean usesMachine(ServerPlayer player) {
        return player.containerMenu instanceof VendingMachineMenu;
    }

    /** Pays {@code spurs} into the wallet and buys bread that many times; the stacks wait in the queue. */
    private static void buyBread(ServerPlayer player, VendingMachineBlockEntity machine, int times) {
        VendingMachineMenu menu = new VendingMachineMenu(0, player.getInventory(), machine, List.of(BREAD));
        machine.deposit(player, new ItemStack(coin("spur"), times));
        for (int i = 0; i < times; i++) {
            menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE);
        }
    }

    private static int stacksInChute(VendingMachineBlockEntity machine) {
        int stacks = 0;
        for (int i = 0; i < machine.output.getSlots(); i++) {
            if (!machine.output.getStackInSlot(i)
                .isEmpty()) stacks++;
        }
        return stacks;
    }

    private static void tick(VendingMachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            machine.tick();
        }
    }

    @GameTest(template = "empty")
    public static void oneUserAtATime(GameTestHelper h) {
        placeMachine(h);
        ServerPlayer first = new Listener(h);
        Listener second = new Listener(h);
        use(h, first);
        h.assertTrue(usesMachine(first), "the first player should open the machine");
        use(h, second);
        h.assertFalse(usesMachine(second), "a second player should be refused while the first uses it");
        ClientMessage busy = new ClientMessage(Component.translatable("vendingmachine.gui.error.player_using"), false);
        h.assertTrue(second.messages.equals(List.of(busy)),
            "the refused player should get the busy message in chat, got " + second.messages);
        first.closeContainer();
        use(h, second);
        h.assertTrue(usesMachine(second), "closing the screen should free the machine");
        second.setHealth(0);
        use(h, first);
        h.assertTrue(usesMachine(first), "the user dying should free the machine");
        first.discard();
        ServerPlayer third = new Listener(h);
        use(h, third);
        h.assertTrue(usesMachine(third), "the user leaving should free the machine");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sneakUseWithCoinsPaysThemIn(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        ServerPlayer player = new Listener(h);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(coin("cog"), 3));
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player,
            InteractionHand.MAIN_HAND, h.absolutePos(MACHINE), hit(h));
        VendingMachineBlock.allowSneakDeposit(event);
        h.assertTrue(event.getUseBlock() == TriState.TRUE, "sneak-use with coins should reach the machine");
        BlockState state = h.getBlockState(MACHINE);
        state.useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h));
        h.assertTrue(player.getMainHandItem()
            .isEmpty(), "the whole held stack should be paid in");
        h.assertTrue(machine.wallets()
            .count(player.getUUID(), coin("cog")) == 3, "the wallet should hold the three cogs");
        h.assertFalse(usesMachine(player), "the screen should not open");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        ItemInteractionResult result = state
            .useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h));
        h.assertTrue(result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "other items act as before");
        h.assertTrue(player.getMainHandItem()
            .getCount() == 2, "other items should not be taken");
        h.succeed();
    }

    /** Sneak-uses the machine with {@code count} spurs in hand. */
    private static void sneakUseWithSpurs(GameTestHelper h, ServerPlayer player, int count) {
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(coin("spur"), count));
        h.getBlockState(MACHINE)
            .useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h));
    }

    /**
     * As in the original, an unformed machine's screen opens but lists no trades, and the machine takes no coins,
     * sells nothing and ejects nothing.
     */
    @GameTest(template = "empty")
    public static void unformedMachineListsNothingAndTakesNoCoins(GameTestHelper h) {
        VendingMachineBlockEntity machine = Machines.controller(h, MACHINE, Direction.NORTH);
        Listener player = new Listener(h);
        machine.wallets()
            .add(player.getUUID(), List.of(new ItemStack(coin("spur"), 5)));
        use(h, player);
        h.assertTrue(player.containerMenu instanceof VendingMachineMenu open && !open.formed(),
            "the screen should open showing the incomplete structure, not the trades");
        VendingMachineMenu menu = new VendingMachineMenu(1, player.getInventory(), machine, List.of(BREAD));
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "no trade while unformed");
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_EJECT_COINS), "no eject while unformed");
        h.assertFalse(menu.clickMenuButton(player, VendingMachineMenu.ejectCoinButton(0)), "no coin eject either");
        menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .set(new ItemStack(coin("spur"), 2));
        h.assertTrue(menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .getItem()
            .getCount() == 2, "coins should stay in the IN slot");
        sneakUseWithSpurs(h, player, 3);
        h.assertTrue(player.getMainHandItem()
            .getCount() == 3, "sneak-use should leave the coins in hand");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 5 && machine.queued() == 0,
            "the wallet should be untouched and nothing queued");
        h.succeed();
    }

    /** The formed machine takes coins by sneak-use and the IN slots, and sells. */
    @GameTest(template = "empty")
    public static void formedMachineTakesCoinsAndSells(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Listener player = new Listener(h);
        VendingMachineMenu menu = new VendingMachineMenu(1, player.getInventory(), machine, List.of(BREAD));
        h.assertTrue(menu.formed(), "the machine should be formed");
        sneakUseWithSpurs(h, player, 3);
        h.assertTrue(player.getMainHandItem()
            .isEmpty(), "sneak-use should pay in");
        menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .set(new ItemStack(coin("spur"), 1));
        h.assertTrue(menu.getSlot(VendingMachineMenu.COIN_SLOT)
            .getItem()
            .isEmpty(), "the IN slot should pay in");
        h.assertTrue(menu.clickMenuButton(player, VendingMachineMenu.BUTTON_FIRST_TRADE), "should sell");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 3 && machine.queued() == 1,
            "three and one paid in, less one for the bread");
        h.succeed();
    }

    /** Each line's English text, from the lang file. */
    private static List<String> english(List<Component> lines) {
        JsonObject en = englishLang();
        return lines.stream()
            .map(line -> en.get(((TranslatableContents) line.getContents()).getKey())
                .getAsString())
            .toList();
    }

    private static JsonObject englishLang() {
        String path = "/assets/" + VendingMachine.MODID + "/lang/en_us.json";
        try (InputStream in = MachineBehaviourGameTests.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the English lang file", e);
        }
    }

    /** The original's item tooltip: machine type, lore and the Shift hint; with Shift, the structure. */
    @GameTest(template = "empty")
    public static void itemTooltipHasTheOriginalLines(GameTestHelper h) {
        List<String> lines = english(VendingMachineBlock.tooltip(false));
        h.assertTrue(lines.equals(List.of(
            "Machine Type: \u00a7eVending Machine",
            "Who even restocks this...",
            " ",
            "Hold \u00a7l[LSHIFT]\u00a7r\u00a77 to display structure guidelines")), "tooltip lines, got " + lines);
        List<String> structure = english(VendingMachineBlock.tooltip(true));
        h.assertTrue(structure.contains("   \u00a7fController: \u00a77Middle right, 2nd layer")
            && structure.contains("Cannot be flipped onto its side"), "structure lines, got " + structure);
        h.assertTrue(VendingMachineBlock.tooltip(false)
            .stream()
            .allMatch(line -> line.getStyle()
                .getColor()
                .equals(TextColor.fromLegacyFormat(ChatFormatting.GRAY))),
            "lines are gray, as lore");
        h.succeed();
    }

    /** The original's WAILA line, which Jade shows: red while casings are missing, green "Running Fine" once formed. */
    @GameTest(template = "empty")
    public static void structureStatusFollowsTheCasings(GameTestHelper h) {
        Machines.controller(h, MACHINE, Direction.NORTH);
        assertStatus(h, h.getBlockState(MACHINE), "** INCOMPLETE STRUCTURE **", ChatFormatting.RED);
        Machines.build(h, MACHINE);
        assertStatus(h, h.getBlockState(MACHINE), "Running Fine", ChatFormatting.GREEN);
        h.succeed();
    }

    private static void assertStatus(GameTestHelper h, BlockState state, String text, ChatFormatting color) {
        Component status = VendingMachineBlock.structureStatus(state);
        List<String> line = english(List.of(status));
        h.assertTrue(line.equals(List.of(text)), "status should read " + text + ", got " + line);
        h.assertTrue(status.getStyle()
            .getColor()
            .equals(TextColor.fromLegacyFormat(color)), "status should be " + color);
    }

    @GameTest(template = "empty")
    public static void dropDelayShortensAsTheQueueGrows(GameTestHelper h) {
        h.assertTrue(VendingMachineBlockEntity.dropDelay(1) == 10, "one stack waits 10 ticks");
        h.assertTrue(VendingMachineBlockEntity.dropDelay(2) == 10, "ln 2 is below 1, so 10 ticks");
        h.assertTrue(VendingMachineBlockEntity.dropDelay(3) == 9, "10 / ln 3 is 9 ticks");
        h.assertTrue(VendingMachineBlockEntity.dropDelay(100) == 2, "10 / ln 100 is 2 ticks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void purchasesDropIntoTheChuteOneStackAtATime(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        ServerPlayer player = new Listener(h);
        buyBread(player, machine, 3);
        h.assertTrue(machine.queued() == 3 && stacksInChute(machine) == 0, "purchases should wait in the queue");
        tick(machine, 1);
        h.assertTrue(stacksInChute(machine) == 1, "the first stack should drop at once");
        tick(machine, 1);
        h.assertTrue(stacksInChute(machine) == 2, "the next stack should follow on the next tick");
        tick(machine, 9);
        h.assertTrue(stacksInChute(machine) == 2, "the last stack should wait out the 10-tick delay");
        tick(machine, 1);
        h.assertTrue(stacksInChute(machine) == 3 && machine.queued() == 0, "the last stack should drop after 10 ticks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chuteHoldsOneHundredStacks(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        ServerPlayer player = new Listener(h);
        h.assertTrue(machine.output.getSlots() == 100, "the chute should have 100 slots");
        for (int i = 0; i < 99; i++) {
            machine.output.setStackInSlot(i, new ItemStack(Items.STONE));
        }
        buyBread(player, machine, 2);
        h.assertTrue(machine.queued() == 1, "only the stack that fits the last slot should be sold");
        h.assertTrue(machine.wallets()
            .total(player.getUUID()) == 1, "the refused purchase should cost nothing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void queueSurvivesSaveAndLoad(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        buyBread(new Listener(h), machine, 2);
        CompoundTag saved = machine.saveWithFullMetadata(h.getLevel()
            .registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(), saved, h.getLevel()
            .registryAccess());
        h.assertTrue(loaded instanceof VendingMachineBlockEntity copy && copy.queued() == 2, "the queue should load");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingDropsChuteAndQueue(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        buyBread(new Listener(h), machine, 3);
        tick(machine, 1);
        h.destroyBlock(MACHINE);
        h.assertItemEntityCountIs(Items.BREAD, MACHINE, 2.0, 9);
        h.succeed();
    }

    /**
     * A server player that records the sounds played to it alone and opens menus without a client. It stays out of
     * the player list, so other mods do not send it network payloads the test server cannot deliver.
     */
    private static final class Listener extends FakePlayer {

        final List<SoundEvent> heard = new ArrayList<>();
        final List<ClientMessage> messages = new ArrayList<>();

        Listener(GameTestHelper h) {
            super(h.getLevel(), new GameProfile(UUID.randomUUID(), "user"));
            setPos(Vec3.atCenterOf(h.absolutePos(MACHINE.north())));
        }

        @Override
        public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
            containerMenu = provider.createMenu(1, getInventory(), this);
            return OptionalInt.of(1);
        }

        @Override
        public void playNotifySound(SoundEvent sound, SoundSource source, float volume, float pitch) {
            heard.add(sound);
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            messages.add(new ClientMessage(message, overlay));
        }
    }

    private record ClientMessage(Component text, boolean overlay) {}

    @GameTest(template = "empty")
    public static void otherPlayersDepositSoundsGoToTheMachine(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Listener user = new Listener(h);
        user.containerMenu = new VendingMachineMenu(1, user.getInventory(), machine, List.of(BREAD));
        machine.startUsing(user);
        Listener depositor = new Listener(h);
        List<Vec3> atMachine = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = sound -> atMachine.add(sound.getPosition());
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            machine.deposit(depositor, new ItemStack(coin("spur"), 1));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.assertTrue(user.heard.equals(List.of(Registration.COIN_INSERT.get())),
            "the user should hear another player's insert at their position, got " + user.heard);
        h.assertTrue(
            depositor.heard.isEmpty(),
            "the depositor should not hear their own insert, got " + depositor.heard);
        Vec3 centre = Vec3.atCenterOf(machine.getBlockPos());
        h.assertTrue(atMachine.size() == 1 && centre.equals(atMachine.get(0)),
            "everyone else should hear the insert at the machine, got " + atMachine);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void userHearsSoundsWhereTheyStand(GameTestHelper h) {
        VendingMachineBlockEntity machine = placeMachine(h);
        Listener user = new Listener(h);
        user.containerMenu = new VendingMachineMenu(1, user.getInventory(), machine, List.of(BREAD));
        machine.startUsing(user);
        List<Vec3> atMachine = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = sound -> atMachine.add(sound.getPosition());
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            buyBread(user, machine, 1);
            tick(machine, 1);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.assertTrue(
            user.heard.equals(List.of(Registration.COIN_INSERT.get(), Registration.ITEM_DROP.get())),
            "the user should hear the insert and the drop at their position, got " + user.heard);
        Vec3 centre = Vec3.atCenterOf(machine.getBlockPos());
        h.assertTrue(
            atMachine.size() == 2 && atMachine.stream()
                .allMatch(centre::equals),
            "everyone else should hear both at the machine, got " + atMachine);
        h.succeed();
    }
}
