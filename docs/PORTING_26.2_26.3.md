# Porting notes: Minecraft 26.2 and 26.3

The four new builds (`versions/mc26.2` and `mc26.3`, NeoForge and Forge) start from the 26.1.2 build of the same loader. Gradle and loader versions come from the other mods' 26.2 and 26.3 builds (BarelyPipes, BarelyPowered, BEI).

| Build | Loader version | Java |
|---|---|---|
| 26.2 NeoForge | `26.2.0.93` | 25 |
| 26.2 Forge | `26.2-65.1.6` | 25 |
| 26.3 NeoForge | `26.3.0.101` | 25 |
| 26.3 Forge | `26.3-66.1.5` | 25 |

## API changes from 26.1.2 to 26.2 (all four builds)

- `Minecraft.setScreen` is `setScreenAndShow`; `minecraft.screen` is `minecraft.gui.screen()`; `getOverlay()` is `gui.overlay()`.
- Dev tour only: `gui.getChat()` is `gui.hud.getChat()`, `getToastManager()` is `gui.toastManager()`, `getMainRenderTarget()` is `gameRenderer.mainRenderTarget()`, `options.hideGui` is `gui.hud.toggle()`, `WorldPresets::createFlatWorldDimensions` is looked up in the registry.
- `InventoryChangeTrigger` moved to `net.minecraft.advancements.triggers`.
- Tag providers take `ResourceKey`s: `builtInRegistryHolder().key()` instead of the block or fluid. Forge has no `BlockTagsProvider`/`IntrinsicHolderTagsProvider` any more (`TagsProvider<Block>` and `FluidTagsProvider` instead).
- `EntityType.COW` is `EntityTypes.COW`; `BlockTags.SAPLINGS` is `BlockItemTags.SAPLINGS.block()`; `Items.LIGHTNING_ROD` is a weathering collection (`.weathering().unaffected()`).
- NeoForge `PlayerInteractEvent.EntityInteract` takes the hit location as a fourth argument.

## Additional changes in 26.3

- Blocks have no `codec()`/`MapCodec` any more (removed from every block class).
- `ModConfig.Type.COMMON` is `STARTUP` on NeoForge; Forge keeps `COMMON`.
- `BlockBehaviour.Properties.isViewBlocking` takes a fourth (box) argument; `player.drop(stack, false)` is `Containers.dropItemStack`.
- `BakedQuad.MaterialInfo` has a shade direction instead of a flag and two more render types.
- `ClockManager.moveToTimeMarker` returns a `MoveResult` instead of a boolean.
- `ConfirmLinkScreen.confirmLinkNow` takes a `URI`.
- Data pack formats: loot tables use `condition` and `modifier` (no `bonus_rolls`), and the `recipe_unlocked` trigger takes `recipes`. The generated data in these two builds was converted by script.
- The data generators are ported to 26.3 (2026-11-09). Recipes, loot tables and advancements are datapack registry entries there: NeoForge gathers them in `GatherDataRegistryEntriesEvent` (`DataGenerators.gatherRegistryEntries`), Forge through a `RegistryDataBuilder` reloadable layer with a `MultiRegistryBootstrap` for the recipes. `gradlew runData` writes the 26.3 format directly (no `category` on crafting recipes, one `recipes` string in `recipe_unlocked`), so the earlier script conversion is gone. Forge 26.3 writes the six Flux Sifter recipe-unlock advancements again, with a `forge:condition` for Ex Deorum and Ex Nihilo Sequentia.

## Left out where the other mod has no build

| Mod | 26.2 | 26.3 |
|---|---|---|
| KubeJS | no build: hooks removed from the NeoForge builds | no build: removed |
| WTHIT | NeoForge and Forge builds exist | no build: plugin removed from both builds |
| Jade, The One Probe, JEI, REI, AE2 | NeoForge only | NeoForge only |
| FTB Chunks | no build: the claim provider is compiled against the 26.1.2 API and only loads if the mod is installed | same |
| EMI, Mekanism, Create, CraftTweaker | no build | no build |

## Our own API jars

`smwti-api` and `bei-api` come from the private Maven (`com.itmecasey:<name>:<mc>-<loader>-<version>`: `bei-api` 2026.11.7, `smwti-api` 2026.11.3) when `IMC_MAVEN_USER` and `IMC_MAVEN_PASSWORD` are set. Without a login, or with `-PapiFromLibs`, the build reads the jars in the build's `libs/` folder.

## Checks (2026-11-06)

Server self-test: 628 (26.2 NeoForge), 621 (26.2 Forge), 623 (26.3 NeoForge), 620 (26.3 Forge) checks, 0 failures. Client: the machine and pipe screens were opened in a real client on all four builds (frames `docs/frames/port-<mc>-<loader>-*-screen.png`).
