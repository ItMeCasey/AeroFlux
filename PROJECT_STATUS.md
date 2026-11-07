# Project Status
<!-- status-format: 1 -->
Updated: 2026-11-06
Summary: All six builds (Forge and NeoForge on 26.1.2, 1.21.1 and 1.20.1) have the full machine, pipe, tool and guidebook set and pass the in-game self-test in CI, and 2026.11.5 went out by tag as six CurseForge betas, while client-side screens and art are still unseen in a real client and Modrinth is on hold.
Spend: none

## Planning
- [x] Gather requirements and decisions (w:2)
- [x] Write PLAN.md (w:2)

## Phase 0-1: Tooling and foundation
- [x] All six loader and version targets build (w:3)
- [x] Multiloader skeleton, shared-core and the Build workflow (unit tests, six builds, six self-tests, translation check; 13 jobs green on main) (w:4)
- [x] Registries, tier config, datagen pipeline (w:3)

## HUDs and GUIs
- [x] Shared widget library (gauge, progress, status, side panel, upgrade slots, redstone button on all six; opened in a real client on all six builds, 2026-11-06, no clipping or overlap; upgrade-slot hints and the tank percent fixed in 2026.11.6) (w:4)
- [x] Machine screens (every machine at gold, the furnace at copper and fluxium, opened in a real client on all six builds with the game's own handlers and read frame by frame; frames in docs/frames/gui-*) (w:4)
- [x] Collector screen with sky panel and tank screen (copper and fluxium, opened in a real client on all six builds and read; the tank now shows <1% below one percent) (w:3)
- [~] In-world look-at overlays (Jade, The One Probe, WTHIT and SMWTI plugins; see Phase 8-9) (w:3)

## Phase 2-4: Fluids, collectors, storage
- [x] Aero Flux and Moon Flux fluids and buckets (w:3)
- [x] Tiered dual-mode collectors (w:4)
- [x] Tiered tanks with visual fill level (w:4)
- [x] Tanks take any fluid (all six, self-tested; a saved fluid from a removed mod loads as an empty tank on 26.1 NeoForge, fix a0b33c2) (w:3)
- [x] Tanks keep their contents when broken (all six, self-tested) (w:1)
- [x] Per-dimension rule map (data-driven aeroflux/dimension_rules on all six) (w:2)
- [x] Conduit bundle (3D pipe layout, shapes, dynamic model, wrench) (w:5)
- [x] Pipe framework, per-face modes, wrench (w:5)
- [~] Fluid and Energy pipes (self-tested; energy not yet tested against a real source block) (w:3)
- [x] Item pipes (w:4)
- [~] Redstone pipes (channel 0 only) (w:3)
- [~] Gas pipes (Mekanism soft dependency on 1.21.1 NeoForge and both 1.20.1 builds, self-tested; Mekanism has no 26.1 or 1.21.1 Forge build, so gas pipes stay uncraftable there) (w:3)
- [x] Pipe tiers, speed and capacity upgrades, distribution modes, per-face Filter and Priority upgrades (all six, self-tested) (w:3)
- [~] Pipe config screen (logic self-tested on all six; opened in a real client on 26.1.2 NeoForge, 26.1.2 Forge and 1.21.1 NeoForge and read: glyph-only layout, fine; not opened on the 1.20.1 builds or Forge 1.21.1 because their tours have no pipe code) (w:4)
- [~] Machine side-config panel (logic self-tested on all six; redesign never seen in a client) (w:3)
- [x] Flux Wrench (w:2)
- [x] Flux Configurator (copy, paste relative to facing, area paste with limit, eight named presets, one-minute undo; logic in shared-core with unit tests, actions self-tested) (w:4)
- [~] Conduit Tool (edit, remove, remove run, copy and paste, inspect, tier upgrade; logic self-tested on all six; some modes verified on 26.1 NeoForge only) (w:4)
- [~] Playtest fixes (26.1 NeoForge checked in game by Casey; the others ported, not played) (w:3)

## Phase 5-7: Machines and resources
- [x] Machine base framework, menus, Speed/Capacity/Efficiency/Fortune/Silk Touch/Purity upgrades (all six, self-tested) (w:5)
- [x] Furnace, Grinder, Sawmill (all six, self-tested) (w:4)
- [x] Alloy Smelter and Infuser (all six, self-tested; the Infuser takes Aero Flux and Moon Flux since 2026.11.3) (w:3)
- [x] Enchanter and Disenchanter (rules and config self-tested on all six) (w:4)
- [x] Plant Grower (all six, self-tested: soil slot, crops on farmland, aeroflux/plant_soils rule files, Mystical Agriculture essence farmland bonus) (w:4)
- [~] Flux Chunk Loader (26.1 and 1.21.1, both loaders: six tiers, kill switch, admin command, screen; none on 1.20.1) (w:3)
- [~] Flux converters (Flux to FE on all six, to AE2 power on 26.1/1.21.1 NeoForge and both 1.20.1 builds, Create stress on 1.21.1 NeoForge only; six tiers) (w:3)
- [x] Auto Import/Export on machine faces and the Plant Grower seed refill (all six, self-tested) (w:2)
- [~] Machine art v2 (3D multi-element models, six-face textures, tiers, animated active fronts on all six; never seen in a client) (w:4)
- [x] Second machine wave (Crafter, Purifier, Moon Spawner, Sky Controller, Blender, Animal Breeder, Repairer, Sifter, Magnet, Sun and Moon Lamps; all six, self-tested) (w:5)
- [~] Machine sounds (wired on all six, registration self-tested, never heard in game) (w:2)
- [x] Resources and full recipe set (machine and block recipes on all six; early-game Infuser; Sawdust to Flux Dust) (w:3)

## Phase 8-9: Advancements and compat
- [~] Advancement tree (15 advancements on all six, never seen in a client) (w:3)
- [x] Recipe import compat layer (aeroflux/recipe_imports rules on all six for Create, Mekanism, Immersive Engineering, Thermal, Ender IO and others) (w:3)
- [~] JEI, REI and EMI plugins (JEI on every build but 26.1 Forge; REI on both NeoForge 26.1/1.21.1 and both 1.20.1; EMI on 1.21.1 NeoForge and both 1.20.1; none seen in a client) (w:3)
- [~] KubeJS and CraftTweaker hooks (KubeJS on both NeoForge 26.1/1.21.1 and both 1.20.1; CraftTweaker on 1.21.1 NeoForge and both 1.20.1) (w:2)
- [~] Fluid and pipe interop with other mods (c:aero_flux style fluid tags; Mekanism tank interop on 1.21.1; self-tested with AeroFlux's own tanks on 26.1) (w:2)
- [~] ME and Dense ME pipes (Applied Energistics 2 soft dependency; grid nodes on NeoForge 26.1/1.21.1 and both 1.20.1 builds, self-tested on 1.20.1) (w:3)
- [~] Jade, The One Probe, WTHIT and SMWTI plugins (SMWTI on 26.1 Forge only, through its API jar; Jade, The One Probe and WTHIT where those mods exist, none seen in a client) (w:3)
- [x] BEI recipe viewer plugin (cards for every recipe machine and the Sifter on all six builds, seen in a client on all six: browser, Copper Infuser card, Copper Alloy Smelter cards; 26.1.2 Forge got a recipe sync of its own first) (w:3)
- [x] SMWTI plugin on all six builds (seen in a client on all six: Copper Furnace and Copper Flux Collector panels read correctly) (w:2)
- [~] Client check of every viewer and overlay integration (BEI and SMWTI on all six, Jade on four builds, frames in docs/frames; JEI, REI, EMI, The One Probe and WTHIT not checked) (w:3)
- [x] Compatibility table (docs/COMPATIBILITY.md: which BEI, REI, JEI, EMI, Jade and SMWTI builds exist per version and loader, what AeroFlux has) (w:1)

## Cross-cutting
- [~] Ownership and claim-mod protection (26.1 and 1.21.1, both loaders; not on 1.20.1; self-tested) (w:3)
- [~] Performance budget and /aeroflux profile (26.1 and 1.21.1, both loaders; not on 1.20.1) (w:3)
- [~] Save-data versioning (26.1 and 1.21.1, both loaders; not on 1.20.1) (w:2)
- [x] Storage sizes in the config for every block, synced to clients and shown in tooltips (all six, self-tested) (w:2)
- [x] Comparator, hopper and dropper support (all six, self-tested) (w:2)
- [x] Enchanter and Disenchanter config rules (all six, self-tested) (w:2)
- [~] Creative tab, creative blocks, sounds, particles (files and creative self-tests on all six; never seen rendered) (w:3)
- [~] In-game guidebook (all six: 23 pages in six chapters, contents page, wiki buttons; opened in a real client on 26.1.2 NeoForge and read, contents spacing fixed in 2026.11.7; the other five builds share the code but were not opened) (w:3)
- [x] Per-loader build metadata templates (all six, checked in the built jars) (w:2)
- [x] Localization pipeline (all text through lang keys and datagen, lang_sync.py, TRANSLATING.md) (w:1)
- [x] Security pass of packets, menus, claims and saved data (converter reach check, capped recipe-sync lists, 2026.11.4) (w:2)

## Feature changes of 2026-11-03
- [x] Early-game Infuser recipes (copper ingot build, Sawdust to Flux Dust on Aero Flux; all six) (w:1)
- [x] Machines named <Tier> <Machine> without Flux (display names only, ids unchanged; wiki pages fixed in the site repo, commit 511e40d) (w:1)
- [~] Simpler Conduit Tool and Configurator art, held upright (all six; never seen in a client; the Wrench keeps its old art) (w:1)

## Phase 10-11: Port, polish, release
- [x] Port to all six builds (self-tested 2026-11-02) (w:5)
- [ ] Art, sounds, balance review in a real client (w:4)
- [x] Unit tests and CI (shared-core JUnit, six builds, six server self-tests, translation check; flaky orb and tank-load tests fixed in e15c4ad and a0b33c2) (w:3)
- [x] Docs and wiki (59 pages live at itmecasey.com/wiki/aeroflux/ with the current machine names) (w:2)
- [x] Release workflow (tag v* runs build.yml, checks mod_version against the tag, makes a GitHub prerelease, uploads six jars to CurseForge; dry run green; release run 41944925123 for v2026.11.5) (w:3)
- [x] CurseForge release 2026.11.5 (six betas uploaded by the tag, all six approved on CurseForge) (w:2)
- [x] CurseForge releases 2026.11.6 and 2026.11.7 (six builds each by tag; 2026.11.7 fixes upgrade-slot hint icons, the guidebook contents page and the Other Mods page) (w:1)
- [x] Modrinth release: frozen indefinitely by Casey (2026-11-06), existing uploads stay as they are (was on hold; project dN4XaJU1 has the page text and 2026.11.1 (NeoForge 26.1.2 only) and the six builds of 2026.11.2 to 2026.11.4 uploaded by hand, 2026.11.5 not uploaded, a duplicate 2026.11.3 version still to delete) (w:2)

## Port to 26.2 and 26.3
- [x] Four new builds (versions/mc26.2 and mc26.3, NeoForge and Forge) compile, package and keep the 26.1.2 feature set (2026.11.8 in those four; the other six stay at 2026.11.7 until a release bumps all ten) (w:4)
- [x] Headless self-test on all four (620 to 628 checks, 0 failures; ports 8996 to 8999) (w:3)
- [x] Unit tests of shared-core pass (w:1)
- [x] One real-client check per new build (machine and pipe screens opened with the dev tour through the isolated desktop and read, 2026-11-06; frames docs/frames/port-26.x-*) (w:3)
- [~] 26.3 data generators (not ported: recipes, advancements and loot tables use a new bootstrap system; generated data was converted by script; docs/PORTING_26.2_26.3.md) (w:3)
- [~] Integrations on 26.2 and 26.3 (compiled and self-tested with Jade, The One Probe, AE2, JEI and WTHIT where those mods have a build; KubeJS hooks left out, WTHIT left out on 26.3; none seen in a client) (w:2)
- [x] API jars from the private Maven (all four builds compile against com.itmecasey:bei-api 2026.11.7 and smwti-api 2026.11.3 when IMC_MAVEN_* is set, verified; libs/ jars are the fallback without a login) (w:2)
- [x] CI and release workflows, CurseForge script, README, changelog, compatibility table and wiki draft cover the four new builds (w:2)
