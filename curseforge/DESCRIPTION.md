<!-- Paste into CurseForge's description editor (use DESCRIPTION.html if the editor does not take Markdown).
     Replace each REPLACE_WITH_* link with the URL CurseForge gives you after you upload that image to the gallery:
     HEADER = images/aeroflux_header.png, G1 = gallery_01_tier_ladder.png, G2 = gallery_02_collector.png,
     G3 = gallery_03_machines.png, G4 = gallery_04_pipe_layouts.png, G5 = gallery_05_pipe_glyphs.png -->

![AeroFlux](REPLACE_WITH_HEADER_URL)

# Power your world with sunlight and moonlight

**AeroFlux** gives you two glowing liquids to gather: **Aero Flux** from the sun by day, and **Moon Flux** from the moon at night. Store them, send them down thin colorful pipes, and use them to run a whole workshop of machines. No coal, no cables, no new ores.

> **Early development.** AeroFlux is being built right now. This page shows where it's heading. The changelog lists exactly what's in each download, and early versions start with the two fluids and their buckets.

## Two lights, two jobs

![Collectors from copper to Fluxium](REPLACE_WITH_G1_URL)

- **Aero Flux** is a soft golden-yellow. It comes from the sun and powers the machines that make things: smelting, grinding, sawing, and alloying.
- **Moon Flux** is a cool blue-white. It comes from the moon and powers the magic machines: infusing, enchanting, and disenchanting, and it helps plants grow at night.

Both glow gently and can be carried in buckets.

## Flux Collectors

![A copper Flux Collector](REPLACE_WITH_G2_URL)

Place a collector under open sky and it does the rest. It gathers Aero Flux while the sun is up and Moon Flux once it's dark, with one block for both. Rain slows the sun, and a full moon is best for the moon.

There are six tiers, from **Copper** up to **Fluxium**, and each one collects faster and holds more.

## Flux Tanks

Glass tanks that show how full they are, with the glowing liquid rising inside. Bigger tiers hold far more, and a tank keeps its contents when you pick it up.

## Pipes

![Pipes in one block: one, two, three, four, five, and six](REPLACE_WITH_G4_URL)

Pipes are thin and **share a single block**, so one run can carry up to six different things side by side. Every kind has its own color and a small symbol, so you can tell them apart at a glance:

![The pipe colors and symbols](REPLACE_WITH_G5_URL)

- **Light gray:** fluids
- **Dark gray:** items
- **Red:** redstone signals
- **Orange:** energy from other mods
- **Gold:** gas from other mods
- **Purple:** Applied Energistics 2 network cables

Pipes line up neatly: one pipe sits in the middle, two sit side by side, three make a triangle, and so on up to six in a ring. When a run needs to split off in a new direction, place a plain dark gray **Pipe Node** and the pipes branch from it. You choose what each side of a pipe does: take in, send out, both, or stay shut.

## Machines

![The Flux machines](REPLACE_WITH_G3_URL)

All run on Flux.

- **Powered by sunlight:** Furnace, Grinder, Sawmill, Alloy Smelter
- **Powered by moonlight:** Infuser, Enchanter, Disenchanter
- **Either:** the **Plant Grower** uses sunlight by day and moonlight by night
- **Flux Chunk Loader:** keeps the area around it running while you're away, so your collectors never stop

## Handy tools

- **Flux Wrench:** turn machines, change how a pipe side works, or pick a block up with everything still inside it.
- **Flux Configurator:** copy one machine's settings and paste them onto others, even a whole area at once.
- **Conduit Tool:** edit, remove, swap, copy, and inspect your pipes.

## Easy to start

There are no new ores. You craft **Flux Dust** from glowstone dust, quartz, and redstone, then work up through crystals and Fluxsteel. Your first collector, tank, pipe, furnace, and wrench need no machines at all. An in-game guidebook walks you through it.

## Works with your other mods

AeroFlux plays well with others and needs none of them. If you have them, it connects to Mekanism gas, Applied Energistics 2, and the common recipe viewers (JEI, EMI, REI), and its machines can work with recipes from mods like Mekanism, Create, Thermal, Immersive Engineering, and Farmer's Delight.

## Versions

Minecraft **26.1.2**, **1.21.1**, and **1.20.1**, on both **NeoForge** and **Forge**.

## Questions

**Do I need other mods?** No. AeroFlux works on its own.

**Does it add new ores or world generation?** No. Everything is crafted.

**Can I use it in a modpack?** Yes, it's MIT licensed.

**Can I change how fast things are?** Yes. Speeds, capacities, and limits are all in the config.

## For modpack makers and server owners

- Per-dimension rules decide where collectors work. By default only the Overworld collects, and datapacks can add rules for other dimensions.
- The chunk loader has per-player and per-dimension limits, an admin command, and a global off switch.
- Machines, pipes, and loaders can be set to public, team, or owner-only and respect claim mods.
- Recipe import, KubeJS and CraftTweaker support, and the Jade, The One Probe, and WTHIT overlays are built in, with no hard dependencies.

Source and issues: REPLACE_WITH_REPOSITORY_URL
