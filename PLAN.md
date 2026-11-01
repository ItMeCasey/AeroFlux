# AeroFlux: Mod Plan

Decisions locked with Casey (2026-11-01):
- **Targets:** all 6 builds: NeoForge + Forge on MC 26.1.2, 1.21.1, 1.20.1.
- **Layout:** multiloader per version: one shared `common` module, thin loader modules.
- **Flux:** real placeable fluids (Aero Flux, Moon Flux) with buckets.
- **Roles:** Aero Flux powers production machines. Moon Flux powers magic machines and night plant growth.
- **Transport:** adjacency plus AeroFlux conduits; other mods' fluid pipes also work.
- **Collectors:** one tiered dual-mode block: Aero by day, Moon by night.
- **Compat v1:** recipe-type import, JEI/EMI/REI, KubeJS/CraftTweaker, fluid/pipe interop.

## 1. Core design

### Flux and collection
- `aeroflux:aero_flux` and `aeroflux:moon_flux`: source + flowing fluids, bucket, fluid type (glow, viscosity, no infinite sources).
- **Look:** Aero Flux is a light yellow with a faint glow (tint `#FFF2A8`, light level 5, emissive texture layer). Moon Flux is a bluish-white with a faint glow (tint `#D6E8FF`, light level 5, emissive texture layer). Both use soft animated still and flow textures, and the same two colors drive buckets, tank fill, gauges, tooltips, particles, conduit cores and the HUD badges, so Aero and Moon read instantly everywhere.
- Collector output per tick = `tierRate x skyFactor x phaseFactor`.
  - Needs open sky above (heightmap check, cached, rechecked every 20 ticks).
  - Day (`dayTime` in sun window) makes Aero Flux; night makes Moon Flux. Dusk/dawn blend to 0 for a short gap.
  - Weather: rain 50%, thunder 25% of Aero; Moon Flux unaffected by rain (thematic, tunable).
  - Moon phase scales Moon Flux (full moon 100%, new moon 40%).
  - Dimensions: controlled by a per-dimension rule map (see "Per-dimension rules" below). Default: Overworld only; Nether and End produce nothing.
- Output goes to an internal tank, then pushes to adjacent fluid handlers and conduits.

### Tiers (six; shared by collectors, tanks, conduits)
| Tier | Name | Frame material | Collector rate (mB/t, full sun) | Collector buffer | Tank capacity |
|---|---|---|---|---|---|
| 1 | Copper | copper | 8 | 8 B | 32 B |
| 2 | Iron | iron | 20 | 24 B | 128 B |
| 3 | Gold | gold + amethyst | 50 | 64 B | 512 B |
| 4 | Diamond | diamond | 120 | 160 B | 2,048 B |
| 5 | Netherite | netherite | 300 | 400 B | 8,192 B |
| 6 | Fluxium | Fluxsteel + Aero/Moon Crystals | 750 | 1,000 B | 32,768 B |

Numbers are starting values; balance pass in Phase 9. All tier stats live in a data-driven config (`aeroflux-common.toml` plus datapack JSON).

### Per-dimension rules
A data-driven map decides what each dimension does. One JSON per dimension at `data/<namespace>/aeroflux/dimension_rules/<dimension>.json` (datapacks and modpacks can add or override), plus a `[dimensions]` section in the config for quick overrides. Fields:
- `aero_enabled`, `moon_enabled`: whether each flux can be collected.
- `aero_multiplier`, `moon_multiplier`: output scaling (e.g. 0.5 in a dim with weaker light).
- `time_source`: `dimension_time` (use that dimension's day/night), `overworld_time` (follow the Overworld clock), `always_day`, `always_night`, or `always_both` (for fixed-light dimensions).
- `requires_sky`: whether open sky is needed, with `ignore_weather` as an option.
- `chunk_loader_allowed`: whether Flux Chunk Loaders may be placed there.
Defaults: Overworld on with `dimension_time`; Nether, End and unknown modded dimensions off unless a rule exists. Day-length mods work because `dimension_time` reads that dimension's actual cycle.

### Storage with visible level
- Tier 1-6 Flux Tanks (the Fluxium tank reuses the Netherite tank model with the Fluxium frame texture). Fill shown by a block entity renderer drawing the fluid sprite at height = fill ratio, tinted per flux type, with emissive light. Tank also shows a 0-8 `fill` blockstate for cheap far-LOD and light level.
- Sync fill to client only when the 1/16th step changes (not every tick).
- Tooltip and Waila/Jade/TOP info (soft dependency).
- Tanks stack vertically and share contents (optional config).
- Tanks keep contents when broken (item NBT/data component).

### Pipes (7 types)
All seven share one pipe framework (`PipeBlock`, `PipeNetwork<T>`, per-face connection state). The first five (Fluid, Item, Redstone, Energy, Gas) each have their own block family, six tiers (same tier table), texture set and color band. The two ME pipes are the exception: they are not tiered (see their rows). Pipes join only pipes of the same type; they connect to any block exposing the matching capability.

| Pipe | Carries | Behavior | Base throughput (tier 1 -> 6) |
|---|---|---|---|
| **Fluid Pipe** | Any fluid (Aero/Moon Flux, water, lava, other mods) | One fluid per network at a time; buffered segments; flow equalizes between tanks | 100 -> 12,000 mB/t |
| **Item Pipe** | Items | Routed packets with visible items moving in the pipe; round-robin or nearest destination; filter support | 1 -> 64 items per op, 20 -> 2 ticks per op |
| **Redstone Pipe** | Redstone signal 0-15 | Carries the strongest signal in the network with no decay, up to 16 labeled channels (dye color); outputs at every Export face | tier sets channel count (1, 2, 4, 8, 12, 16) |
| **Energy Pipe** | Energy (FE; RF and FE are the same thing) | Uses the loader's energy capability; tier sets transfer limit. AeroFlux itself neither makes nor uses Energy; the pipe is a carrier for other mods' generators and machines | 1,000 -> 1,000,000 FE/t |
| **Gas Pipe** | Gas / chemicals from other mods (e.g. Mekanism) | AeroFlux has no gas of its own. The pipe is a carrier for other mods' gas handlers and is active only when a mod providing the gas capability is installed; otherwise the blocks are hidden from tabs and recipes | 256 -> 64,000 mB/t |
| **ME Pipe** | Applied Energistics 2 network link | Soft dependency on AE2, like the Gas Pipe. A carrier that joins the AE2 grid: it exposes the AE2 grid-node capability so AE2 devices, controllers and other AE2 cables attach to it, and it joins neighbor ME and Dense ME pipes. AeroFlux adds no ME devices. Hidden from tabs and recipes when AE2 is absent. Behaves like AE2's Glass/Covered/Smart cable | Fixed 8 channels (no tiers) |
| **Dense ME Pipe** | Applied Energistics 2 network backbone | Same as the ME Pipe but a long-haul trunk like AE2's Dense cable: more channels, and it connects only end to end (no side devices or buses; step down to a normal ME Pipe to attach them) | Fixed 32 channels (no tiers) |

- **ME pipe rules.** AE2 cables have no tiers, so the six-tier table, pipe upgrades (Speed, Capacity), and Import/Export modes do not apply; per-face modes are limited to `Open`, `Off` and `Blocked`. Channels and device attachment are handled by AE2 itself, not by `PipeNetwork`. Smart-style channel readout (lit stripes, 4 channels each) is shown on the pipe model and in the pipe screen. AE2's 128-channel tiers come from a third-party add-on; AeroFlux does not add them.

- **Per-face connection mode** on every pipe, every face: `Open` (both ways), `Import` (pull from neighbor into network), `Export` (push from network into neighbor), `Off` (connected but passive; visible stub), `Blocked` (no connection; pipe visually disconnects and nothing can attach). Pipes show the mode by arm color and arrow.
- **Redstone Pipe** uses the same modes: `Import` reads the neighbor's redstone level onto the pipe's channel, `Export` emits power to the neighbor.
- **Networks:** one `PipeNetwork` per connected same-type graph, cached per loaded chunk group, rebuilt on neighbor or mode change only; transfer happens in a single network tick, not per pipe. Unloaded-chunk boundaries pause transfer safely.
- **AeroFlux Wrench:** click a pipe face to cycle its mode; sneak-click to toggle the whole pipe; rotates machines; dismantle with sneak.
- **Pipe screen:** right-click a pipe to open a small GUI with the cube diagram of its six faces, mode per face, upgrade slots, channel selector (Redstone), and filter slots (Item, Fluid).

### Conduit bundles (EnderIO-style grouping)
All pipe types can share one block space, as in Ender IO. A `ConduitBundleBlock` (one block entity) holds up to one conduit per network type (Fluid, Item, Redstone, Energy, Gas, ME), so six conduits run through a single block position and one wire-like bundle. The ME lane holds either an ME Pipe or a Dense ME Pipe, never both (they are one network type). A Dense ME Pipe has no side connections, so it sits in the lane as a pass-through trunk.
- **Placing:** right-click an existing pipe or bundle with another type's pipe item to add it to the bundle; the block turns into a bundle automatically. A single pipe in a block is just a bundle with one conduit, so there is one block, one block entity and one network layer for everything.
- **Pipe size and layout (decided with Casey).** Every pipe is 4x4 px on the 16 px block grid, with at least 1 px between pipes. The pipes in a block are always centered as a group, and the layout depends only on how many types the block holds:
  - 1 pipe: centered (x 6-10, y 6-10).
  - 2 pipes: side by side, centered, 2 px apart (x 3-7 and 9-13, y 6-10).
  - 3 pipes: a triangle, one on top and two below, centered (top at x 6-10, y 3-7; bottom at x 3-7 and 9-13, y 9-13).
  - 4 pipes: a 2x2 square, centered (x 3-7 and 9-13, y 3-7 and 9-13).
  - 5 and 6 pipes: a ring (pentagon, then hexagon) around the center, about 5 px from center to pipe center. Exact pixels are set in the Phase 4 prototype.
- **Fixed order.** Types are always placed in this order: Fluid, Item, Redstone, Energy, Gas, ME. The pipes fill the layout slots clockwise from the top (for 2 pipes, left then right). The same set of types therefore always gives the same layout, so a run with the same set from block to block lines up exactly. Adding a pipe to a bundle re-centers the group.
- **Colors and glyphs.** Each pipe is its main color, flat, with a lighter highlight along its top edge and a darker shade along its bottom edge. A small glyph repeats along the length (every 8 px) to say what it carries. Keep it simple: no busy patterns.

| Pipe | Color | Hex | Glyph |
|---|---|---|---|
| Fluid | light gray | `#C9CED3` | Water drop |
| Item | dark gray | `#4B5057` | Small box |
| Redstone | red | `#D8322C` | Dust dot |
| Energy | orange | `#E8731A` | Lightning bolt |
| Gas | gold-yellow | `#E6B422` | Wisp (curl) |
| ME | purple | `#8B4FD8` | Diamond |
| Dense ME | dark purple | `#5E2FA8` | Diamond with a ring |

  Gas gold-yellow is distinct from Aero Flux's pale yellow (`#FFF2A8`), which only appears inside Fluid pipes, tanks and gauges.
- **Model:** each conduit renders as its own colored core and arms in its layout slot. One block with a block entity and a dynamic baked model (NeoForge `IDynamicBakedModel`, a per-loader adapter on Forge), not multipart blockstates: a bundle's type set and per-type, per-face connections are about 42 on/off flags, far too many blockstate combinations to register. The model reads the block entity, places each pipe's core and arms in its layout slot, and caches the quads per (type set, connections) key.
- **Hit boxes:** the shape is built per conduit and per face arm. Because a pipe is only 4x4 px, the wrench, upgrade insertion and mode changes use the pipe nearest the crosshair inside the bundle instead of requiring an exact hit on the pipe.
- **Networks:** each conduit still joins a network of its own type only. Different types in the same block never interact. Per-type, per-face modes (Open/Import/Export/Off/Blocked) are stored separately on the bundle.
- **Tiers:** each conduit in a bundle has its own tier and upgrades, so a bundle can carry a tier 6 Energy conduit next to a tier 2 Item conduit.
- **Bundle screen:** right-click a bundle for the pipe screen with tabs for each conduit it contains (color-coded icons). Each tab has the cube diagram, mode per face, channel selector (Redstone), filter slots and upgrade slots.
- **Quick tools:** sneak-wrench removes only the targeted conduit; the wrench's "Dismantle all" mode picks up the whole bundle and keeps its contents/settings in the item. A "copy settings" paste applies one bundle's modes and upgrades to another.
- **Redstone lanes:** a Redstone conduit holds all 16 color channels inside its single lane; no extra lanes needed.
- **Connection rule:** pipe blocks join each other only when both blocks hold the identical set of types, so same-set runs (straight or around corners) need no extra block. A change in the set needs a Pipe Node (below). Machines and other mods' blocks attach to a pipe directly with no node: at a block's side faces each conduit connects independently, so an Item conduit can attach to a chest while an Energy conduit on the same face attaches to a generator next to it. Faces show one stub per active conduit.
- **Machine connector.** Where a bundle attaches to a machine or other block, the connector is a simple block 2 px deep, measured from the machine face. Its face is just large enough to fit every pipe in that block, so it matches the bounding box of the pipe layout (for example 4x4 px for one pipe, 10x4 for two). It is flat dark gray, the same as the node, and the pipes run into it.
- **Pipe Node block.** A flat dark gray block (one solid color, no texture, no ports, no extra detail), used where pipes break off or the set of types changes. It holds any set of the six types, and each face carries any subset of them, so a bundle entering one face can leave as two runs (for example Fluid and Item to the north, Energy and Gas to the east). The pipes simply enter and leave through the faces, each in its layout slot and color. It joins pipe blocks of any set on any face, uses the same per-type, per-face modes, and opens the same bundle screen. Throughput is limited by the pipes' own tiers; the node has no tier.
- **Facades (later):** optional paint-with-block cover. Out of scope for v1.
- **Ender IO behavior worth matching** (from fan guides and wikis, not official docs; check in game before locking): right-click a connector stub to open that connection's settings; insert and extract are separate toggles with optional redstone control; the top fluid tier has channel colors for insert and extract, an integer insert priority (highest fills first), and round-robin extraction; item, fluid and redstone filters and an extraction speed upgrade exist; mixed tiers connect on one network and speed drops to the slowest tier. These map onto the per-face modes, filter and priority upgrades, and the bundle screen already planned. Ender IO also keeps different conduit types in one block, which this section already copies.
- **Prototype status (2026-11-01).** The bundle exists on all six targets: shared rules in `shared-core` (`core.conduit`, 37 unit tests), one `ConduitBundleBlock` with a block entity, seven pipe items, a first Flux Wrench that cycles the mode of the nearest pipe, a plain Pipe Node, neighbor-based connections, cached hit shapes and a dynamic baked model. Decisions made while building it: the 5 and 6 pipe rings use radii of 5.3 and 5.8 px (a 5 px ring leaves under 1 px between square pipes); each pipe's core sits at its slot in the north-south plane and arms run straight from the core, so a straight run is evenly spaced but arms of different pipes overlap where a bundle bends or branches (resolving that is still open); a Dense ME pipe never attaches to side blocks. Redstone, gas and ME pipes have nothing to attach to yet, and no network or transfer exists. The 26.1 targets use a different model mechanism (NeoForge: a custom block state model type; Forge: a baking-result swap) and have not been seen in a running world.
- **Technical risk:** per-conduit lane shapes, ray tracing and rendering are the most complex rendering and interaction work in the mod. They are built once in `common` and shared; Phase 4 gets extra time and a prototype before the individual conduit types.

### Pipe upgrades
Each conduit (not each block) has its own upgrade slots; each conduit segment has 3 upgrade slots (4 on tier 5-6). Upgrades are items inserted through the pipe screen; stacks add up with diminishing returns.

| Upgrade | Effect (per upgrade, max 4 stacked) |
|---|---|
| **Speed** | Item: shorter ticks per op; Fluid/Gas/Energy: higher transfer rate (+50% base) ; Redstone: no delay between updates |
| **Capacity** | Larger pipe buffer and per-op batch size (+100% buffer; Item pipes +8 items per op) |
| **Filter** | Item/fluid whitelist or blacklist slots (extra upgrade, optional in v1) |
| **Priority** | Higher-priority destinations fill first (extra upgrade, optional in v1) |

Upgrades are crafted from the existing resources (Speed: Aero Crystal + redstone + gold; Capacity: Moon Crystal + iron + quartz; Filter/Priority: Fluxsteel + hopper/comparator). Upgrade cost scales with the pipe's tier requirement (some need tier 3+). Tooltips list effects and combined totals.

### Machines
All use one `FluxMachineBlockEntity` base: input/output slots, one Flux tank (Aero or Moon), progress, upgrade slots, redstone mode, and a `SideConfig` per resource type (items, fluid, energy, gas, redstone). `Blocked` hides the capability on that face so neither pipes nor other mods' hoppers/pipes can reach it; `Import`/`Export`/`Both` gate insert/extract on that face; `Off` leaves it connected but inert. Machines only expose the types they use (e.g. Furnace: items + fluid; no gas).

| Machine | Flux | Function |
|---|---|---|
| Flux Furnace | Aero | Smelts (reads vanilla smelting/blasting/smoking; 2x speed) |
| Flux Grinder | Aero | Ore -> 2x dust/raw, gems -> powder, bone -> bonemeal, etc. |
| Flux Sawmill | Aero | Logs -> planks (6 per log), plus sawdust byproduct chance |
| Alloy Smelter | Aero | 2-3 inputs -> alloy (e.g. Fluxsteel, Aerogold) |
| Flux Infuser | Moon | Item + Moon Flux -> infused item (crystals, tools, blocks) |
| Flux Enchanter | Moon | Applies enchantments from book + lapis + Moon Flux cost |
| Flux Disenchanter | Moon | Strips enchantments to books; item survives, XP optional |
| Plant Grower | Aero by day, Moon by night | Grows crops/saplings/flowers; Moon Flux gives bonus yield for night-flowers and fungi |

Machine upgrades (craftable): Speed, Efficiency, Capacity, Fortune (grinder), Silk (grower).

### Flux Chunk Loader
AeroFlux's own chunk loader, powered by Flux (it accepts either Aero or Moon Flux).
- **Tiers:** the same six tiers set the area: 1x1, 3x3, 5x5, 7x7, 9x9, 11x11 chunks, with Flux cost per chunk per tick that drops at higher tiers.
- **Behavior:** while it has Flux it forces its chunks to stay loaded and ticking (loader tickets: NeoForge `TicketController`, Forge `ForgeChunkManager`, per-version adapter). When it runs dry or is broken, the tickets release. A short grace period (configurable) avoids flicker.
- **Why it matters:** loaded chunks keep collectors, machines and pipe networks running, which solves the unloaded-chunk problem for collection.
- **GUI:** same widget set: fill gauge, runtime remaining at the current draw, a 2D chunk-map showing the loaded area with the center highlighted, and the usual side-config window.
- **Limits and safety:** per-player and per-dimension caps in config, `chunk_loader_allowed` per dimension, an owner field that respects claim mods, and `/aeroflux chunkloaders` for admins to list and remove loaders. Server-wide kill switch in config.

### Storage with visible level

### Self-contained progression (all from vanilla resources)
- **Flux Dust:** crafted from glowstone dust + quartz + redstone.
- **Aero Crystal:** quartz + Flux Dust (workbench early, Infuser later). **Moon Crystal:** amethyst shard + Flux Dust.
- **Fluxsteel:** iron + Flux Dust in Alloy Smelter. **Aerogold:** gold + Aero Crystal. Others as needed.
- Bootstrap, no machines needed: hand-crafted Copper Collector, Copper Tank, Copper Conduit, Flux Furnace (Tier 1), Wrench.
- Tier upgrades by crafting the next tier from the previous one + material.
- Worldgen: none required. Optional Flux Geodes later; v1 avoids it.

### Advancements (tree, root `aeroflux:root`)
Root "Soak up the Sky" -> Copper Collector -> First Drop (collect Aero) -> Moonlighting (collect Moon) -> tank tiers -> each machine (first use) -> Fluxsteel -> Enchant/Disenchant -> Plant Grower harvest -> Netherite Collector -> "Full Cycle" (store 24h of both). Each grants a recipe-book unlock or small item. Datagen-built.

### Tools
Three tools, each with a clear job. All use the same tier-colored frames, show their current mode in a HUD line above the hotbar, and respect ownership and claim checks. Durability is optional (config, default unbreakable). Recipes use Fluxsteel, so they unlock after the Alloy Smelter.

| Tool | For | Use |
|---|---|---|
| **Flux Wrench** | Machines and general blocks | Right-click rotates; sneak right-click dismantles and returns the block with its contents, upgrades and settings stored in the item. Also cycles a pipe face's mode (replaces the earlier "wrench-like item" mention). |
| **Flux Configurator** | Copying and editing machine settings | See below. |
| **Conduit Tool** | Editing and removing pipes | See below. |

**Flux Configurator (machine settings)**
- **Copy:** sneak right-click a machine to copy its configuration: all side modes for items/fluid/energy/gas/redstone, redstone mode, auto-eject, comparator mode, filters and upgrade *types* (not the upgrade items). The HUD shows what was copied.
- **Paste:** right-click another machine of the same family (or the same machine type) to apply it. Fields the target does not support are skipped and listed in a chat summary.
- **Area paste:** hold the use key to drag a box, then paste to every matching machine inside; confirmation screen shows the count. Capped by a config limit (default 64) and respects ownership.
- **Presets:** 8 named memory slots stored in the item; open its screen to rename, delete, or view a preset's contents. Presets sync to the item data component/NBT and survive death.
- **Edit in place:** the Configurator's screen can edit a preset directly (the same cube diagram as the machine's side-config window), so you can build a setup before touching any machine.
- **Undo:** the last paste or area paste can be undone once (stored for a short time).
- Works on any block with an AeroFlux config, including collectors, tanks, conduit bundles, and the chunk loader (for its owner/mode settings).

**Conduit Tool (editing and removing pipes)**
- **Modes** (scroll with Shift or press the keybind): *Edit*, *Remove*, *Replace tier*, *Copy bundle*, *Paste bundle*, *Inspect*.
- **Edit:** click a conduit face to cycle its mode (Open, Import, Export, Off, Blocked); right-click on the lane opens its pipe screen.
- **Remove:** click a conduit lane to remove just that conduit (the bundle keeps the others). Sneak-click removes the whole bundle and returns contents to the player or drops them. **Line remove:** click-drag along a run to remove all conduits of one type between two points (shows an outline preview, confirmation on large runs).
- **Replace tier:** swap a conduit's tier in place by using it with the new-tier pipe in hand; the old pipe is returned and upgrades are kept.
- **Copy/Paste bundle:** copies a bundle's conduits, tiers, modes and upgrade types, then pastes onto another bundle or onto a straight run (paste along a line); missing items are taken from the player's inventory.
- **Inspect:** shows the network summary (type, size, contents, throughput, connected endpoints) in chat and a highlighted outline of the whole network for a few seconds.
- Safety: removing a conduit in a loaded network sends its stored fluids/items back to inventory or drops them; Energy and Redstone conduits hold nothing.

## 1b. HUDs and GUIs

Shared widget library in each version's `common` (`client/widget/`), so every screen looks and behaves the same. All widgets are plain GUI components; only the render calls differ between MC versions (`GuiGraphics` in 1.20.1+, adjusted for 26.1).

### Widgets
| Widget | Behavior |
|---|---|
| **FluxGauge** | Vertical tank bar. Draws the real fluid sprite (`aeroflux:block/aero_flux_still` / `moon_flux_still`) tiled from the bottom up to fill height, using the fluid type's tint, over a framed glass background with tick marks every 10%. Animated sprite frames follow game time. Hover tooltip: fluid name, `12,400 / 32,000 mB`, percent, and net rate (`+120 mB/t`). Sprite fetched from the block atlas, so resource packs retexture it. |
| **ProgressArrow / ProgressFlame / ProgressGear** | Cropped overlay on an empty icon; fill = `progress / maxProgress`, drawn left-to-right (arrow), bottom-up (flame), or rotating (gear for grinder/sawmill). Machine-specific: furnace flame, grinder gears, sawmill blade, infuser swirl, enchanter glyphs, grower sprout. Tooltip: seconds remaining. |
| **FluxCostBar** | Thin bar next to the progress icon showing Flux used per operation versus Flux available; turns red and pulses if the tank cannot cover the next tick. |
| **StatusLight** | Small lamp: green = running, yellow = idle/no input, red = no Flux, blue = output full, grey = redstone-disabled. Tooltip gives the exact reason. |
| **FluxTypeBadge** | Sun or moon icon at the gauge top showing which flux the machine accepts and whether it is currently receiving it. |
| **UpgradeSlots** | Slot column with ghost icons for allowed upgrades and a per-upgrade effect tooltip. |
| **SideConfigPanel** | Small collapsible window docked to the machine screen (tab icon on the left edge, opens a ~96x110 popout). A cube diagram of the six faces (labeled Front/Back/Left/Right/Top/Bottom, relative to the machine's facing) with a resource-type selector across the top: **Items, Fluid, Energy, Gas, Redstone** (types the machine does not use are hidden). Click a face to cycle **Import / Export / Both / Off / Blocked** for the selected type (right-click cycles backward). Face colors: blue = import, orange = export, green = both, grey = off, red with an X = blocked. Hover shows the mode and what is attached (pipe type, neighbor block). Buttons: "All Off", "Copy to all faces", "Reset", "Auto-eject" toggle (push outputs to Export faces on a timer), and "Copy settings / Paste settings" with the wrench. Changes are sent to the server per click and applied immediately; server validates the face and mode. |
| **RedstoneTab** | Always on / On with signal / Off with signal / Pulse. |
| **TierBadge** | Frame in the tier color on every GUI header (Copper through Fluxium), showing tier name and stats. |

### Screens
- **Machine screens** (all 8 machines): input slots left, progress icon center, output slots right, FluxGauge on the far left, StatusLight and FluxCostBar above the progress icon, upgrade and config tabs on the edges. Slot layout and icon swap per machine.
- **Collector screen**: large FluxGauge plus a **sky panel**: sun/moon dial showing current phase of day, sky-visibility check (green tick or red "blocked by <block>" with position), weather modifier, moon-phase icon and percentage, current output rate in mB/t, and an estimated "full in" time. A 24-hour mini-graph of expected output (day arc in gold, night arc in violet).
- **Tank screen**: large gauge, capacity, fill percentage, contents, in/out rate, and a "stack link" indicator when connected vertically.
- **Enchanter / Disenchanter**: extra panel listing the candidate enchantments with level selectors and their Moon Flux and lapis costs, greyed out when unaffordable.
- **Plant Grower**: shows the planted crop, growth stage bar, current flux mode (Aero by day / Moon by night) and the yield bonus.
- **Conduit / wrench**: no screen; the wrench opens a small radial on face selection.

### In-world HUD (works without Jade/TOP)
- **Look-at overlay** (client event, config toggle, movable anchor): when looking at an AeroFlux block, draws a compact panel with the block name, tier badge, a horizontal FluxGauge strip with the fluid texture, and a progress bar plus status text for machines. Collectors add the sun/moon icon and current rate; conduits show throughput and connected network size.
- **Held-item HUD**: holding the Wrench shows the targeted face and its current mode; holding a Flux bucket or tank item shows stored amount.
- **Inventory/tooltips**: tank and collector items show contents as a mini-gauge in the tooltip (fluid sprite bar plus text), stats per tier, and Shift for extra details.
- **Warning toasts** (optional, off by default): "Collector blocked", "Machine out of Flux" for blocks you recently opened.
- Jade, The One Probe and WTHIT providers are fed from the same data (see section 3, item 5), so the built-in overlay and the third-party mods show identical numbers.

### Technical notes
- **Sync:** server sends gauge/progress state to open menus through `ContainerData` or custom packets (Flux amounts exceed 16-bit, so use int/long packets, not vanilla `DataSlot` shorts). Look-at overlay uses a throttled request packet (max 4/sec per player) and caches the reply client-side.
- **Rendering:** fluid sprite drawn with `GuiGraphics.blit` using sprite UVs, tiled vertically; shared helper `FluidRenderUtil` used by gauges, tooltips and the tank block entity renderer so the fill matches in-world.
- **Fluid texture lookup:** via `IClientFluidTypeExtensions` (NeoForge) / `IClientFluidTypeExtensions` on Forge, with a per-version adapter.
- **Config:** HUD scale, anchor, on/off, tooltip detail level, colorblind-safe status icons (shapes plus colors), reduced-motion option for animations.
- **Textures:** one GUI atlas per tier frame plus widget sheet (`gui/widgets.png`, 256x256), generated alongside the block textures.

## 2. Architecture

```
AeroFlux/
  versions/
    mc1.20.1/   common/  forge/  neoforge/
    mc1.21.1/   common/  forge/  neoforge/
    mc26.1.2/   common/  forge/  neoforge/
  shared-core/          pure Java, no MC imports: tier math, flux rules, recipe model, config schema, compat mapping parser
  assets-src/           textures/models/lang source of truth, copied into each version
  buildSrc/ or build-logic/   shared Gradle conventions
```

- **`shared-core`** holds everything that does not touch Minecraft classes (tier tables, daylight/phase math, network graph algorithm, compat mapping format). Unit-testable with JUnit, no game launch.
- **Per-version `common`** holds MC-touching logic (blocks, BEs, menus, screens, recipes). APIs differ across 1.20.1 / 1.21.1 / 26.1 (recipe inputs, data components vs NBT, codecs), so each version gets its own common with the same package layout; a `compat-mc` interface layer inside minimizes divergence.
- **Loader modules** do only: registration (`DeferredRegister`), capability/fluid-handler exposure, events, networking, config binding, datagen entry points.
- Build: Gradle with ModDevGradle (NeoForge) and ForgeGradle (Forge); Architectury Loom is an alternative if 26.1 tooling supports it (decide in Phase 0). Mojang mappings throughout. Java 17 for 1.20.1, Java 21 for 1.21.1; 26.1 toolchain to verify.
- **As built (Phase 1):** `shared-core` is a standalone Gradle project with unit tests (`Tier`, `FluxType`, `DimensionRules`, `CollectionMath`). Each loader project adds `shared-core/src/main/java` as a source directory, so the code compiles into every jar without jar-in-jar. Per-version `common` modules are not split out yet; each loader project holds its own code until a second loader port makes the duplication worth factoring. Registries, the tier config and datagen exist on 1.21.1 NeoForge first and are ported from there.
- Fluid access goes through our `FluxHandler` interface; each loader adapter maps to `IFluidHandler`/capability. Forge 1.20.1 uses `LazyOptional` capabilities; NeoForge 1.21.1 uses `BlockCapability`.
- Data generation: recipes, loot, tags, blockstates/models, lang, advancements generated, then committed.

## 3. Compatibility layer

1. **Recipe import.** At `RecipesUpdatedEvent`/reload, scan the recipe manager for foreign recipe types. A mapping file `data/aeroflux/compat/<name>.json` declares: foreign type id, which machine, JSON paths for inputs/outputs/chance/time, and Flux cost formula. Bundled mappings: Mekanism (crushing, enriching, smelting), Create (milling, crushing, cutting), Thermal (pulverizer, sawmill, smelter), Immersive Engineering (crusher, alloy), Farmer's Delight (cutting), vanilla (smelting, blasting, stonecutting). Packs add their own mapping files. Imported recipes are virtual (not written to disk), flagged in viewers, and can be disabled by config or tag.
2. **Viewer support.** JEI, EMI, REI plugins: machine categories, Flux cost shown, catalyst blocks, click-to-transfer. Isolated in optional modules loaded only if the viewer is present.
3. **Script hooks.** KubeJS (recipe schema + events for each machine) and CraftTweaker (`<recipetype:aeroflux:grinder>.addRecipe`) in optional classes loaded via reflection or mod-presence checks.
4. **Fluid/pipe interop.** Standard fluid, item and energy capabilities on every tank/machine/collector with sides configurable; Gas capability exposed when Mekanism is present. AeroFlux pipes also connect to other mods' blocks, and other mods' pipes connect to ours. Gas (Mekanism chemicals) and ME networks (Applied Energistics 2, via the ME and Dense ME pipes) are soft dependencies only. Flux fluids tagged `c:aero_flux` / `c:moon_flux` for other mods to filter. No hard dependencies anywhere.

5. **Info-overlay support (Jade, The One Probe, WTHIT).** WAILA's lineage is covered by Jade (the current successor, also loads WAILA-style plugins on most packs) and WTHIT (a direct WAILA fork). Each gets an optional plugin module, loaded only when the mod is present, with no hard dependency.
   - **Shared data layer:** a loader-neutral `OverlayInfo` provider in `common` builds a list of rows from a block entity: tier, fluid type and amount (with fill bar), machine progress and status, collector output rate and sky state, pipe type, throughput and per-face modes, upgrades installed, and redstone channel. Server sync uses each overlay's own data mechanism (Jade/WTHIT server data, TOP `IProbeInfoProvider`), reading the same `OverlayInfo`.
   - **Jade:** `IWailaPlugin` registering `IBlockComponentProvider` + `IServerDataProvider` for every AeroFlux block entity; fluid bar uses Jade's fluid element (shows the Flux texture), progress uses its progress bar. Config toggles registered with Jade's plugin config.
   - **The One Probe:** `IProbeInfoProvider` registered via the `InterModComms`/`registerProvider` path of each loader and version; fluid gauge via TOP's `ProgressStyle` with the Flux color, progress bar for machines, extended rows in Extended mode.
   - **WTHIT:** `IWailaPlugin` (WTHIT API) with equivalent component and data providers.
   - Compatibility notes: Jade, TOP and WTHIT have different release schedules per MC version/loader. Phase 0 lists which exist for each of the six builds; missing ones are skipped for that build.
   - When any of the three is present, the built-in look-at overlay defaults to off to avoid duplicate panels (config can force it on).

## 4. Phases

| # | Phase | Deliverable |
|---|---|---|
| 0 | Tooling spike | Verify each of the 6 targets exists and builds (esp. Forge on 26.1.2); empty mod loads on all 6 |
| 1 | Foundation | Multiloader skeleton, shared-core, CI, registries, tier config, lang/datagen pipeline |
| 2 | Flux fluids | Both fluids, buckets, fluid types, textures |
| 3 | Collectors | Tiered dual-mode collector, sky/weather/phase logic, tests |
| 4 | Storage and pipes | Tiered tanks with visual fill, 5 pipe types, pipe upgrades, wrench, per-face modes |
| 5 | Machine framework | Base BE, menus/screens, side config, upgrades, sync |
| 6 | Machines | Furnace, Grinder, Sawmill, Alloy Smelter, Infuser, Enchanter, Disenchanter, Plant Grower |
| 7 | Resources and recipes | Crystals, alloys, full recipe set, datagen |
| 8 | Advancements and tooltips | Full tree, guide tooltips, Jade/TOP |
| 9 | Compat layer | Recipe import, viewers, scripting, fluid interop, ME and Dense ME pipes (AE2) |
| 10 | Balance and polish | Numbers, art, sounds, particles, perf profiling |
| 11 | Test and release | GameTests, multiplayer test, Modrinth/CurseForge packaging per build |

Backport order: build everything on **1.21.1 NeoForge** first (Phase 1-8 vertical slice), then port to the other five. Phase 0 settles whether 26.1.2 comes second or last.

## 5. Risks
- 26.1.2 (new versioning scheme) and Forge on it: tooling or the loader may lag. Phase 0 decides if that build ships later.
- Six-way maintenance: mitigated by `shared-core` and generated data, but expect version-specific bugs.
- Art: ~40 block/item textures plus fluid animations. Plan to generate/paint in a consistent 16x palette (gold-white Aero, blue-violet Moon).
- Performance: conduit graph and collector sky checks must be cached and tick-throttled.

## 5b. Phase 0 findings (2026-11-01, from the official Maven repos)
| Target | Loader build available | Java |
|---|---|---|
| 26.1.2 NeoForge | `26.1.2.125` (four-part versioning) | 25 |
| 26.1.2 Forge | `26.1.2-64.1.5` | 25 |
| 1.21.1 NeoForge | `21.1.265` | 21 |
| 1.21.1 Forge | `1.21.1-52.1.20` | 21 |
| 1.20.1 NeoForge | `1.20.1-47.1.106` (artifact `net.neoforged:forge`, legacy fork) | 17 |
| 1.20.1 Forge | `1.20.1-47.4.28` | 17 |

- All six targets exist. Forge also has 26.2 and 26.3 lines, so 26.1.2 is not the newest MC; stay on 26.1.2 as requested.
- Toolchain on this PC: JDK 17 (`C:\Program Files\Microsoft\jdk-17.0.5.8-hotspot`), JDK 21 (`C:\Users\casey\tools\jdk-21`, Temurin 21.0.13, installed 2026-11-01), JDK 25. Gradle 9.8.2 at `C:\Users\casey\tools\gradle` (no PATH change; call `gradle.bat` directly, then use the wrapper per build).
- **Built (2026-11-01):** empty eroflux-1.0.0.jar builds on all six targets, each as a standalone Gradle project under ersions/<mc>/<loader>. The 1.20.1 NeoForge project needed `legacyForge { enable { neoForgeVersion = ... } }` (the plain `version` setting pulls MinecraftForge's Forge instead of NeoForge's fork).
- **Load-tested (2026-11-01):** all six jars start in a dev server and log "AeroFlux skeleton loaded". ForgeGradle 7 (Forge 1.21.1 and 26.1.2) needs a Java 8 toolchain for its run tasks; JDK 8 is at `C:\Users\casey\tools\jdk-8` for that only. Jars now build as `aeroflux-<loader>-<mc>-2026.11.1.jar`.

## 6. Final decisions
- Mod ID `aeroflux`, package `com.itmecasey.aeroflux`, license MIT.
- Six tiers (Copper, Iron, Gold, Diamond, Netherite, Fluxium). Tier 6 is crafted from Fluxsteel and both crystals.
- Art: Claude generates 16x textures and simple models; replaceable later. Consistent palette (gold-white Aero, blue-violet Moon).
- Plant Grower accepts modded crops and saplings through tags (`c:crops`, `minecraft:saplings`), with a config blacklist.
- Release to Modrinth and CurseForge, one file per loader/version build.

## 7. Cross-cutting requirements
1. **Protection and ownership.** Machines, conduit bundles, collectors and chunk loaders store an owner and optional team. Access modes: Public / Team / Owner-only (default Public, server config can force a default). Break/open/configure checks go through the loader's block-break and interact events so claim mods (FTB Chunks, Open Parties and Claims) can veto. Automation using fake players (quarries, deployers) is allowed unless the claim mod blocks it; a config list can deny specific fake-player names.
2. **Performance budget.** Max conduits per network (default 4,096), max networks ticking per level per tick, and per-machine tick throttling when idle (sleep until an input or neighbor change). Collector sky checks cached and rechecked every 20 ticks. `/aeroflux profile` reports per-subsystem tick time. Target: under 0.5 ms per tick on a base with 50 machines and 1,000 conduits.
3. **Save-data versioning.** Every block entity and saved-data file writes `schema_version`; loaders upgrade old versions on read. Worlds cannot move between MC versions, but updates within one version must never lose items, fluids or settings.
4. **Comparator, hopper and dropper support.** Tanks and machines output a comparator signal (fill level or progress, selectable in the side-config window), and accept hopper/dropper input only on faces set to Import or Both (and extraction on Export or Both).
5. **Enchanter and Disenchanter rules.** Data-driven (1.21+ enchantments are registry entries). Config: enchantment blacklist/whitelist, allow curses (default no for Enchanter), max level cap, treasure enchantments toggle, Moon Flux and lapis costs per level, and whether the Disenchanter returns XP. Works with modded enchantments through the registry/tag, no hard-coded IDs.
6. **Creative and polish content.** Creative tab (grouped by tier), Creative Tank and Creative Collector for testing, full loot tables and mining tags, sounds (hum, activate, finish), and particles for collectors (rising motes: yellow by day, blue-white at night), machines and chunk loaders.
7. **In-game guide.** An optional guidebook item with chapters on tiers, collectors, pipes and bundles, machines and chunk loading. Built as a lightweight own screen, with Patchouli entries generated when Patchouli is present. Unlocked through advancements.
8. **Build metadata.** Per-loader/version metadata generated from templates: `neoforge.mods.toml` (NeoForge), `mods.toml` or `neoforge.mods.toml` (Forge, per version), correct `pack.mcmeta` format for each MC version, dependency ranges for Forge/NeoForge/MC, optional dependencies marked.
9. **Testing.** JUnit on `shared-core`, GameTests per version (collector rates, conduit routing, side modes, chunk loader tickets, save round-trips), and a CI matrix building all six targets on each push.
10. **Docs and release pages.** README, CONTRIBUTING, CHANGELOG (Keep a Changelog), calendar versioning: jars are named `aeroflux-<loader>-<mc>-YYYY.M.N.jar` (for example `aeroflux-neoforge-1.21.1-2026.11.1.jar`), where `YYYY.M` is the release year and month (no zero padding) and `N` is the release counter within that month, starting at 1. The mod version in `gradle.properties` and the metadata is the `YYYY.M.N` part; the same number is used across all six builds of one release, icon and screenshot set, Modrinth and CurseForge descriptions, a wiki/docs page for config and dimension rules.
11. **Localization.** American English base (`en_us`), all text through lang keys (including HUD, tooltips, advancements, guide), datagen'd lang, and a documented process for community translations.
