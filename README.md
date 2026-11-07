# AeroFlux

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3%20%7C%2026.2%20%7C%2026.1.2%20%7C%201.21.1%20%7C%201.20.1-3C8527?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI%2BPGcgZmlsbD0iI2ZmZiI%2BPHBhdGggZD0iTTEyIDIuNiAxOS44IDcgMTIgMTEuNCA0LjIgN3oiLz48cGF0aCBkPSJNMy44IDguMSAxMS40IDEyLjR2OC40TDMuOCAxNi41eiIvPjxwYXRoIGQ9Ik0yMC4yIDguMSAxMi42IDEyLjR2OC40bDcuNi00LjN6Ii8%2BPC9nPjwvc3ZnPg%3D%3D)](#targets)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3%20%7C%2026.2%20%7C%2026.1.2%20%7C%201.21.1%20%7C%201.20.1-D7742D?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI%2BPHBhdGggZmlsbD0iI2ZmZiIgZD0iTTEgNmgyMnY0aC03bC0xIDRoLTZsLTEtNEgxek03IDE0aDEwdjJoMnYzSDV2LTNoMnoiLz48L3N2Zz4%3D)](#targets)
[![Forge](https://img.shields.io/badge/Forge-26.3%20%7C%2026.2%20%7C%2026.1.2%20%7C%201.21.1%20%7C%201.20.1-6A86B8?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI%2BPHBhdGggZmlsbD0iI2ZmZiIgZD0iTTEgNmgyMnY0aC03bC0xIDRoLTZsLTEtNEgxek03IDE0aDEwdjJoMnYzSDV2LTNoMnoiLz48L3N2Zz4%3D)](#targets)

A Minecraft mod where machines run on **Aero Flux**, gathered from the sun by day, and **Moon Flux**, gathered from the moon at night. Tiered collectors, tanks, conduit bundles, machines (furnace, grinder, sawmill, alloy smelter, infuser, enchanter, disenchanter, plant grower) and a Flux-powered chunk loader.

## Targets

| Minecraft | NeoForge | Forge | Java |
|---|---|---|---|
| 26.3 | `26.3.0.101` | `26.3-66.1.5` | 25 |
| 26.2 | `26.2.0.93` | `26.2-65.1.6` | 25 |
| 26.1.2 | `26.1.2.125` | `26.1.2-64.1.5` | 25 |
| 1.21.1 | `21.1.265` | `1.21.1-52.1.20` | 21 |
| 1.20.1 | `1.20.1-47.1.106` (legacy fork) | `1.20.1-47.4.28` | 17 |

## Layout

```
versions/
  mc26.3/{neoforge,forge}
  mc26.2/{neoforge,forge}
  mc26.1.2/{neoforge,forge}
  mc1.21.1/{neoforge,forge}
  mc1.20.1/{neoforge,forge}
```

Each folder is a standalone Gradle project. Rules that need no Minecraft classes live in [shared-core](shared-core) and are compiled into every build; run its unit tests with `cd shared-core && ./gradlew test`. CI (`.github/workflows/build.yml`) tests it and builds all ten targets.

## Building

Use the Gradle wrapper inside a version folder:

```
cd versions/mc1.21.1/neoforge
./gradlew build
```

The jar lands in `build/libs`, named `aeroflux-<loader>-<mc>-YYYY.M.N.jar` (for example `aeroflux-neoforge-1.21.1-2026.11.1.jar`). Versions are calendar based: year, month, and a release counter within that month. Each project sets its own Java toolchain, so JDK 17, 21 and 25 must be installed.

## Self-test

Each project has a development-only self-test in `devtest/SelfTest.java`. It does nothing unless the environment variable `AEROFLUX_SELFTEST` is set. With it set, `gradlew runServer` places conduit bundles, a pipe node, tanks and collectors in a forced chunk, checks joining, blocking, saving, shapes and drops, runs collectors through a day and a night, prints `AEROFLUX-SELFTEST PASS/FAIL` lines and stops the server. It checks game logic only, not how anything looks.

## License

MIT. See [LICENSE](LICENSE).
