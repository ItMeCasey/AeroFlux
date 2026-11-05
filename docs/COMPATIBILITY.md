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
| BEI recipes | gap (below) | code | code | code | code | code |
| REI recipes | no viewer build | code | no viewer build | code | code | code |
| JEI recipes | no viewer build | code | code | code | code | code |
| EMI recipes | no viewer build | no viewer build | no viewer build | code | code | code |
| SMWTI info | code | gap | gap | gap | gap | gap |
| Jade info | no Jade build | code | no Jade build | code | code | code |

Notes:

- **BEI** (new, not released yet): one card category per recipe machine plus one for the Sifter, with each machine's own recipes and the ones imported from other mods, built from the same `ViewerRecipes` lists as JEI, REI and EMI. The plugin class is `compat/bei/AeroFluxBeiPlugin`, found by BEI through its `@BeiPlugin` annotation. AeroFlux compiles only against BEI's API jar, kept in each build's `libs/` folder, and declares BEI as an optional client dependency; the jar ships no BEI classes.
- **BEI on 26.1.2 Forge is a gap.** That build has no `ViewerRecipes`, `MachineRecipes.ownOf` or recipe-import sync (`RecipeImportsSync`), which BEI needs to see AeroFlux's recipes on a 26.1 client. Port those first, then copy the plugin.
- **REI, JEI, EMI** on 1.20.1 NeoForge: the plugin code is there, but the viewers publish no NeoForge 1.20.1 build, so it is exercised only with a Forge file if at all.
- **SMWTI** has builds for all six targets. AeroFlux's plugin (`compat/smwti/AeroFluxSmwtiPlugin`) exists on 26.1.2 Forge only. The other five builds still show Flux info through Jade where Jade exists (26.1.2 NeoForge, 1.21.1 NeoForge, both 1.20.1 builds), through The One Probe and WTHIT where those exist, and through nothing on 1.21.1 Forge, so **1.21.1 Forge is the first SMWTI gap to close**.
- Gas pipes, AE2 and Create support are separate from this table; see the CurseForge description.

## What was tested

| Check | Result |
|---|---|
| All six builds compile and package | yes, 2026-11-05 (`gradlew build -x test`; BEI plugin included in five of them) |
| Built jars contain the BEI plugin class and no BEI API classes | yes for the five builds that have it |
| In-game server self-test (headless) after adding BEI | not run locally; the Build workflow runs it on push |

| REI, JEI, EMI, Jade, SMWTI, The One Probe, WTHIT shown in a real client | not tested on any build in this pass |

The CurseForge description's "Tested" line should say so until the client checks are done: frames go in `docs/frames/`, one per integration per Minecraft version.
