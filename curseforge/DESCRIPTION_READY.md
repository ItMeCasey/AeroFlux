![AeroFlux](https://media.forgecdn.net/attachments/description/1778368/description_cd7115a7-a153-4c83-9912-b8b161fe7eaf.png)

# Power your world with sunlight and moonlight

AeroFlux adds two glowing fluids to Minecraft. Aero Flux comes from the sun and Moon Flux comes from the moon. Put a collector out under the sky, let it fill a tank, and use what it gathers to run a whole workshop of machines: they smelt, grind, saw, enchant, craft, sift, breed, repair and grow things for you. There's nothing to mine and nothing to burn.

## The two fluids

**Aero Flux** is a warm golden yellow and gathers while the sun is up. It runs the machines that make and shape things.

**Moon Flux** is a pale blue-white and gathers at night. It runs the magic machines. Blend the two in a Blender and you get **Twilight Flux** for the top recipes.

All three glow a little in the dark, and you can carry them in buckets.

## Collectors and tanks

A collector works on its own. Set it somewhere with open sky and it gathers sun by day and moon by night, all in one block. Rain slows the sun down, and a full moon gives you the most moonlight. Its screen shows what it holds, how fast it gathers, and what is blocking the sky if something is.

Tanks are made of glass, so you can see the liquid rising inside, and they take **any fluid**, one at a time, not only Flux. The screen draws it with its own texture. A tank keeps its contents when you pick it up and move it, and comparators read how full it is.

Everything comes in **six tiers**, from Copper up through Iron, Gold, Diamond and Netherite to Fluxium. Each tier is faster and holds more, and an **Upgrade Card** raises a block you already placed by one tier without losing what's in it. Every size and speed is in the config.

## Machines

Every machine runs on Flux from its own tank, comes in all six tiers, and has the same screen: a Flux gauge, a progress arrow, a status line that tells you why it stopped, and a side panel where you choose what each face takes in or puts out.

**Sun machines (Aero Flux)**
- **Furnace:** smelts like a furnace, without the fuel.
- **Grinder:** grinds ores and other items down; bones become bone meal.
- **Sawmill:** cuts logs into six boards, and sometimes drops Sawdust.
- **Alloy Smelter:** melts two or three ingredients together into one.
- **Crafter:** an automatic crafting table. Lay the recipe in its grid and feed it with pipes.
- **Purifier:** ore, a little water and Flux into three times the raw metal.
- **Sifter:** a powered sieve for **Ex Deorum** and **Ex Nihilo Sequentia** recipes; the mesh in its slot decides the drops.
- **Blender:** mixes Aero and Moon Flux into Twilight Flux and fills buckets with it.
- **Sky Controller:** day, night and rain at the push of a button.
- **Animal Breeder:** feeds the animals around it so they breed.

**Moon machines (Moon Flux)**
- **Enchanter:** puts the enchantments of a book onto your gear for lapis and Moon Flux.
- **Disenchanter:** moves enchantments from an item onto a book and keeps the item.
- **Repairer:** mends a tool with Bottles o' Enchanting and keeps its enchantments.
- **Moon Spawner:** rolls a mob's loot from a spawn egg with no mob in the world.
- **Magnet:** pulls dropped items in from around it, with a whitelist or blacklist filter.

**Either one**
- **Infuser:** soaks an item and a pinch of Flux Dust in Flux to make something new. Sawdust becomes Flux Dust on Aero Flux alone; crystals and crying obsidian take Moon Flux.
- **Plant Grower:** grows crops and saplings from seeds, with a soil block in its second slot (farmland for crops, dirt for the rest). It grows any mod's crop. Mystical Agriculture's essence farmland gives extra seeds, and data packs can add soils with their own bonuses.

**Also**
- **Sun Lamp and Moon Lamp:** keep monsters away and help plants grow.
- **Flux Chunk Loader:** keeps the area around it loaded while you're away, so your collectors keep working.
- **Flux Converters:** turn Flux into Forge Energy, or into Applied Energistics 2 power, and on 1.21.1 NeoForge into Create stress.
- **Creative Flux Tank:** never runs dry, for creative mode.

## Upgrades

Drop upgrades into the slots on a machine's screen, or onto pipes.

- **Speed** and **Capacity** for machines and pipes.
- **Efficiency:** the machine burns less Flux while it works.
- **Fortune:** the Grinder and Sifter give more.
- **Silk Touch:** the Plant Grower gives leaves, melons and mushroom blocks whole.
- **Purity:** the Purifier gives an extra raw metal.
- **Filter** and **Priority** for pipe connections.

**Auto Import** and **Auto Export** on a machine's side pull items in from neighbors and push products out, and the Plant Grower can refill its own seeds. A redstone button picks when a machine runs.

## Pipes

The pipes are thin and several of them share one block, so a single run can carry up to six different things side by side. Each kind has its own color and a small symbol:

- Light gray: fluids
- Dark gray: items
- Red: redstone signals
- Orange: energy
- Gold: gas from Mekanism
- Purple: Applied Energistics 2 network cables

One pipe sits in the middle of the block. Two sit side by side, three make a triangle, and it keeps going up to a ring of six. When you want a run to branch off, drop in a plain dark gray Pipe Node. On each side of a pipe you pick what it does: take in, send out, both, or stay closed, and how it shares between several targets. Right-click a connector for its settings.

## Tools

- **Flux Wrench:** rotate machines, change what a pipe side does, or pick a block up with everything still inside.
- **Flux Configurator:** copy one machine's settings and paste them onto others, even a whole area at once. Eight named presets keep your favorites, and the last paste can be undone for a minute.
- **Conduit Tool:** edit, remove, copy, paste and inspect your pipes, remove a whole run at once, and see what a network can carry.

## Getting started

There are no new ores. Chop a tree and you'll shake loose some Sawdust; two make a Flux Dust. Work up through crystals to Fluxsteel. Your first collector, tank, pipe, furnace, Infuser and wrench don't need any machine to make. The in-game **guidebook** has 23 pages in six chapters, a contents page, and buttons that open the wiki.

## Other mods

AeroFlux doesn't need any other mod. If you have them, it works with:

- **Recipe imports:** the machines can run other mods' processing recipes as the game loaded them: Create, Mekanism, Immersive Engineering, Thermal, Ender IO, Occultism, Oritech, Silent Gear, Mystical Agriculture, Ars Nouveau, Botany Pots, Actually Additions, Modern Industrialization, Industrial Foregoing and Crossroads.
- **Mekanism** gas pipes (on the versions Mekanism exists for), **Applied Energistics 2** channels and power, and **Create** stress.
- **Recipe viewers:** BEI, JEI, EMI and REI list the machines' recipes, including imported ones.
- **Overlays:** Jade, The One Probe, WTHIT and our own **Show Me What This Is** show Flux levels and machine status.
- **Scripting:** KubeJS and CraftTweaker can add, remove and change machine recipes.

**Tested in a game client:** Barely Enough Items and Show Me What This Is on all six builds, and Jade on 26.1.2 NeoForge, 1.21.1 NeoForge and both 1.20.1 builds. JEI, REI, EMI, The One Probe and WTHIT are built in but not yet tested in a client.

## Versions

Minecraft 26.1.2, 1.21.1 and 1.20.1, on both NeoForge and Forge. Which optional mods work depends on which of them exist for that version and loader.

## Questions

**Does it add ores or world generation?** No. Everything is crafted.

**Can I put it in a modpack?** Yes. It's MIT licensed.

**Can I change the speeds?** Yes. Speeds, capacities, enchanting rules and limits are all in the config, and tank and buffer sizes are sent from the server to the players.

**Can I add my own machine recipes?** Yes, with a data pack, KubeJS or CraftTweaker.

## For server owners and pack makers

- Collectors only work in the Overworld by default. Datapacks can add rules for other dimensions.
- The chunk loader has per-player and per-dimension limits, an admin command and a global off switch.
- On 1.21.1 and 26.1.2, machines and loaders remember who placed them and respect claim mods, there is a `/aeroflux profile` command that shows what costs the most tick time, and saves carry a version number so future updates can convert them.
