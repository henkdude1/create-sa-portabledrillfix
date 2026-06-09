# Create S&A Portable Drill Fix

A small server-side **NeoForge mod for Minecraft 1.21.1** that fixes the Digging (3x3) and
Chunk Eater (vein miner) enchantments from
[Create: Stuff 'N Additions](https://www.curseforge.com/minecraft/mc-mods/create-stuff-additions)
not applying the tool's other enchantments to the extra blocks they break.

With this mod installed, a Portable Drill with **Digging + Silk Touch** silk-touches the whole
3x3 area, **Digging + Fortune** applies Fortune to the whole area, and ores broken by the AOE
drop the correct amount of XP.

## The bug it fixes

Create: Stuff 'N Additions breaks the extra blocks of its AOE enchantments with
`Level.destroyBlock(...)`, which computes loot **as if no tool was used**. Only the block the
player actually mined gets the tool's enchantments; the other 8 blocks of the 3x3 (or the whole
vein with Chunk Eater) drop their normal loot and no enchantment-adjusted XP.

## How the fix works

The mod is purely event-based — no mixins, no changes to Create: Stuff 'N Additions itself:

1. When a player breaks a block with a tool that has a configured trigger enchantment
   (`create_sa:digging`, `create_sa:chunk_eater` by default), a short-lived *mining context*
   is recorded (`BlockEvent.BreakEvent` fires before the AOE code runs).
2. The AOE enchantment then destroys the nearby blocks; each one fires NeoForge's
   `BlockDropsEvent` with an **empty tool**.
3. For each such tool-less drop close (in space and time) to an active mining context, the mod
   recomputes the drops from the block's loot table using the player's actual tool, and
   recomputes the dropped XP the same way.

The block the player mined directly is untouched — vanilla already applies enchantments there.
Because the fix only keys off the configured enchantments, it works with any of the mod's tools
that can receive them, and it is safe to keep installed without Create: Stuff 'N Additions.

This mod is only needed on the server (or for singleplayer, in the local mods folder).

## Configuration

`config/create_sa_drillfix-common.toml`:

| Option | Default | Meaning |
| --- | --- | --- |
| `triggerEnchantments` | `["create_sa:digging", "create_sa:chunk_eater"]` | Enchantment ids that activate the fix. If the ids ever change in Create: Stuff 'N Additions, look them up in its jar under `data/<namespace>/enchantment/`. |
| `maxBlockDistance` | `12` | Max distance between the mined block and an AOE-broken block for the fix to apply. |
| `contextLifetimeTicks` | `3` | How many ticks after the player mined a block nearby tool-less drops are still fixed. Keep small to avoid affecting unrelated drops. |

## Building

Requires Java 21. Then:

```
./gradlew build
```

The jar ends up in `build/libs/`. To test in a development environment: `./gradlew runClient`.

## Compatibility notes

- Built against NeoForge 21.1.233 / Minecraft 1.21.1; works with any NeoForge 21.1.x.
- The fix is intentionally conservative: it only rewrites drops that were computed with an
  empty tool, in the same moment and area as a break performed with a trigger-enchanted tool.
