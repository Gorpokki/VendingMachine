## Introduction

A NeoForge 1.21.1 port of the GTNH Vending Machine. A player places the machine, inserts coins, and buys trades from a catalogue that lives in data files.

The original 1.7.10 mod is on the `master` branch. This branch keeps its trade model (currency in, items out), its per-player wallet, and its screen layout. The GregTech multiblock, ModularUI2, NEI and BetterQuesting parts do not exist on 1.21.1, so the block, screen and quest parts are rewritten. Quest unlocks are not ported.

## Trades

Each JSON file in `data/<namespace>/vendingmachine/trades/` is a price table: an array of trades. A data pack reload replaces the whole catalogue, and screens open on the old catalogue close.

```json
[
  { "item": { "id": "minecraft:bread", "count": 3 }, "price": 64, "sell_price": 8, "category": "food" },
  { "item": { "id": "minecraft:torch", "count": 16 }, "price": 32, "category": "tools" }
]
```

- `item`: the item stack the buyer gets (required).
- `price`: cost in currency units (required, more than 0).
- `sell_price`: what a selling block may pay for the same stack (optional, more than 0). The machine itself does not buy items.
- `category`: the category the screen filters by (required). The screen shows the `vendingmachine.category.<category>` translation, or the raw name when there is none.

Every trade is open to every player from the start, and can be bought again with no wait and no limit. The screen filters by category and by search text on the item name.

A purchase is all or nothing. If payment is unavailable, or there is no room for the whole stack in the machine's output slots, nothing is taken.

Every price and sell price must be a multiple of the smallest coin's value. A trade that breaks a rule is logged and skipped; the rest of its file still loads.

## Currency

Inserted coins and any overpayment from ME storage become player credit. **Return Coins** gives credit back as coins, and breaking the machine drops all credit as coins.

`config/vendingmachine-common.toml` sets the accepted coins:

```toml
currency = ["minecraft:emerald=1", "minecraft:emerald_block=9"]
```

With an empty list (the default) and Create: Numismatics installed, the machine takes Numismatics coins at their Numismatics values, counted in spurs.

Every coin's value must be a multiple of the smallest coin's value, and each item may appear only once. Otherwise the machine accepts no currency and logs an error.

The currency is read once when the game or dedicated server launches; restart it after changing `currency`. Changing `currency` on a world that already holds stored credit can strand remainders the new coins cannot pay out; each one is logged when the machine pays out or breaks.

## Cards

The card slot takes a Numismatics bank card or authorized card whatever the currency. A card pays only while the currency is the Numismatics default. A bound card pays from its bank account when the player may use that account; an authorized card pays from the shared account it was made for when the player is trusted on it. The card pays first, as at a Numismatics Vendor. Credit from inserted coins pays only when there is no card or the card cannot pay. A refused purchase takes nothing from either. The card goes back to the player when the screen closes.

## ME storage

With Applied Energistics 2 installed, the machine joins an adjacent AE2 network like any network block. When there is no card, or the card cannot pay, and the player's credit is too low, the machine takes coin items worth the missing value from the network's ME storage. It checks with a simulated extract first and takes nothing unless the coins cover the whole shortfall. Coins are taken largest first; when they cannot match the shortfall exactly, one more coin covers it and the overpaid part stays as the player's credit. The network must be powered. Only the player who placed the machine may pay from its ME storage; other players pay with coins or a card.

## Building

`./gradlew build` makes the jar. `./gradlew runGameTestServer` runs the GameTests with Create, Create: Numismatics and Applied Energistics 2 loaded.
