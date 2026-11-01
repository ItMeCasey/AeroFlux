# AeroFlux: Rodin Prompt Sheet

Source: PLAN.md (nothing is built yet). Status: prompts only; no Rodin tool was reachable when this was written, so nothing has been generated.

## How I'm thinking about it

- Minecraft blocks are 16x16x16 cuboid JSON models, so a Rodin mesh is a **design reference**, not a ship-ready asset. Plan: generate, pick the best angle, then rebuild as a cuboid model plus a 16x texture. Tools and held items can use the mesh more directly (item models can be any mesh via a custom renderer, but keep them low-poly).
- **Palette is fixed by the plan.** Aero Flux: light yellow `#FFF2A8`, faint glow. Moon Flux: bluish-white `#D6E8FF`, faint glow. Tier frames: copper, iron, gold + amethyst, diamond, netherite, Fluxsteel + crystals. Every prompt below names these so the set reads as one family.
- **Style line on every prompt:** "chunky stylized voxel-friendly, hard edges, flat-shaded, game asset, centered, neutral grey background, no text". Rodin does best with one object per prompt.
- **Generate one tier, derive the rest.** Collector, tank, and conduit each get one Rodin base (tier 1 copper). Tiers 2-6 are material swaps on the same silhouette, which keeps the six-tier ladder readable and cheap.
- **Order:** collector first (the hero block, and its dual sun/moon look sets the tone), then tank, conduit lane, one machine, then the rest.

## Blocks

| Block | Prompt idea | Design reasoning |
|---|---|---|
| Collector (tiers 1-6) | A squat metal pedestal with a shallow glass dish on top, copper frame, a pale yellow glow in the dish on one half and a blue-white glow on the other half, small sun and crescent-moon engravings on the rim | Dual-mode by day and night, so the top must show both flux colors. Tier = frame material. |
| Flux Tank (tiers 1-5) | A tall glass cube in a corner-braced iron frame, half full of glowing pale yellow liquid, tick marks on the glass | Fill level is rendered in-world, so the frame must leave the glass face open. Stacks vertically, so top and bottom need plain connector plates. |
| Pipes (7 types) | Thin 4x4 px square pipes: light gray glass with water droplets (Fluid), dark gray plates with cargo dots (Item), red dust wire with sparks (Redstone), orange lightning zigzag (Energy), gold-yellow swirling wisps (Gas), purple fiber lattice (ME), dark purple ribbed (Dense ME) | Final design: see PLAN.md "Pipe size and layout". References: `concept/pipe_textures_six.png`, `concept/pipe_layouts_1_to_6.png`. |
| Conduit Bundle | The pipes in one block, centered as a group: 1 centered, 2 side by side, 3 triangle, 4 square, 5 and 6 a ring, in fixed order Fluid, Item, Redstone, Energy, Gas, ME | Same set of types lines up block to block with no extra block. |
| Pipe Node | Small metal junction cube with a round port on each face, pipes splitting off inside | Needed only where the set of pipe types changes. The first concepts (`concept/pipe_node_block.png`, `concept/pipe_run_and_node.png`) are muddled; draw by hand. |
| Flux Furnace | A stone furnace body with a copper trim and a glowing yellow slot instead of fire | Aero machine, so yellow accents. Starting machine, so close to vanilla furnace proportions. |
| Flux Grinder | A metal box with two visible interlocking gear wheels behind a grate, yellow glow | GUI uses a gear progress icon, so the block should show gears. |
| Flux Sawmill | A low workbench with a circular saw blade in a slot, yellow glow | Matches the sawblade progress icon. |
| Alloy Smelter | A heavy squat crucible furnace with three input ports on top, yellow glow | 2-3 inputs, so show the ports. |
| Flux Infuser | A pedestal with a floating swirl of blue-white light above a bowl | Moon machine, so blue-white accents. Matches the swirl icon. |
| Flux Enchanter | A lectern-style stand with an open book and floating blue-white glyphs | Moon machine, with book and lapis inputs. |
| Flux Disenchanter | Like the Enchanter but the book is closed with chains and the glyphs are fading | Reads as the opposite of the Enchanter at a glance. |
| Plant Grower | A glass-topped planter box with a small sprout, sun panel on one side, moon panel on the other | Aero by day, Moon by night, so both panels show. |
| Flux Chunk Loader | A tall obelisk with a rotating ring around the top, both yellow and blue-white light | Accepts either flux, so use both colors. Ring doubles as the "active" animation. |
| Creative Tank and Creative Collector | The tier 6 model with a rainbow-shifting trim | Testing blocks, so they should look obviously different from survival ones. |

## Fluids and buckets

| Item | Prompt idea |
|---|---|
| Aero Flux Bucket | An iron bucket filled with softly glowing pale yellow liquid, small sun mark on the side |
| Moon Flux Bucket | An iron bucket filled with softly glowing blue-white liquid, small crescent mark on the side |

The fluids themselves are textures (animated still and flow sprites), not Rodin work.

## Items

| Item | Prompt idea |
|---|---|
| Flux Dust | A small pile of fine glittering dust, pale yellow with white sparkles |
| Aero Crystal | A faceted quartz-like crystal, pale yellow glow inside |
| Moon Crystal | A faceted amethyst-like crystal, blue-white glow inside |
| Fluxsteel Ingot | A dark steel ingot with a thin yellow and blue-white vein through it |
| Aerogold Ingot | A gold ingot with a pale yellow glow in its seams |
| Upgrades (Speed, Capacity, Filter, Priority, Efficiency, Fortune, Silk) | A small square circuit chip with one icon each (lightning bolt, stacked boxes, funnel, up arrow, leaf, gem, feather) |
| Guidebook | A thick book with a tier-colored cover and a sun and moon emblem |

## Tools

| Tool | Prompt idea | Design reasoning |
|---|---|---|
| Flux Wrench | A chunky wrench with an adjustable jaw, Fluxsteel handle, small yellow gem at the pivot | Doubles as rotate and dismantle, so it should look sturdy. |
| Flux Configurator | A handheld tablet-like device with a small screen, a copy and a paste button, a blue-white gem | Copy and paste with presets, so a "device" look separates it from the wrench. |
| Conduit Tool | A pipe-cutter style tool with two handles and a clamp jaw, five small colored dots on the handle | The dots echo the five conduit colors. |

## Next step

Once a Rodin route is available, run the collector, tank, and conduit prompts first and check the set reads as one family before generating the rest.
