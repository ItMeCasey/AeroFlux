## 2026.11.8

- **New:** AeroFlux for Minecraft 26.2 and 26.3, on both NeoForge and Forge (four new builds). They have the same machines, pipes, tanks, converters, chunk loaders and guidebook as the 26.1.2 builds.
- Optional mods work where the other mod has a build: Jade, The One Probe, JEI, REI, Applied Energistics 2 and WTHIT (26.2 only) on NeoForge, WTHIT on 26.2 Forge, and Barely Enough Items and Show Me What This Is on all four. KubeJS has no 26.2 or 26.3 build yet, so its hooks are left out of those builds.
- **1.20.1 (NeoForge and Forge):** machines, chunk loaders, the Conduit Tool and the Flux Configurator now ask claim mods before they act, as the newer builds do (the standard permission events, plus Open Parties and Claims directly; FTB Chunks is covered through the events only). A machine acts as the player who placed it. Turn it off with `protection.enabled` in the common config.
- **1.20.1:** `/aeroflux profile [seconds]` times the block entities AeroFlux ticks, and saved data carries a version number, so a newer build never silently misreads an older save.
- **WTHIT on Forge (26.1.2 and 26.2):** the Flux bar of a collector, tank or converter was labeled "Unknown Fluid". It now says Aero Flux, Moon Flux or Twilight Flux and draws the fluid.
