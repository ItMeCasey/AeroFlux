# Scripting

With [ModJS](https://www.curseforge.com/minecraft/mc-mods/modjs) installed, server and startup scripts get a global named
`AeroFlux`. ModJS is optional: without it nothing is loaded and nothing fails. Client scripts get no `AeroFlux` global, because
there is nothing to read or listen to on the client.

The code is in `shared-core/.../core/script` (the API, the events, the recipe builder and the reflective bridge, with no
Minecraft classes) and `compat/modjs/AeroFluxScripts.java` in each build (the reads and the three event hooks). ModJS is found
by reflection through `ExtraBindings.register(BindingProvider)`, so AeroFlux compiles and runs without it.

Script rules, the same everywhere:

- A read that fails or finds nothing returns `null`; a failing read logs one plain line.
- A listener that throws is skipped and logged once per event until its scripts load again. Other listeners still run.
- Reads never change the game and never load a chunk.
- Listeners are kept per script type. Loading the server scripts again drops the old server listeners and nothing else.

## The `AeroFlux` global

### Information

| Call | Returns |
|---|---|
| `AeroFlux.version()` | The mod's version. |
| `AeroFlux.type()` | `startup` or `server`. |
| `AeroFlux.tiers()` | The tier ids, lowest first: `copper`, `iron`, `gold`, `diamond`, `netherite`, `fluxium`. |
| `AeroFlux.machines()` | Every machine id. |
| `AeroFlux.recipeMachines()` | The machines with recipes of their own: `grinder`, `sawmill`, `alloy_smelter`, `infuser`, `plant_grower`, `purifier`. |
| `AeroFlux.pipes()` | The pipe kinds: `fluid`, `item`, `redstone`, `energy`, `gas`, `me`, `dense_me`. |
| `AeroFlux.events()` | The event names. |

### Events

`AeroFlux.on(name, handler)`, or one of `onMachineFinished`, `onPipeModeChanged`, `onTierUpgraded`. They run on the server thread.
The handler gets an event object. A field that does not apply to the event is an empty string (`0` for the position).

| Event | When | Fields |
|---|---|---|
| `machineFinished` | A machine finishes a job. | `dimension`, `x`, `y`, `z`, `id` (the machine), `tier` |
| `pipeModeChanged` | A pipe face changes its mode, by the wrench, the Conduit Tool or the pipe screen. | `dimension`, `x`, `y`, `z`, `id` (the pipe), `face`, `mode` |
| `tierUpgraded` | An Upgrade Card raises a block one tier. | `dimension`, `x`, `y`, `z`, `id` (the block), `tier` (the new one), `player` |

### Reading the game

Server scripts and event handlers can read. Startup scripts run before there is a world, so their reads give `null`.
`dimension` is an id such as `minecraft:overworld`.

| Call | Returns |
|---|---|
| `AeroFlux.machine(dimension, x, y, z)` | The machine at the block: `id`, `tier`, `status` (`idle`, `running`, `no_flux`, `output_full`, `disabled`), `progress`, `totalTime`, `fluxPerTick`, `speedUpgrades`, `capacityUpgrades`, `redstoneMode`, `flux`. |
| `AeroFlux.flux(dimension, x, y, z)` | The Flux a machine, collector, tank, converter or chunk loader holds: `holder`, `type` (`aero`, `moon`, `twilight`, or empty), `amount`, `capacity`, in mB. |
| `AeroFlux.network(dimension, x, y, z, pipe)` | The network of one pipe kind through the bundle at the block: `pipe`, `bundles`, `sources`, `destinations`, `amountPerTick` (mB or FE; fluid, gas and energy), `itemsPerOperation`, `ticksPerOperation` (item), `truncated`. |
| `AeroFlux.player(name)` | An online player's `name`, `uuid`, `chunkLoaders` (running) and `chunkLoaderLimit` (0 for none). |

### Recipes

AeroFlux's machine recipes are ordinary data recipes, so ModJS's `event.custom(...)` adds them and `event.remove(...)` removes
them. `AeroFlux.recipe(machine)` builds the JSON for you in the shape this Minecraft version reads, with the same keys the
KubeJS and CraftTweaker support uses.

```js
ServerEvents.recipes(event => {
  event.custom(
    AeroFlux.recipe('grinder')
      .input('#c:ores/iron')
      .output('2x minecraft:raw_iron')
      .output('minecraft:gravel', 1, 0.1)
      .time(300)
      .build()
  ).id('mypack:grind_iron');

  // every Sawmill recipe
  event.remove({ type: 'aeroflux:sawmill' });
});
```

| Builder call | Sets |
|---|---|
| `input(ingredient)`, `input(ingredient, count)` | An ingredient: an item id, a `#tag`, or either with a count in front (`3x minecraft:iron_ingot`). |
| `output(item)`, `output(item, count)`, `output(item, count, chance)` | A result. A chance above 0 and up to 1; 1 is always. |
| `silkOutput(item, count, chance)` | What a Plant Grower with a Silk Touch Upgrade gives instead. Plant Grower only. |
| `time(ticks)` | Ticks per run (200 when left out). |
| `flux(mB)`, `fluxType('aero' / 'moon' / 'twilight')` | Extra Flux per run, and the Flux the recipe needs. |
| `water(mB)` | Water per run, for machines with a water tank. |
| `build()` | The recipe, ready for `event.custom(...)`. Needs one input and one output. |

A bad value (an unknown machine, a tag as a result, a chance of 2) stops the script at that line with a plain message; ModJS
reports it like any script error and the game carries on.

## Example

A server script that logs finished Grinder jobs, reads the Flux at a tank, and brings the first tier of a mod's ore into the Grinder:

```js
AeroFlux.onMachineFinished(e => {
  if (e.id === 'grinder') {
    const machine = AeroFlux.machine(e.dimension, e.x, e.y, e.z);
    console.log('Grinder at ' + e.x + ' ' + e.y + ' ' + e.z + ' finished; ' + machine.flux.amount + ' mB left');
  }
});

AeroFlux.onPipeModeChanged(e => console.log(e.id + ' pipe, ' + e.face + ' side: ' + e.mode));

AeroFlux.onTierUpgraded(e => console.log(e.player + ' raised ' + e.id + ' to ' + e.tier));

ServerEvents.recipes(event => {
  event.custom(AeroFlux.recipe('grinder').input('#c:ores/tin').output('2x modpack:raw_tin').build());
});
```

## What is not in the API

These would need new features in the mod, so they are not exposed: events for every item or fluid a pipe moves (the movers run
every tick and would need counting hooks), events for a collector filling or running dry (the collector keeps no edge state),
and writing to machines, pipes or tanks from scripts.
