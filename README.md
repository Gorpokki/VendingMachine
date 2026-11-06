# Vending Machine

A vending machine and a shipping bin for NeoForge 1.21.1. Players buy items
with coins from a catalogue in data files, and sell items to the bin for coins.

## Build and test

- `./gradlew build` builds the jar.
- `./gradlew runGameTestServer` runs the GameTests with Create,
  Create: Numismatics and Applied Energistics 2 loaded.

## Vending machine

Place the machine and open it. Put coins in the coin slot: each coin adds its
value to your credit at that machine. Pick a trade and buy it. The item goes to
the machine's output slots.

A purchase is all or nothing. When you cannot pay, or the output slots have no
room for the whole stack, nothing changes. **Return Coins** pays your credit
back as coins. Breaking the machine drops all its credit as coins. Credit that
the accepted coins cannot make stays unpaid, and the server log names it.

The machine pays from these sources, first to last:

- Card: a Numismatics bank card or authorized card in the card slot, when
  prices are in Numismatics spurs and you may use the card's account.
- Credit: your credit at the machine.
- ME storage: coins in the ME storage of an adjacent, powered Applied
  Energistics 2 network, when you placed the machine. The machine takes the
  missing value, largest coins first. One extra coin covers a value that the
  coins cannot match, and the surplus becomes credit.

## Shipping bin

The bin buys every item that has a `sell_price` in the catalogue, in whole
lots of the trade's stack size, and puts the coins in the bin. It sells
whenever its contents change. Coins, items with no sell price and leftovers
smaller than one lot stay. When the coins do not fit, nothing sells.

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
- `price`: the cost in currency units, above 0.
- `sell_price`: optional; what the shipping bin pays for the same stack.
- `category`: the screen's category filter. The screen shows the
  `vendingmachine.category.<category>` translation when one exists.

Every price must be a multiple of the smallest coin's value. The log names
each trade that breaks a rule, and the rest of its file still loads.

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

## Crafting

- Vending machine: 7 copper ingots, 1 glass pane and 1 chest.
- Shipping bin: 2 wooden slabs, 5 planks and 1 chest.

A data pack can replace either recipe in `data/vendingmachine/recipe/`.
