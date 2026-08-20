# Storm Weapon

Storm Weapon is an original Forge mod for Minecraft Java Edition 26.2. Version 0.1.0 is the
weather-first prototype: it implements the artificial regional storm itself before the missile
and launcher gameplay is added.

The storm is not a wrapper around `/weather thunder`. It is a server-authoritative lifecycle with
a regional client bridge to Minecraft's stock thunderstorm sky tint, world-anchored wind-driven
rain, visibility fog, deterministic intra-cloud flashes, procedural strike channels, screen
exposure flashes, and sparse server-authoritative damaging lightning. The bridge does not change
server weather; custom rain, wind and lightning remain controlled by Storm Weapon.

## Requirements

- Minecraft Java Edition 26.2
- Minecraft Forge 65.1.x (developed and tested with 65.1.1)
- Java 25 for development

## Installation

1. Install Forge 65.1.x for Minecraft 26.2.
2. Copy `stormweapon-0.1.0.jar` into the instance `mods` directory.
3. Start Minecraft with that Forge profile.

No VulkanMod, Iris, OptiFine, or Sodium-family dependency is required.

## Vulkan

Minecraft 26.2 includes an experimental native Vulkan backend. Open:

`Options -> Video Settings -> Graphics API -> Prefer Vulkan (Experimental)`

Restart Minecraft after changing the backend. Storm Weapon submits geometry only through
Minecraft/Blaze3D render types and frame-graph APIs; it contains no direct OpenGL calls. It also
supports `Prefer OpenGL`. The active backend is logged as either:

```text
StormWeapon graphics backend: Vulkan
StormWeapon graphics backend: OpenGL
```

## Gameplay in 0.1.0

This release is intentionally weather-first. Use an operator/cheats-enabled world and trigger the
storm with commands. The launcher, missile item, and missile flight sequence are the next project
milestone and are not represented by placeholder blocks or sprites in this build.

The default lifecycle lasts 330 seconds:

`SEEDING -> CLOUD_BUILDUP -> WIND_RISING -> HEAVY_RAIN -> SUPERCELL -> PEAK_STORM -> DECAY -> CLEARING`

The default core radius is 768 blocks with a 256-block transition zone. Rain exists in deterministic
world cells while only nearby cells are submitted for each observer; the server never scans the
full storm area. The stock thunder sky tint is also multiplied by the observer's regional influence.

## Commands

All commands require operator/cheats permission.

```text
/stormweapon storm start
/stormweapon storm start <x> <z>
/stormweapon storm stop
/stormweapon storm phase <phase>
/stormweapon debug
```

For immediate visual testing:

```text
/stormweapon storm start
/stormweapon storm phase peak_storm
/stormweapon debug
```

The debug HUD shows phase timing, center and radius, local influence, cloud/rain/lightning
envelopes, wind direction and rain tilt, cloud/rain budgets, active lightning, and graphics backend.

## Configuration

Common/server configuration (`stormweapon-common.toml`):

- `stormRadius`, `stormTransitionWidth`
- Per-phase durations under `phaseSeconds`
- `physicalLightningMinSeconds`, `physicalLightningMaxSeconds`
- `lightningDamageMultiplier`
- `stormFireEnabled` (default `false`)

Client configuration (`stormweapon-client.toml`):

- `stormQuality`: `LOW`, `MEDIUM`, `HIGH`, or `ULTRA`
- `cloudQuality` (reserved for the dormant experimental custom-cloud renderer)
- `rainDensity`
- `cameraShakeIntensity` (reserved for the missile milestone)
- `lightningFlashIntensity`
- `stormFog`

## Building

```bash
./gradlew build -Djava.net.useSystemProxies=false
```

The output JAR is created under `build/libs/`. The extra Java networking property is only needed
on environments where an OS proxy interferes with Gradle TLS; omit it otherwise.

On macOS, the development client run includes `-XstartOnFirstThread`. Forge 65.1.1 userdev may
mis-handle percent-encoded non-ASCII project paths; use an ASCII-only temporary checkout for
`runClient`/`runGameTestServer` if that Forge launcher issue appears. Normal mod installation and
`build` are unaffected.

## Original assets

All code and weather textures in this repository are original. The mod does not contain or
redistribute assets, audio, shaders, logos, or code from other games.
