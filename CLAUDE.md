# CLAUDE.md

NeoForge 1.21.1 mod (`create_sa_drillfix`) that fixes the Digging / Chunk Eater
enchantments from Create: Stuff 'N Additions not applying Silk Touch / Fortune / XP
to the extra blocks they break. Mechanism: record a mining context on
`BlockEvent.BreakEvent`, then rewrite nearby tool-less `BlockDropsEvent` drops using
the player's actual tool. No mixins, server-side only.

## Build & test

- Requires JDK 21. Build: `./gradlew build` → jar at `build/libs/create_sa_drillfix-<version>.jar`.
- Dev smoke test (mod loads, but Create S&A is NOT present): `./gradlew runClient`.
- Automated end-to-end test: `./gradlew runGameTestServer` runs the gametest in
  `AoeDropsGameTests` (simulates the drill's AOE break, asserts silk-touched drops).
  CI (`.github/workflows/build.yml`) runs build + gametest on every push and greps for
  the "required tests passed" marker because the gradle task can exit 0 even when the
  server fails to launch.
- Real-world testing must happen OUTSIDE the dev environment: build the jar, then put
  it in a normal launcher instance (Prism/CurseForge, MC 1.21.1 + NeoForge 21.1.x)
  together with Create: Stuff 'N Additions + Create. In-game: Portable Drill with
  Digging + Silk Touch mining a 3x3 of stone must drop 9 stone (not cobblestone).

## Claude Code remote sandbox constraints

- The sandbox network policy blocks maven.neoforged.net, piston-meta.mojang.com,
  Modrinth/CurseForge — local Gradle builds FAIL at dependency resolution. Test by
  pushing and watching GitHub Actions instead (this works; iterate on CI logs).
- raw.githubusercontent.com works via WebFetch — verify NeoForge APIs against the
  `1.21.1` branch of neoforged/NeoForge (NOT `1.21.x`, which tracks a newer MC).

## Gotchas learned the hard way

- Gametest discovery: tests are namespace-filtered via
  `GameTestHooks.getTemplateNamespace`. Use
  `@GameTest(template = "empty", templateNamespace = MOD_ID)` +
  `@PrefixGameTestTemplate(false)`; a namespaced `template` string silently yields
  ZERO tests ("No test functions were given!") while the gradle task still exits 0.
- The gametest runner instantiates the test class via a PUBLIC no-arg constructor
  even for static methods — don't add a private constructor to `AoeDropsGameTests`.
- `ClientInformation` lives in `net.minecraft.server.level`, not `server.network`.
- `@EventBusSubscriber`'s `bus = ...` parameter is deprecated in NeoForge 21.1 —
  omit it; the bus is auto-detected.

## Known unknowns

- The default trigger enchantment ids `create_sa:digging` / `create_sa:chunk_eater`
  are inferred (Create S&A is closed-source MCreator; its jar could not be downloaded
  from the sandbox). If wrong, the real ids are visible in the Create S&A jar under
  `data/<namespace>/enchantment/` and users fix it in
  `config/create_sa_drillfix-common.toml` (`triggerEnchantments`) — no code change.
- Code review flagged (not yet applied): matching heuristic can false-positive on
  unrelated tool-less drops near a mining player (Create contraptions, leaf decay);
  consider `isCorrectToolForDrops` check / smaller default `maxBlockDistance`, event
  priority for the `drops.clear()` compat hazard, and cancellation handling for
  claim-protection mods.
