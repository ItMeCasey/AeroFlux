# Project Status
<!-- status-format: 1 -->
Updated: 2026-11-09
Summary: All ten builds (Forge and NeoForge on 26.3, 26.2, 26.1.2, 1.21.1 and 1.20.1) have the full machine, pipe, tool and guidebook set and pass the in-game self-test, 2026.11.8 and 2026.11.9 are released, and all ten builds at 2026.11.10 are ready to release (2026.11.8 and 2026.11.9 are out); screens, art, sounds, advancements, tools and guidebook have been seen in real clients, what stays open is the balance review, which is Casey's. Mods that have no build for a loader or version are n/a there, not open. Modrinth is on hold.
Spend: none

## Planning
- [x] Gather requirements and decisions (w:2)
- [x] Write PLAN.md (w:2)

## Phase 0-1: Tooling and foundation
- [x] All ten loader and version targets build (w:3)
- [x] Multiloader skeleton, shared-core and the Build workflow (unit tests, ten builds, ten self-tests, translation check) (w:4)
- [x] Registries, tier config, datagen pipeline (w:3)

## HUDs and GUIs
- [x] Shared widget library (gauge, progress, status, side panel, upgrade slots, redstone button on all six; opened in a real client on all six builds, 2026-11-06, no clipping or overlap; upgrade-slot hints and the tank percent fixed in 2026.11.6) (w:4)
- [x] Machine screens (every machine at gold, the furnace at copper and fluxium, opened in a real client on all six builds with the game's own handlers and read frame by frame; frames in docs/frames/gui-*) (w:4)
- [x] Collector screen with sky panel and tank screen (copper and fluxium, opened in a real client on all six builds and read; the tank now shows <1% below one percent) (w:3)
- [x] In-world look-at overlays (Jade, The One Probe, WTHIT and SMWTI plugins, seen in real clients on every build where those mods exist; see Phase 8-9) (w:3)

## Phase 2-4: Fluids, collectors, storage
- [x] Aero Flux and Moon Flux fluids and buckets (w:3)
- [x] Tiered dual-mode collectors (w:4)
- [x] Tiered tanks with visual fill level (w:4)
- [x] Tanks take any fluid (all ten, self-tested; a saved fluid from a removed mod loads as an empty tank on 26.1 NeoForge, fix a0b33c2) (w:3)
- [x] Tanks keep their contents when broken (all ten, self-tested) (w:1)
- [x] Per-dimension rule map (data-driven aeroflux/dimension_rules on all six) (w:2)
- [x] Conduit bundle (3D pipe layout, shapes, dynamic model, wrench) (w:5)
- [x] Pipe framework, per-face modes, wrench (w:5)
- [x] Fluid and Energy pipes (self-tested; energy now runs from a real source: a gold Flux to FE converter, a copper energy pipe and a test sink block, 631 checks pass with 0 failures on 26.1.2 NeoForge, 2026-11-09; the energy mover is the same code on the other builds) (w:3)
- [x] Item pipes (w:4)
- [x] Redstone pipes (channel 0: a powered lever drives a lamp through a redstone pipe, self-tested on all ten) (w:3)
- [x] Redstone pipe channels (every face of a redstone pipe is tuned to one of 1, 2, 4, 8, 12 or 16 channels by tier; the mover keeps one signal per channel; channel buttons on the pipe screen of all ten builds; unit tests (10) and the self-test pass, 642 checks on 26.1.2 NeoForge; screen frames docs/frames/channels-26.1.2-neoforge, -1.21.1-neoforge and -1.20.1-forge, one per screen layout) (w:3)
- [x] Gas pipes (Mekanism soft dependency, self-tested with Mekanism loaded on every build that has it: 1.21.1 NeoForge and both 1.20.1 builds; n/a: no Mekanism build for 26.3, 26.2, 26.1.2 or 1.21.1 Forge, so gas pipes stay uncraftable there) (w:3)
- [x] Pipe tiers, speed and capacity upgrades, distribution modes, per-face Filter and Priority upgrades (all ten, self-tested) (w:3)
- [x] Pipe config screen (logic self-tested on all ten; opened in a real client and read on 26.1.2 NeoForge and Forge, 1.21.1 NeoForge and Forge, 1.20.1 NeoForge and Forge, and on all four 26.2 and 26.3 builds; frames docs/frames/status-* and port-26.x-*) (w:4)
- [x] Machine side-config panel (logic self-tested on all ten; panel opened with mixed face modes in a real client and read on all ten builds; frames docs/frames/status-* and port-26.x-*) (w:3)
- [x] Flux Wrench (w:2)
- [x] Flux Configurator (copy, paste relative to facing, area paste with limit, eight named presets, one-minute undo; logic in shared-core with unit tests, actions self-tested) (w:4)
- [x] Conduit Tool (edit, remove, remove run, copy and paste, inspect, tier upgrade; logic self-tested on all ten; every mode driven through the client right-click in a real 26.1.2 NeoForge client, 2026-11-09: edit changed the face mode, paste copied the bundle, a diamond pipe raised the tier, remove and remove run took the pipes out; frames docs/frames/status-26.1.2-neoforge-tool-*) (w:4)
- [x] Playtest fixes (26.1 NeoForge checked in game by Casey; tours in real clients on the other five older builds show the fixed screens and scenes, 2026-11-09) (w:3)

## Phase 5-7: Machines and resources
- [x] Machine base framework, menus, Speed/Capacity/Efficiency/Fortune/Silk Touch/Purity upgrades (all ten, self-tested) (w:5)
- [x] Furnace, Grinder, Sawmill (all ten, self-tested) (w:4)
- [x] Alloy Smelter and Infuser (all ten, self-tested; the Infuser takes Aero Flux and Moon Flux since 2026.11.3) (w:3)
- [x] Enchanter and Disenchanter (rules and config self-tested on all ten) (w:4)
- [x] Plant Grower (all ten, self-tested: soil slot, crops on farmland, aeroflux/plant_soils rule files, Mystical Agriculture essence farmland bonus) (w:4)
- [x] Flux Chunk Loader (all ten builds: six tiers, kill switch, admin command, screen; the screen opened in a real client and read on all ten, 2026-11-09, frames docs/frames/status-*-chunk-loader.png) (w:3)
- [x] Flux converters (Flux to FE on all ten, to AE2 power on 26.3, 26.2, 26.1, 1.21.1 NeoForge and both 1.20.1 builds, Create stress on 1.21.1 NeoForge and both 1.20.1 builds; six tiers; the FE converter feeds an energy pipe into a sink in the 26.1.2 self-test) (w:3)
- [x] Create stress converter on 1.20.1 (Forge and NeoForge: Create 6.0.8 loaded in the self-test, 588 checks pass; shaft speed, stress capacity, burn, look-at line and an empty tank checked; screen frame docs/frames/stress-1.20.1-forge-screen.png; n/a: no Create build for 26.3, 26.2, 26.1.2 or Forge 1.21.1) (w:2)
- [x] Auto Import/Export on machine faces and the Plant Grower seed refill (all ten, self-tested) (w:2)
- [x] Machine art v2 (3D multi-element models, six-face textures, tiers, animated active fronts: every machine and pipe item in the inventory, held, on the ground and placed, lit furnace, Creative Flux Tank and Moon Flux seen in real 26.1.2 NeoForge and 1.21.1 NeoForge clients; 1.20.1 Forge scenes too; frames docs/frames/status-*) (w:4)
- [x] Second machine wave (Crafter, Purifier, Moon Spawner, Sky Controller, Blender, Animal Breeder, Repairer, Sifter, Magnet, Sun and Moon Lamps; all ten, self-tested) (w:5)
- [x] Machine sounds (wired on all ten, registration self-tested; in a real 26.1.2 NeoForge client the sound engine was asked to play start, loop, done and stop of a working furnace, 2026-11-09, log lines AEROFLUX-SHOTS SOUND; the dev client runs muted, so this is the engine call, not the speaker) (w:2)
- [x] Resources and full recipe set (machine and block recipes on all six; early-game Infuser; Sawdust to Flux Dust) (w:3)

## Phase 8-9: Advancements and compat
- [x] Advancement tree (15 advancements on all ten; the AeroFlux tab opened in a real 26.1.2 NeoForge client with one and then all advancements granted, frames docs/frames/status-26.1.2-neoforge-advancements-*; Forge 26.3 has no Sifter recipe-unlock advancements, see docs/PORTING_26.2_26.3.md) (w:3)
- [x] Recipe import compat layer (aeroflux/recipe_imports rules on all six for Create, Mekanism, Immersive Engineering, Thermal, Ender IO and others) (w:3)
- [x] JEI plugin (a page per recipe machine with its tiers as stations; the Alloy Smelter and Infuser pages opened in a real client on 26.1.2, 26.2 and 26.3 NeoForge, 1.21.1 NeoForge and 1.20.1 Forge, 2026-11-09, frames docs/frames/jei-*) (w:2)
- [x] EMI plugin (a page per recipe machine; the Alloy Smelter and Infuser pages opened in a real client on 1.21.1 NeoForge and 1.20.1 Forge, 2026-11-09, frames docs/frames/emi-*; EMI logs three harmless dev-mode warnings about untranslated #c:aero_flux style fluid tags) (w:2)
- [x] REI plugin (a page per recipe machine; the Alloy Smelter and Infuser pages opened in a real client on 26.1.2, 26.2, 26.3 and 1.21.1 NeoForge, 2026-11-09, frames docs/frames/rei-*; REI joins a dev run only with -PbeiCheck -PdevOnly=rei; the 1.20.1 Forge page was not opened) (w:2)
- [x] KubeJS and CraftTweaker hooks (KubeJS on NeoForge 26.1.2 and 1.21.1 and both 1.20.1 builds, CraftTweaker on 1.21.1 NeoForge and both 1.20.1 builds, each self-tested with the mod loaded; n/a: no KubeJS build for 26.2 or 26.3 or for Forge 26.1.2 and 1.21.1, no CraftTweaker build for 26.x or Forge 1.21.1) (w:2)
- [x] Fluid and pipe interop with other mods (c:aero_flux style fluid tags; Mekanism tank interop self-tested on 1.21.1 NeoForge and both 1.20.1 builds; AeroFlux tanks and pipes self-tested on all ten; n/a: no Mekanism build for 26.x or Forge 1.21.1) (w:2)
- [x] ME and Dense ME pipes (Applied Energistics 2 soft dependency, so only where AE2 has a build: NeoForge 26.3, 26.2, 26.1.2 and 1.21.1 and both 1.20.1 builds; the AE2 grid-node checks pass with AE2 loaded on 26.1.2 (634 checks), 26.2 (631), 26.3 (626) and 1.20.1; the Forge 26.x and 1.21.1 builds have no ME pipes because AE2 has no Forge build there) (w:3)
- [x] Jade, The One Probe, WTHIT and SMWTI plugins (SMWTI on all ten; Jade, The One Probe and WTHIT seen in real clients on every build where they exist, frames docs/frames/overlay-*; WTHIT on Forge showed "Unknown Fluid" for Flux and was fixed with a fluid description on 26.1.2 and 26.2 Forge, checked again) (w:3)
- [x] BEI recipe viewer plugin (cards for every recipe machine and the Sifter on all six builds, seen in a client on all six: browser, Copper Infuser card, Copper Alloy Smelter cards; 26.1.2 Forge got a recipe sync of its own first) (w:3)
- [x] SMWTI plugin on all six builds (seen in a client on all six: Copper Furnace and Copper Flux Collector panels read correctly) (w:2)
- [x] Client check of every viewer and overlay integration (BEI, SMWTI, Jade, The One Probe, WTHIT, JEI, EMI and REI checked in real clients on the builds where those mods exist; frames in docs/frames) (w:3)
- [x] Compatibility table (docs/COMPATIBILITY.md: which BEI, REI, JEI, EMI, Jade and SMWTI builds exist per version and loader, what AeroFlux has) (w:1)

## Cross-cutting
- [x] Ownership and claim-mod protection (all ten builds; 1.20.1 NeoForge and Forge ported 2026-11-09 with the permission events and Open Parties and Claims, FTB Chunks only through the events; self-tests 574 passed, 0 failed on both 1.20.1 builds) (w:3)
- [x] Performance budget and /aeroflux profile (all ten builds; 1.20.1 ported 2026-11-09, covered by the 1.20.1 self-tests) (w:3)
- [x] Save-data versioning (all ten builds; 1.20.1 ported 2026-11-09, round trip and old-layout loads self-tested on both 1.20.1 builds) (w:2)
- [x] Storage sizes in the config for every block, synced to clients and shown in tooltips (all six, self-tested) (w:2)
- [x] Comparator, hopper and dropper support (all ten, self-tested) (w:2)
- [x] Enchanter and Disenchanter config rules (all ten, self-tested) (w:2)
- [x] Creative tab, creative blocks, sounds, particles (the AeroFlux tab with its 203 items opened and scrolled in a real client, the Creative Flux Tank feeding a lit furnace, collector sparkles and furnace motes seen; frames docs/frames/status-*) (w:3)
- [x] In-game guidebook (all ten: 23 pages in six chapters, contents page, wiki buttons; opened in a real client and a page read on all ten builds, 2026-11-09, frames docs/frames/status-*-guide-page-10.png and guide-26.1.2-neoforge-*) (w:3)
- [x] Per-loader build metadata templates (all six, checked in the built jars) (w:2)
- [x] Localization pipeline (all text through lang keys and datagen, lang_sync.py, TRANSLATING.md) (w:1)
- [x] Security pass of packets, menus, claims and saved data (converter reach check, capped recipe-sync lists, 2026.11.4) (w:2)

## Feature changes of 2026-11-03
- [x] Early-game Infuser recipes (copper ingot build, Sawdust to Flux Dust on Aero Flux; all six) (w:1)
- [x] Machines named <Tier> <Machine> without Flux (display names only, ids unchanged; wiki pages fixed in the site repo, commit 511e40d) (w:1)
- [x] Simpler Conduit Tool and Configurator art, held upright (seen in hand in real clients, frames docs/frames/status-26.1.2-neoforge-tool-edit.png; the Wrench keeps its old art) (w:1)

## Phase 10-11: Port, polish, release
- [x] Port to all ten builds (self-tested 2026-11-02), then to 26.2 and 26.3 on both loaders (2026-11-06) (w:5)
- [ ] Balance review in a real client (play the progression and tune costs and rates; the art, screens, sounds and overlays were checked in clients on 2026-11-09) (w:4)
- [x] Unit tests and CI (shared-core JUnit, ten builds, ten server self-tests, translation check; flaky orb and tank-load tests fixed in e15c4ad and a0b33c2) (w:3)
- [x] Docs and wiki (59 pages live at itmecasey.com/wiki/aeroflux/ with the current machine names) (w:2)
- [x] Release workflow (tag v* runs build.yml, checks mod_version against the tag, makes a GitHub prerelease, uploads ten jars to CurseForge; dry run green; release run 41944925123 for v2026.11.5) (w:3)
- [x] CurseForge release 2026.11.5 (six betas uploaded by the tag, all six approved on CurseForge) (w:2)
- [x] CurseForge releases 2026.11.6 and 2026.11.7 (six builds each by tag; 2026.11.7 fixes upgrade-slot hint icons, the guidebook contents page and the Other Mods page) (w:1)
- [x] Modrinth release: frozen indefinitely by Casey (2026-11-06), existing uploads stay as they are (was on hold; project dN4XaJU1 has the page text and 2026.11.1 (NeoForge 26.1.2 only) and the six builds of 2026.11.2 to 2026.11.4 uploaded by hand, 2026.11.5 not uploaded, a duplicate 2026.11.3 version still to delete) (w:2)

## Port to 26.2 and 26.3
- [x] Four new builds (versions/mc26.2 and mc26.3, NeoForge and Forge) compile, package and keep the 26.1.2 feature set (2026.11.8 in those four, released; every build is 2026.11.10 after the 1.20.1 claim, profile and save-version port, the Forge WTHIT fix and the 26.3 data generators) (w:4)
- [x] Headless self-test on all four (620 to 628 checks, 0 failures; ports 8996 to 8999) (w:3)
- [x] Unit tests of shared-core pass (w:1)
- [x] One real-client check per new build (machine and pipe screens opened with the dev tour through the isolated desktop and read, 2026-11-06; frames docs/frames/port-26.x-*) (w:3)
- [x] 26.3 data generators (ported 2026-11-09: NeoForge gathers recipes, loot tables and advancements through GatherDataRegistryEntriesEvent, Forge through a RegistryDataBuilder layer; runData ran on both and regenerated the data in the 26.3 format; self-tests 623 and 620 passed, 0 failed; the six Flux Sifter advancements are back on Forge 26.3) (w:3)
- [x] Integrations on 26.2 and 26.3 (Jade, The One Probe, WTHIT (26.2), JEI, REI and BEI seen in clients; AE2 grid nodes self-tested on both NeoForge builds; KubeJS hooks and WTHIT on 26.3 are left inert because those mods have no build, see the KubeJS line) (w:2)
- [x] API jars from the private Maven (all four builds compile against com.itmecasey:bei-api 2026.11.7 and smwti-api 2026.11.3 when IMC_MAVEN_* is set, verified; libs/ jars are the fallback without a login) (w:2)
- [x] CI and release workflows, CurseForge script, README, changelog, compatibility table and wiki draft cover the four new builds (w:2)
