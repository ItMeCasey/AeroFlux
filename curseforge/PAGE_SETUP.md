# CurseForge page setup: AeroFlux

Everything to fill in the Create Project form and the project page. Items marked CHECK are things I could not confirm from CurseForge's docs (their limits and category lists change), so check the form itself.

## Honest status first

AeroFlux is in early development: only the two fluids, their buckets, and Flux Dust exist in code today. [DESCRIPTION.md](DESCRIPTION.md) describes the full design and says so in a banner. Keep that banner until the features ship, and write the changelog to list exactly what each file contains. CurseForge moderation asks for accurate pages, so do not publish before a first file is ready to upload.

## Project fields

| Field | Value |
|---|---|
| Name | AeroFlux |
| CurseForge project ID | `1778368` (public; the API token is separate and lives in the `CURSEFORGE_TOKEN` environment variable, never in a file) |
| URL slug | `aeroflux` |
| Game / type | Minecraft Java Edition, Mods |
| Summary | Gather Aero Flux from the sun and Moon Flux from the moon, store it in tiered tanks, run it through pipe bundles, and power machines with it. |
| Logo | `branding/aeroflux_icon_400.png` (square, 400x400; CHECK that no larger size is required) |
| Description | [DESCRIPTION.md](DESCRIPTION.md) or [DESCRIPTION.html](DESCRIPTION.html) |
| License | MIT (the repo's `LICENSE`) |
| Source URL | REPLACE (the repo is not set up as a git remote yet) |
| Issues URL | REPLACE |

CurseForge's guidance says the summary should describe what the project does, and should not name the game, loader, or categories, which this one avoids.

## Categories (CHECK against the form's current list)

- Main: Technology, with a transport or processing subcategory if offered.
- Optional extras if allowed: Storage (tanks), Magic (enchanter and infuser).
- Do not add categories that don't fit; the docs warn moderators may send it back.

## Files to upload

Name each file as the plan says: `aeroflux-<loader>-<mc>-<version>.jar`, for example `aeroflux-neoforge-1.21.1-2026.11.1.jar`. One file per loader and Minecraft version.

| Minecraft | Loader | Java |
|---|---|---|
| 26.1.2 | NeoForge, Forge | 25 |
| 1.21.1 | NeoForge, Forge | 21 |
| 1.20.1 | NeoForge (legacy), Forge | 17 |

Release type: Alpha for early builds. Use [CHANGELOG_FIRST_RELEASE.md](CHANGELOG_FIRST_RELEASE.md) for the first file's changelog.

## Images

Prepared in [images](images):

| File | Use | Notes |
|---|---|---|
| `aeroflux_header.png` | Top of the description | Upload to the gallery or host it, then put the URL in the description |
| `aeroflux_icon_400.png` | Project logo | |
| `gallery_01_tier_ladder.png` | Gallery | Concept art |
| `gallery_02_collector.png` | Gallery | Concept art |
| `gallery_03_machines.png` | Gallery | Concept art |
| `gallery_04_pipe_layouts.png` | Gallery | Concept art |
| `gallery_05_pipe_glyphs.png` | Gallery | Concept art |

These are AI concept pictures, not in-game screenshots. Label them "Concept art" in the gallery captions. CurseForge recommends showing real in-game examples, so replace them with screenshots once the blocks render in the game.

## Gallery captions

1. Six collector tiers, copper to Fluxium (concept art)
2. Tier 1 copper collector with its dual sun and moon dish (concept art)
3. The Aero and Moon machines (concept art)
4. How pipes sit in one block, from one to six (concept art)
5. The seven pipe colors and glyphs (concept art)

## After the first file is live

- Add screenshots of the real blocks and replace the concept art.
- Add a short video or GIF of a collector filling a tank.
- Add badges for Modrinth and the source repository at the top of the description, kept modest in size.
- Mirror the page to Modrinth using the same text.
