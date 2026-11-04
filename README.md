## Introduction

A NeoForge 1.21.1 port of the GTNH Vending Machine. A player places the machine, inserts coins, and buys trades from a catalogue that lives in data files.

The original 1.7.10 mod is on the `master` branch. This branch keeps its trade model (currency in, items out), its per-player wallet, and its screen layout. The GregTech multiblock, ModularUI2, NEI and BetterQuesting parts do not exist on 1.21.1, so the block, screen and quest parts are rewritten. Quest unlocks are not ported.

## Trades

Each JSON file in `data/<namespace>/vendingmachine/trades/` is one trade. A data pack reload replaces the whole catalogue.

```json
{
  "price": 64,
  "results": [{ "id": "minecraft:bread", "count": 3 }]
}
```

- `price`: cost in currency units (required, more than 0).
- `results`: the item stacks the buyer gets (required, at least one).

A purchase is all or nothing. With too little credit, or no room for every result in the machine's output slots, nothing is taken.

Every price must be a multiple of the smallest coin's value. A trade whose price is not is logged and skipped.

## Currency

Inserted coins become credit for the player who inserted them. **Return Coins** gives the credit back as coins, and breaking the machine drops all credit as coins.

`config/vendingmachine-common.toml` sets the accepted coins:

```toml
currency = ["minecraft:emerald=1", "minecraft:emerald_block=9"]
```

With an empty list (the default) and Create: Numismatics installed, the machine takes Numismatics coins at their Numismatics values, counted in spurs.

Every coin's value must be a multiple of the smallest coin's value, or the machine accepts no currency and logs an error.

The currency is read at server start. Changing `currency` on a world that already holds stored credit can strand remainders the new coins cannot pay out; each one is logged when the machine pays out or breaks.

## Building

`./gradlew build` makes the jar. `./gradlew runGameTestServer` runs the GameTests with Create and Create: Numismatics loaded.
