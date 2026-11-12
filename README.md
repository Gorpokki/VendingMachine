# Vending Machine

A vending machine and a shipping bin for NeoForge 1.21.1. Players buy items
with coins, items or both from a catalogue in data files, and sell items to
the bin for coins.

## Build and test

- `./gradlew build` builds the jar.
- `./gradlew runGameTestServer` runs the GameTests with Create,
  Create: Numismatics and Applied Energistics 2 loaded.

## Vending machine

As in the original, the machine is 2 blocks wide, 3 tall and 1 deep. The
Vending Machine block is the middle right of its front, and five Vending
Machine Casings fill the rest. Hold Shift over the machine item to see this
layout in its tooltip. The machine checks its casings once a second. Until all
five are in place it stays dark, and its screen shows "Incomplete Structure."
with no trades; it takes no coins, sells nothing and ejects nothing. Once
formed, the front lights up. A casing that
is already part of another formed machine does not count. Pistons cannot move
casings.

With Jade installed, looking at the machine shows its status as in the
original: "Running Fine" once formed, else "\*\* INCOMPLETE STRUCTURE \*\*".
The mod runs without Jade.

Place the machine and open it. Shift-click a trade to buy it; ctrl-click marks
it as a favourite. The screen filters by category tab and by search text on the
names of the items a trade shows or gives. It marks each trade you can pay for,
by the card alone or by the wallet and ME storage together, when you also hold
the items it asks for. Favourites sort first and have their own tab. Each
single-player save and each server keeps its own favourites; favourites from
an earlier version move to the first world opened.

One player at a time uses the machine. While that player has the screen open,
anyone else who tries sees a chat message that the machine is busy.

A purchase is all or nothing. When you cannot pay, lack the items a trade asks
for, or the 100 chute slots have no room for every bought stack beside the
stacks already in the chute or queued for it, nothing changes. Bought stacks
wait in a queue, saved with the machine, and drop into the output chute one
stack at a time, faster when many are waiting. **Eject Items** moves the
chute's items into your inventory. Breaking the machine drops everything in its
chute and queue.

The machine pays from these sources, first to last:

- Card: a Numismatics bank card or authorized card in the card slot, when
  prices are in Numismatics spurs and you may use the card's account.
- Wallet: the coins in your wallet.
- ME storage: coins in the ME storage of an adjacent, powered Applied
  Energistics 2 network, when you placed the machine. The machine takes the
  missing value, largest coins first. One extra coin covers a value that the
  coins cannot match, and the surplus stays in your wallet as change.

### Wallet

Each player has one wallet, kept with the world and shared by every machine.
Coins put into the IN slots go into your wallet. Sneak-use the machine with
coins in hand to pay in the whole held stack without opening the screen. A
purchase takes coins from the wallet, largest first, and puts any change back.
**Eject All Coins from Wallet** drops every coin into the chute through the
same queue as purchases; shift-click a coin in the coin row to eject only that
type. Wallets stay when a machine breaks.

### Card slot

The card slot takes a Numismatics bank card or authorized card whatever the
currency, and the card pays first, as at a Numismatics Vendor. A bound card
pays from its bank account; an authorized card pays from the shared account it
was made for when you are trusted on it. A refused purchase takes nothing from
card or wallet. The card slot sits in the coin row: while the row is hidden it
takes no card, but a card already in it stays and keeps paying. The card goes
back to you when the screen closes.

### Options

The screen draws its panels, tiles and buttons as plain shapes. A resource
pack can give it textures at `assets/vendingmachine/textures/gui/`, under the
names that the client's `Art` class lists. Each texture it supplies replaces
its shape.

`config/vendingmachine-client.toml` keeps each player's display mode, sort
order, coin row choice and favourites.

## Shipping bin

The bin buys the item of every one-stack trade with a `sell_price` in the
catalogue, in whole lots of the trade's stack size, and puts the coins in the
bin. It sells whenever its contents change. Coins, items with no sell price and
leftovers smaller than one lot stay. When the coins do not fit, nothing sells.

## Catalogue

Each JSON file in `data/<namespace>/vendingmachine/trades/` holds an array of
trades. A data pack reload replaces the catalogue and closes open screens.

```json
[
  {
    "item": { "id": "minecraft:bread", "count": 3 },
    "price": 64,
    "sell_price": 8,
    "category": "food"
  }
]
```

- `item`: the stack the buyer gets.
- `items`: in place of `item`, a list of stacks the buyer gets together.
- `display_item`: optional; the stack the store tile and list row show in
  place of the first stack. The tooltip still lists every stack.
- `price`: the cost in currency units; optional when `from_items` is set.
- `sell_price`: optional; what the shipping bin pays for the same stack.
- `category`: the category tab the trade shows under. The tab shows the
  `vendingmachine.category.<category>` translation when one exists.
- `from_items`: optional; items the purchase takes from the buyer's
  inventory, as `{ "id": "<item>", "count": <n>, "tag": "<item tag>" }`.
  `count` defaults to 1. With `tag`, items in that tag also pay once the
  exact item runs out. Item data is ignored, and damaged items never count.
- `non_consumed_items`: optional; items in the same form that the buyer must
  hold but keeps. They count on top of `from_items`, so one stack cannot
  both pay and stay.

An entry can also be a trade group:
`{ "category": "<category>", "trades": [ ... ] }`. Its trades take the
group's category. Hovering one trade of a group highlights every trade of
the group, and the smart sort orders trades with the same shown item by their
place in their group.

A trade needs a `price` above 0 or at least one `from_items` entry. Every
price and sell price must be a multiple of the smallest coin's value.
The log names each trade that breaks a rule, and the rest of its file still
loads.

Each JSON file in `data/<namespace>/vendingmachine/categories/` sets the order
and icons of the category tabs: an array of
`{ "name": "<category>", "icon": "<icon>" }`, where `<icon>` names a texture at
`assets/vendingmachine/textures/gui/icons/<icon>.png`. Listed categories come
first, in list order; other categories follow in catalogue order with the
`unknown` icon. The log names each broken entry, and the rest still loads.
The mod ships no icon textures: a tab whose icon is missing shows its
category's initial instead.

With JEI or EMI installed, a Vending Machine page lists every trade in
catalogue order: its price as the fewest coins, largest first, then the items
it takes and the items it only requires, four per row, an arrow, and every
stack bought, four per row. A tag cost cycles through the tag's items. Items
that are not consumed say **Requires (Not Consumed):** in their tooltip. The
page updates when you join a world or server and after `/reload`. Neither
viewer is required.

## Currency

`config/vendingmachine-common.toml` lists the accepted coins and their values:

```toml
currency = ["minecraft:emerald=1", "minecraft:emerald_block=9"]
```

With an empty list (the default) and Create: Numismatics installed, the
machine takes Numismatics coins at their own values in spurs. Each coin must be
listed once, and each value must be a multiple of the smallest. Otherwise the
machine accepts no coins and logs an error. Restart the game or server after a
change.

Coins of an item that is no longer currency stay in the wallet until ejected.
Credit that an older version saved in a machine moves into its players' wallets
as coins when the machine loads; the log names any part the coins cannot make,
and that part stays saved in the machine.

## Commands

As in the original, `/vending` needs permission level 4.

- `/vending add [player] <coin> <amount>` adds coins to a wallet. A negative
  amount removes coins, down to none.
- `/vending set [player] <coin> <amount>` sets how many of a coin a wallet
  holds. A negative amount sets none.
- `/vending reset [player] <coin>` takes every coin of that type out of a
  wallet.
- `/vending reload database` reloads the data packs, as `/reload` does, and
  with them the catalogue.

`<coin>` is a coin's item id, such as `numismatics:cog`, or `all` for every
coin type. Without a player, the command changes your own wallet; a named
player must be online. Removed coins are gone, not dropped.

## Crafting

- Vending machine: 2 stone buttons, 3 iron ingots, 1 vending machine
  casing and 1 hopper.
- Vending machine casing: 4 iron ingots, 4 iron nuggets and 1 copper
  ingot make 1 casing.
- Shipping bin: 2 wooden slabs, 5 planks and 1 chest.

A data pack can replace any of these recipes in `data/vendingmachine/recipe/`.
