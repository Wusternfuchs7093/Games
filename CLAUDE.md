# Project memory — Minecraft "Cities" mod

This file is the hand-off between sessions. **Read it first, update it at the end of every request**
(what was done, what is pending, anything the user decided). Keep it short and factual.

## The user
- Speaks Russian; answer in Russian. Code, comments, commit messages in English.
- Goal: a NeoForge mod where some villages grow into towns, cities and capitals. The user says "делай
  следующий этап" and expects the next stage built, committed and pushed. They give the decisions to me
  ("остальное корректируй сам").
- The user has **not run the mod in game yet**. Every stage is verified only by compiling in CI. Expect
  in-game bugs; when the user reports one, fix it first, before new features.

## Repo, branch, build
- Repo `Wusternfuchs7093/Games`, branch `claude/minecraft-cities-mod-mechanics-sdf1qi`. Commit and push
  every stage there. No PR unless asked.
- Mod lives in `minecraft-cities-mod/`. Design doc with the full mechanics list: `minecraft-cities-mod/MECHANICS.md`.
  Player docs (in Russian, one section per stage + command table): `minecraft-cities-mod/README.md`.
- NeoForge **21.1.172**, Minecraft **1.21.1**, Java 21, ModDevGradle 2.0.148, mod id `cities`,
  package `com.wusternfuchs.cities`. Gradle wrapper 8.14.3.
- **The cloud sandbox cannot build**: maven.neoforged.net and Mojang hosts are blocked by its network policy.
  Verify with GitHub Actions (`.github/workflows/build.yml`, runs `./gradlew build` on every push).
  Check a run with `curl https://api.github.com/repos/Wusternfuchs7093/Games/actions/runs?head_sha=<sha>`
  (no auth needed); failed job logs via the GitHub MCP tool `get_job_logs`.
- Because nothing can be compiled locally, stick to APIs you are sure of for 1.21.1 (e.g. `ResourceLocation.parse`,
  `SavedData.Factory`, `MobEffects.MOVEMENT_SLOWDOWN`, `EntityType.Builder.build(String)`).

## Architecture (src/main/java/com/wusternfuchs/cities)
- `CitiesMod` (entry, registers config + entities + `GameEvents`), `CitiesConfig` (server config, sections:
  simulation, growth, construction, defense, trade, government, disasters, people, display), `GameEvents`
  (all game-bus listeners).
- `settlement/` — core. `Settlement` (all persistent state of one town, NBT save/load), `SettlementData`
  (SavedData per dimension: settlements, chunk→owner index, caravans, wars), `SettlementManager` (tick loop,
  village detection from bells, **census** = the per-minute simulation step, economy, tiers, enter titles),
  `SettlementTier` (7 tiers), `Requirement`, `Mood` (named happiness factors), `Stock` (food/materials/wealth),
  `ArchitectureStyle` (= culture: palette, names, bonuses, affinities), `HistoryEntry` (chronicle).
- Census order in `SettlementManager.simulate`: census (+residents, quests) → governance → collect warehouse
  storage → disasters → economy → happiness → migration → garrison → vassal count/wars → tier → research →
  town planner → diplomacy drift → trade → siege roll.
- `building/` — `Blueprint` + `Blueprints` (procedural buildings drawn on a `Canvas`; plot = body + 1-block ring
  for eaves/shutters/towers, body walls at 1..w-2, storeys 4 high; `shell()` = walls/windows/floors/ladder/door/roof;
  roofs gable/steep/flat/hip/pagoda/stepped; `crown()` per culture on town halls; `temple()` per culture),
  `Palette` (materials + roof shape + flags per culture × `Period`), `Period` (EARLY/MEDIEVAL/RENAISSANCE/
  INDUSTRIAL/MODERN from `Era`), `BuildingType` (18 types incl. TEMPLE, CRAFTHOUSE, procedural WALL and HIGHWAY;
  plot size depends only on type+culture+variant, never on era), `Building` (placed record with `era` and `stone`;
  `rebuilt(era)`), `TownPlanner` (what & where; site search sees through trees via `groundLevel`, slope ≤ 6;
  falls back to reconstruction/highways when no site; `startReconstruction` rebuilds outdated buildings),
  `ConstructionManager` (terraforming: clear site + 2-block levelled margin, batched 6 per step; then foundation,
  structure, details, road; queue is recomputed from the world on load and never tears down finished parts),
  `Highways`.
- `defense/` guards hiring, raids (`Siege`). `entity/Guard` (guard / soldier / mercenary in one class),
  `client/` (renderer, client setup). `trade/` caravans, prices, villager price by reputation.
  `diplomacy/` relations. `governance/` mayor, elections, laws, taxes, festivals. `research/` eras + techs.
  `war/` wars, battles, vassals. `disaster/` fire, epidemic, drought, ruins. `people/` names, traits, classes,
  ageing. `quest/` job board, villager wishes, houses for sale, tavern rumours, mercenaries.
  `command/CityCommands` — every `/city ...` command.
- `network/` — `RequestCityPayload` (client→server), `CityViewPayload` (server→client, one page of Components per
  tab), `CityView` (fills each tab by running the matching `/city <tab>` command with a capturing CommandSource),
  `CitiesNetwork` (payload registration). `client/CityScreen` (tabs + action buttons), `ClientEvents` (key K),
  `ClientHooks` (client-only entry point from the network handler). `settlement/BorderDisplay` (border particles).
- `block/` — `CitiesBlocks` (scholar_desk, builder_bench, craft_bench, job_board + items, creative tab),
  `JobBoardBlock` (right-click opens the board). `profession/CitiesProfessions` — POI types + professions
  scholar/builder/artisan (tag `minecraft:acquirable_job_site`), trades via `VillagerTradesEvent` (artisan trades
  depend on the culture of the town the villager stands in). Textures generated by a Python script (job board,
  desks, profession overlays in `textures/entity/{villager,zombie_villager}/profession`).
- Cultures (`ArchitectureStyle`, enum names = biome families): PLAINS German, DESERT Arabic, TAIGA Russian,
  SNOWY Norse, SAVANNA Sahel, OTHER British, CHERRY Japanese, JUNGLE Maya; each has a `Craft`.
- `network/BoardPayload` + `RequestBoardPayload` + `client/JobBoardScreen` — job board screen (key J, block,
  shift-click bell). Key G = `/city donate`, Shift+G = `/city donate all`.
- `data/` — datapack reload listeners: `CustomBlueprints` (`data/<ns>/cities/buildings/*.json` + structure `.nbt`
  → extra variants of a building type, picked via `BuildingType.variantCount/blueprint`), `CultureNames`
  (`data/<ns>/cities/names/<culture>.json`). Registered in `GameEvents.onAddReloadListeners`.
  Example datapack: `minecraft-cities-mod/examples/cities-example-datapack`.
- `api/` — `CitiesApi` (queries) and `SettlementEvent` (Founded, TierChanged, BuildingCompleted, SiegeStarted,
  WarDeclared) posted on `NeoForge.EVENT_BUS`. Keep these stable: other mods may depend on them.
- A new screen tab = add the id to `CityViewPayload.TABS` (must be a `/city <id>` command or special-cased in
  `CityView`) and `cities.gui.tab.<id>` in both lang files.
- Translations: `assets/cities/lang/en_us.json` and `ru_ru.json` — add every new key to **both**.
- Guard texture `assets/cities/textures/entity/guard.png` was generated by a small Python script (64×64 player
  skin layout).

## Conventions
- Each stage: code → translations (en+ru) → README section + command rows → one commit → push → wait for CI →
  report to the user in Russian (what was added, how to test, what is unverified).
- Persist new state in `Settlement.save/load` with backward-compatible defaults.
- New buildings: add to `BuildingType`, a generator in `Blueprints`, planner rule in `TownPlanner.choose`,
  `cities.building.<id>` in both lang files.

## Status
Done stages (all compile in CI, none tested in game):
1 core settlements · 2 construction · 3 guards/walls/raids · 4 trade/diplomacy · 5 government ·
6 research/eras/stone · 7 wars/vassals · 8 disasters/mood · 9 residents · 10 job board/houses/tavern/mercenaries ·
11 cultures/highways/ruins/old towns · 12 city screen (K, /city gui), /city borders, /city chronicle ·
13 datapack buildings/names, multiplayer options (protectBuildings, maxTownsPerPlayer, allowPlayerWars),
API events, /city admin stats. **All 13 planned stages are done.**
14 (CI green; user's request after reading the mod): cultures as countries with distinct architecture per era (German,
Arab, Rus, Norse, Sahel, British, Japanese, Maya), eras INDUSTRIAL + MODERN with 6 techs, reconstruction of
outdated buildings, professions scholar/builder/artisan with workstations, clergy class, temples, culture crafts
+ craft houses, terraforming builders, territory covers the whole found village (`coreRadius`), job board screen
+ block, hotkeys J/G, `/city admin era <era>` for testing. Design calls made by me: scholars = science (3 pts), librarians 1 pt, clerics = healing +
happiness; CITY tier now also needs a TEMPLE.

Extras:
- `minecraft-cities-mod/MODCARD.md` — mod description for Modrinth/CurseForge (Russian). A styled mod card page was also
  published as a private claude.ai artifact: https://claude.ai/artifact/7bUvKt9SW3AREJJ2d1cT7m (source was in the session
  scratchpad, not in the repo; to change it, read the artifact and republish to that URL).
- The user plays with **TLauncher**. Install steps (NeoForge 1.21.1 version → jar from the Actions artifact
  `cities-mod-jar` → `%appdata%\.minecraft\mods`) are in README "Установка через TLauncher". There is no GitHub
  release yet; the jar comes from CI artifacts (need a GitHub login, expire after 90 days). The user confused the
  "Run workflow" button with the download: point them to a finished run (green check) → Artifacts → `cities-mod-jar`.
  Get the latest run URL with `curl https://api.github.com/repos/Wusternfuchs7093/Games/actions/runs?per_page=1`.

- README has a short player guide "Как играть" (any vanilla village works; it only develops while its chunks are
  loaded). The user asked where to develop a village — they are about to start playing.

- UX gap noticed by the user: delivering a job is command-only (`/city board deliver <#>`, `take <#>` for hunts);
  offered to add per-job "Сдать" buttons on the screen's board tab — waiting for their answer.

- User asked how to have their own house as part of a village. Today: `/city buy` (town-built house/manor only),
  mayor `/city order house` then buy, or build it yourself (beds/workstations count, planner avoids it, but it is
  not registered/protected). Offered `/city claim` to register a self-built house as owned+protected — waiting.

- User asked how to grow a village: answered with /city info requirements, beds+food+happiness>=60 for immigrants,
  donations, materials, potential limit, and test speedups (setlevel, census, populationScale, freeConstruction,
  buildIntervalTicks, ignorePotential). If they find growth too slow in play, consider easier defaults.

- User asked how to make a warehouse: town builds it itself (Village tier, 32+ materials, after urgent needs),
  mayor `/city order warehouse`, op `/city admin build warehouse`. Player-placed chests do not count — only the
  warehouse blueprint's chests are storage. Possible future feature: let players designate their own chests.

- Explained "wealth" (treasury, 9 per emerald; sources/sinks; tier thresholds 256/1024/4096/16384/65536).
  If the user finds high tiers' wealth requirements too steep in play, lower them in `SettlementTier`.

- Explained research speed (1 + 2/scholar + 4/library + 2 town hall, ×1.5 Printing, +25% borderland). Offered:
  donated books/paper could add research points (today they go to wealth) — waiting for an answer.

- Explained happiness factors (see `settlement/Mood`). The user is learning the systems one by one; a short
  "how each system works" player guide in README could save repeated questions.

- User asked how to add a building to a village: town plans itself; mayor `/city order <type>` (be mayor via `/city run` election or `/city found`); op `/city admin build <type>`; self-built blocks count only as beds/workstations (POI), not as registered buildings. Same `/city claim` offer applies.

Next:
- In-game testing and bug fixing with the user (top priority).
- Ideas not built yet (from MECHANICS.md): JourneyMap/Xaero markers, families/inheritance, guilds, banks/loans,
  mail, sea trade/ports, Nether/End settlements, seasons, schools/universities beyond the library.

## Known risks to check first when the user tests
- Stage 14 is the biggest unverified change: new building generator (roof/gable fill, domes, shutters, chimneys),
  terraforming (could cut too much or leave holes), custom POI/professions (NeoForge must map POI block states;
  if villagers never take the new jobs, check that first), profession textures, job board screen layout.
- Building rotation (doors, beds, stairs) and builder pathing/teleporting.
- Balance: growth speed, raid/fire/epidemic frequency (all in the server config).
- Fires can spread to player builds until put out.
