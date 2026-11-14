# Compatibility

AeroFlux needs no other mod. It adds optional support for recipe viewers (BEI, REI, JEI, EMI) and in-world info overlays (SMWTI, Jade, The One Probe, WTHIT) wherever those mods have a build. Every integration lives in its own package under `compat/` and loads only when the other mod is installed.

Checked 2026-11-05 against CurseForge (file lists per Minecraft version and loader).

## Which viewer and overlay builds exist

"Yes" means CurseForge lists a build for that Minecraft version and loader. Jade's 1.20.1 file is tagged for both Forge and NeoForge.

| Mod | 26.1.2 Forge | 26.1.2 NeoForge | 1.21.1 Forge | 1.21.1 NeoForge | 1.20.1 Forge | 1.20.1 NeoForge |
|---|---|---|---|---|---|---|
| BEI | yes | yes | yes | yes | yes | yes |
| REI | no build | yes | no build | yes | yes | no build |
| JEI | no build | yes | yes | yes | yes | no build |
| EMI | no build | no build | no build | yes | yes | no build |
| SMWTI | yes | yes | yes | yes | yes | yes |
| Jade | no build | yes | no build | yes | yes | yes (Forge file) |

## What AeroFlux has for each

"Code" means the integration source is in that build and it compiles. "Not tested" means nobody has opened it in a real client; see the last column of the next table.

| Integration | 26.1.2 Forge | 26.1.2 NeoForge | 1.21.1 Forge | 1.21.1 NeoForge | 1.20.1 Forge | 1.20.1 NeoForge |
|---|---|---|---|---|---|---|
| BEI recipes | code | code | code | code | code | code |
| REI recipes | no viewer build | code | no viewer build | code | code | code |
| JEI recipes | no viewer build | code | code | code | code | code |
| EMI recipes | no viewer build | no viewer build | no viewer build | code | code | code |
| SMWTI info | code | code | code | code | code | code |
| Jade info | no Jade build | code | no Jade build | code | code | code |

Notes:

- **BEI** (new, not released yet): one card category per recipe machine plus one for the Sifter, with each machine's own recipes and the ones imported from other mods, built from the same `ViewerRecipes` lists as JEI, REI and EMI. The plugin class is `compat/bei/AeroFluxBeiPlugin`, found by BEI through its `@BeiPlugin` annotation. AeroFlux compiles only against BEI's API jar, kept in each build's `libs/` folder, and declares BEI as an optional client dependency; the jar ships no BEI classes.
- **BEI on 26.1.2 Forge** needed the recipe helpers first: that build had no `ViewerRecipes`, `MachineRecipes` or recipe sync, and Forge 26.1 has no hook to send a mod's recipe types to its players. `RecipeSync` now sends each machine's own recipes, the imported ones and the sieve drops in a channel message of AeroFlux's own, and the BEI plugin reads them.
- **REI, JEI, EMI** on 1.20.1 NeoForge: the plugin code is there, but the viewers publish no NeoForge 1.20.1 build, so it is exercised only with a Forge file if at all.
- **SMWTI** has builds for all six targets, and AeroFlux's plugin (`compat/smwti/AeroFluxSmwtiPlugin`) is now in all six, sending the same Flux lines the Jade, The One Probe and WTHIT plugins show. 1.21.1 Forge also gained `LookInfo` for it; it has no other overlay.
- Gas pipes, AE2 and Create support are separate from this table; see the CurseForge description.

## What was tested

Client checks use the dev-only tours (`AEROFLUX_SHOTS=bei` and `AEROFLUX_SHOTS=smwti`): the game's own handlers and a teleport drive them, nothing touches the desktop, and every frame was read by eye. The mod under test runs next to AeroFlux alone.

| Check | Result |
|---|---|
| All six builds compile and package | yes, 2026-11-05 (`gradlew build -x test`) |
| BEI in a real client: browser lists AeroFlux items, recipe popup opens, Infuser card and two Alloy Smelter cards drawn | yes on all six builds. Frames: `docs/frames/bei-<version>-<loader>-browser.png`, `-popup_dust.png`, `-popup_fluxsteel.png` |
| SMWTI in a real client: look at a Copper Furnace and a Copper Flux Collector, read the panel | yes on all six builds (furnace: Empty 0 / 1000 mB, Idle; collector: Aero Flux about 1900 / 8000 mB). Frames: `docs/frames/smwti-<version>-<loader>-furnace.png` and `-collector.png`. Retaken on 26.1.2 Forge with SMWTI alone: one panel, correct lines. The first 26.1.2 Forge frames showed a second faint panel because WTHIT was also loaded in the dev run; WTHIT is now opt-in there (`-PbeiCheck`), as on the other builds. The 26.1.2 Forge BEI frames were retaken with BEI alone (WTHIT off) on 2026-11-06 and look the same |
| In-game server self-test after these changes | not run locally; the Build workflow runs it on push |
| Jade in a real client (Jade alone in the dev run): look at a Copper Furnace and a Copper Flux Collector | yes on 26.1.2 NeoForge, 1.21.1 NeoForge, 1.20.1 NeoForge and 1.20.1 Forge, 2026-11-06 (furnace: Idle, Empty 1,000 mB; collector: Aero Flux about 1,900 mB). Frames `docs/frames/jade-<version>-<loader>-*.png` |
| JEI, REI, EMI in a real client | not tested. REI needs Architectury and Cloth Config in the dev run, which are not set up; JEI and EMI would need a hook to open AeroFlux's recipe pages from inside the game, which the tours do not have. The plugins compile and their unit and self-test coverage runs in CI |
| The One Probe, WTHIT in a real client | not tested |
| Jade on 1.21.1 Forge | no usable build: Jade has none for Forge 1.21.1 |

The CurseForge description's "Tested" line should say BEI and SMWTI were checked in a client on all six builds and the other viewers were not.

## 26.2 and 26.3 (added 2026.11.8)

Checked 2026-11-06 against Modrinth's version lists (the other tables above were checked on CurseForge). "Code" means the integration is in the build and compiles; "no build" means the other mod has none for that version and loader, so AeroFlux's part is left out or stays inert.

| Mod | 26.2 NeoForge | 26.2 Forge | 26.3 NeoForge | 26.3 Forge |
|---|---|---|---|---|
| BEI | code | code | code | code |
| SMWTI | code | code | code | code |
| Jade | code | no build | code | no build |
| The One Probe | code | no build | code | no build |
| JEI | code | no build | code | no build |
| REI | code | no build | code | no build |
| EMI | no build | no build | no build | no build |
| WTHIT | code | code | no build (plugin removed) | no build (plugin removed) |
| Applied Energistics 2 (ME pipes, AE converter) | code | no build | code | no build |
| KubeJS | no build (hooks removed) | no build | no build (hooks removed) | no build |
| Mekanism, Create, CraftTweaker | no build | no build | no build | no build |

- The 26.2 and 26.3 builds were compiled and self-tested with Jade, The One Probe, AE2, JEI and WTHIT (26.2) in the dev run where those mods have a build. Only the server self-test ran with them; nothing was opened in a client except as listed in the status file.
- The FTB Chunks claim provider is compiled against the 26.1.2 API (FTB Chunks has no 26.2 or 26.3 build) and only loads when that mod is installed.
- Both 26.3 builds have working data generators, and 26.3 Forge has its six Flux Sifter recipe-unlock advancements back; see [PORTING_26.2_26.3.md](PORTING_26.2_26.3.md).

## Create (added 2026.11.10)

The Flux Stress converter needs Create, so it exists only where Create has a build: 1.21.1 NeoForge and, since 2026.11.10, 1.20.1 Forge and NeoForge (Create 6.0.8). For 26.3, 26.2, 26.1.2 and Forge 1.21.1 there is no Create build, so nothing is open there.

## ModJS (added 2026.11.12)

ModJS is optional on all ten builds. With it installed, server and startup scripts get the `AeroFlux` global described in
[scripting.md](scripting.md). It is found by reflection when the mod loads, so AeroFlux has no dependency on it. The bridge
is unit tested in `shared-core` with real JavaScript in Rhino, and the 26.1.2 NeoForge self test checks the reads and events
and, with `-PmodjsJar=<path to a ModJS jar>`, runs a script through ModJS itself.
