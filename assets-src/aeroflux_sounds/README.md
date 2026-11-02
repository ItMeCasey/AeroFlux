# AeroFlux machine sounds

Synthesized (no samples, no paid service) by `assets-src/tools/gen_sounds.py`; rerun it to regenerate.
Mono Ogg Vorbis, 44.1 kHz. Drop `assets/aeroflux/` into each version's resources and merge `sounds.json`
and the `subtitles.*` keys from `lang_en_us_sounds.json` into `lang/en_us.json`.

| Sound event | File | Use |
|---|---|---|
| `aeroflux:machine.furnace.loop` | `machine/furnace_loop.ogg` | 4 s seamless loop while the Flux Furnace works |
| `aeroflux:machine.grinder.loop` | `grinder_loop.ogg` | Flux Grinder |
| `aeroflux:machine.smelter.loop` | `smelter_loop.ogg` | Alloy Smelter |
| `aeroflux:machine.sawmill.loop` | `sawmill_loop.ogg` | Flux Sawmill |
| `aeroflux:machine.infuser.loop` | `infuser_loop.ogg` | Flux Infuser |
| `aeroflux:machine.enchanter.loop` | `enchanter_loop.ogg` | Flux Enchanter |
| `aeroflux:machine.disenchanter.loop` | `disenchanter_loop.ogg` | Flux Disenchanter |
| `aeroflux:machine.grower.loop` | `grower_loop.ogg` | Plant Grower |
| `aeroflux:machine.start` | `start.ogg` | one-shot when a machine begins a job (rising sweep) |
| `aeroflux:machine.stop` | `stop.ogg` | one-shot when it stops or loses power (falling sweep) |
| `aeroflux:machine.done` | `done.ogg` | one-shot chime when an output is finished |

Wiring notes (Java)
- Register the 11 `SoundEvent`s with ids above. Loops are not looped by the engine: play them with a looping,
  stoppable sound instance tied to the block entity (`looping = true`, `delay = 0`, volume about 0.5, attenuation
  already set in sounds.json), start it when the `lit` property turns true and stop it when it turns false or the
  block is removed or unloaded. Only one instance per machine position.
- Play `start` and `stop` on the lit transition, and `done` when an output item is produced.
- Volume knob: add a config value for machine loop volume (some players will want them quieter).
