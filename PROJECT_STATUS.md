# Project Status
<!-- status-format: 1 -->
Updated: 2026-11-01
Summary: AeroFlux (solar/lunar Flux fluid machines mod) is planned; all six projects build warning-free with shared-core, registries, tier config and datagen; Aero/Moon Flux fluids and buckets are in on all six targets; collectors and tanks (no fill display yet) are in on all six targets; a conduit bundle prototype (no networks yet) is in on all six targets.
Spend: none

## Planning
- [x] Gather requirements and decisions (w:2)
- [x] Write PLAN.md (w:2)

## Phase 0-1: Tooling and foundation
- [x] Verify all 6 loader/version targets build (w:3)
- [~] Multiloader skeleton, shared-core, CI (w:4)
- [x] Registries, tier config, datagen pipeline (w:3)

## HUDs and GUIs
- [ ] Shared widget library (gauge, progress, status, tabs) (w:4)
- [ ] Machine, collector and tank screens (w:4)
- [ ] In-world look-at HUD and tooltips (w:3)

## Phase 2-4: Fluids, collectors, storage
- [x] Aero Flux and Moon Flux fluids and buckets (w:3)
- [x] Tiered dual-mode collectors (w:4)
- [~] Tiered tanks with visual fill level (w:4)
- [ ] Per-dimension rule map (w:2)
- [ ] Conduit bundle prototype (4 px pipes, centered layouts, fixed order, shapes, rendering, wrench) (w:5)
- [ ] Pipe Node block (branching where the pipe set changes) (w:3)
- [ ] Pipe framework, per-face modes, wrench (w:5)
- [ ] Fluid and Energy pipes (w:3)
- [ ] Item pipes (w:4)
- [ ] Redstone pipes (w:3)
- [ ] Gas pipes (Mekanism soft dependency) (w:3)
- [ ] Pipe upgrades (speed, capacity, filter, priority) (w:3)
- [ ] Machine side-config HUD window (w:3)
- [~] Flux Wrench (w:2)
- [ ] Flux Configurator (copy, paste, area paste, presets, undo) (w:4)
- [ ] Conduit Tool (edit, remove, replace tier, copy/paste, inspect) (w:4)

## Phase 5-7: Machines and resources
- [ ] Machine base framework, menus, upgrades (w:5)
- [ ] Flux Furnace, Grinder, Sawmill (w:4)
- [ ] Alloy Smelter and Infuser (w:3)
- [ ] Enchanter and Disenchanter (w:4)
- [ ] Plant Grower (w:3)
- [ ] Flux Chunk Loader (w:3)
- [ ] Resources and full recipe set (w:3)

## Phase 8-9: Advancements and compat
- [ ] Advancement tree and tooltips (w:3)
- [ ] Recipe import compat layer (w:4)
- [ ] JEI/EMI/REI plugins (w:3)
- [ ] KubeJS/CraftTweaker hooks (w:2)
- [ ] Fluid/pipe interop (w:2)
- [ ] ME and Dense ME pipes (Applied Energistics 2 soft dependency) (w:3)
- [ ] Jade, The One Probe and WTHIT plugins (w:3)

## Cross-cutting
- [ ] Ownership and claim-mod protection (w:3)
- [ ] Performance budget and profiler command (w:3)
- [ ] Save-data versioning (w:2)
- [ ] Comparator, hopper and dropper support (w:2)
- [ ] Enchanter/Disenchanter config rules (w:2)
- [ ] Creative tab, creative blocks, sounds, particles (w:3)
- [ ] In-game guidebook (w:3)
- [ ] Per-loader build metadata templates (w:2)
- [ ] Localization pipeline (w:1)

## Phase 10-11: Port, polish, release
- [ ] Port vertical slice to remaining 5 builds (w:5)
- [ ] Art, sounds, balance (w:4)
- [ ] GameTests, unit tests and CI matrix (w:3)
- [~] Docs, changelog and release pages (w:2)
- [ ] Release packaging (w:2)
