# Client checks and how to repeat them

Written 2026-11-09 after the status closeout. Every claim of "seen in a real client" in `PROJECT_STATUS.md` comes from one of the dev tours below. A tour opens a fresh creative world, drives the game from inside it (its own handlers, no desktop cursor or keyboard), saves frames into `run/screenshots` as `aeroflux_<name>.png`, logs `AEROFLUX-SHOTS` lines and quits. The frames in `docs/frames` are copies, named by what they show.

Launch a tour only through `launch-isolated.ps1`, on a desktop name of your own (`-Desktop AFTest`): other sessions call `-Kill` on the default `MCTest` desktop and that ends your client. Example:

```
powershell -File C:\Users\casey\.claude\tools\launch-isolated.ps1 -Run -Desktop AFTest -WorkDir <build folder> -Command "set AEROFLUX_SHOTS=<mode>&& .\gradlew.bat runClient --no-daemon <extra arguments>"
```

Use JDK 21 (`JAVA_HOME=C:\Users\casey\tools\jdk-21`) when you run Gradle from a shell.

| `AEROFLUX_SHOTS` | What it does | Where |
|---|---|---|
| `all` | scenes, machine and pipe screens, every GUI, then the status checks | 26.1.2 NeoForge |
| `tour` (any other value) | scenes and screens | 1.21.1 NeoForge, 1.20.1 (both loaders) |
| `pipes` | machine side-config panel and the two pipe screens | 1.21.1 Forge |
| `screens` | the machine and pipe screens | 26.1.2 and newer |
| `status` | sounds (every AeroFlux sound the engine is asked to play is logged), the Conduit Tool in every mode (results read back from the server), the creative tab | 26.1.2 NeoForge |
| `guis` | the screen of every machine, converter, chunk loader, collector and tank | all ten |
| `smwti` | looks at a Copper Furnace and a Copper Flux Collector: shows whichever look-at overlay is in the run (Jade, The One Probe, WTHIT, SMWTI) | all builds |
| `bei` | Barely Enough Items browser and recipe popups | all builds |
| `jei`, `emi`, `rei` | opens that viewer's recipe page for the Alloy Smelter and the Infuser | JEI on five builds, EMI on two, REI on four (see `docs/frames`) |

Extra Gradle arguments: `-PbeiCheck` leaves the other dev mods out of the run, `-PdevOnly=jade` (comma list) puts just those back, and `-PdevOnly=rei` adds REI, which replaces JEI and is not in any other run.

## Findings from this round

- WTHIT on Forge 26.1.2 and 26.2 printed "Unknown Fluid" for Flux. The Forge client plugin now describes the three fluxes to WTHIT (`FluidData.describeFluid`), and the overlay reads "Aero Flux" with the fluid sprite.
- On 26.2 NeoForge, the dev client with all dev mods loaded (JEI, Jade, The One Probe, WTHIT, AE2, GuideME) crashes in the GUI render pass ("Scissor size must be >0, was 0x0") when the Fluxium collector screen is open in an 854x480 window. With `-PbeiCheck` the same tour runs clean, so the crash comes from the overlay mods together, not from AeroFlux. Run 26.2 tours with `-PbeiCheck` plus the one mod you want.
- EMI logs three dev-mode warnings about untranslated `#c:aero_flux`, `#c:moon_flux` and `#c:twilight_flux` fluid tags. They are harmless; adding `tag.fluid.c.*` translations would silence them.
- The Conduit Tool's Remove Run needs the same stack in hand for both clicks, and changing the tool's mode drops a half-made run. The tour only changes the mode when it differs.
