# Climate Weapon

[中文说明](README_zh.md)

Climate Weapon (mod id `stormweapon`) is an original Forge mod for Minecraft Java Edition 26.2.
Version 0.1.7 is a weaponized-weather vertical slice: a deployable launcher fires guided missiles
that detonate at high altitude and trigger a dimension-wide extreme-weather event, on top of the
original regional storm system the project started from.

The weather is not a wrapper around `/weather thunder`. It is a server-authoritative lifecycle with
a client bridge to Minecraft's stock thunderstorm sky tint, world-anchored wind-driven rain,
visibility fog, deterministic intra-cloud flashes, procedural strike channels, screen exposure
flashes, and sparse server-authoritative damaging lightning. The bridge does not change server
weather; custom rain, wind and lightning remain controlled by Climate Weapon.

## Requirements

- Minecraft Java Edition 26.2
- Minecraft Forge 65.1.x (developed and tested with 65.1.1)
- Java 25 for development

## Installation

1. Install Forge 65.1.x for Minecraft 26.2.
2. Copy `climateweapon-0.1.7.jar` into the instance `mods` directory.
3. Start Minecraft with that Forge profile.

No VulkanMod, Iris, OptiFine, or Sodium-family dependency is required.

## Vulkan

Minecraft 26.2 includes an experimental native Vulkan backend. Open:

`Options -> Video Settings -> Graphics API -> Prefer Vulkan (Experimental)`

Restart Minecraft after changing the backend. Climate Weapon submits geometry only through
Minecraft/Blaze3D render types and frame-graph APIs; it contains no direct OpenGL calls. It also
supports `Prefer OpenGL`. The active backend is logged as either:

```text
StormWeapon graphics backend: Vulkan
StormWeapon graphics backend: OpenGL
```

## Gameplay in 0.1.7

Use an operator/cheats-enabled world; there is no survival crafting yet. Items are reachable from
the creative inventory (`Climate Weapon` item group) or via `/give`:

```mcfunction
/give @s stormweapon:weather_missile_launcher
/give @s stormweapon:weather_missile
/give @s stormweapon:storm_controller
/give @s stormweapon:signal_connector
```

Five missile types are available: `Thunder Missile`, `Fog Missile`, `Meteor Missile` (high
destruction), `Blizzard Missile`, and `Cherry Blossom Missile`.

### Launching a missile

1. Place a `Weather Missile Launcher`.
2. Hold a missile item and right-click the launcher to load it; the missile is consumed.
3. Right-click the launcher empty-handed to open the launch control screen.
4. Pick one of three target presets and enter X/Z coordinates, then save it.
5. Set a countdown between 3 and 30 seconds (default 5).
6. Click `ARM / LAUNCH` once to arm, click again to start the countdown. Chat shows a `T-N` warning
   every second down to `T-0`.
7. The server spawns a real missile entity that eases off the rail, accelerates, and guides toward
   the target, detonating at `Y=300` above the target X/Z rather than impacting the ground.

Use the `Signal Connector` to bind a launcher to a button or lever for remote/redstone triggering.
Only one dimension-wide weather event can be active at a time, so a launcher refuses to fire while
one is already running.

### Detonation weather sequence

```text
ATMOSPHERIC_WAVE (10 s ramp, 4 s visible wave, 2000-block radius)
    -> PEAK_STORM (held for 300 s after detonation)
    -> DECAY (45 s)
    -> CLEARING (45 s)
    -> CLEAR
```

The active effect is dimension-wide, not regional.

### Blizzard and Cherry Blossom payloads

- **Blizzard Missile**: forces blizzard weather in every biome for 5 minutes and lets vanilla snow
  layers accumulate normally regardless of biome temperature. Players caught outdoors take
  periodic freezing damage, lose movement speed by 1% every second while exposed (stacking up to
  100%), and slowly lose max health the longer they stay exposed. Shelter is a strict indoor check
  (standing under a tree does not count); stepping indoors lets any accumulated debuff fade back to
  normal over time instead of clearing instantly.
- **Cherry Blossom Missile**: no negative effects. For 5 minutes it showers every biome with
  vanilla cherry-leaf petals that accumulate on the ground, while players caught in the open
  gradually clear their harmful effects and regenerate 2 hearts every second.
- Both the blizzard/fog debuffs and the cherry blossom buff show up as their own status effect
  icons in the effect HUD and inventory screen, exactly like a vanilla potion effect. None of the
  weather debuffs apply to players in creative or spectator mode.

### Plain command weather

For quicker regional testing without a missile, the storm lifecycle can still be driven directly by
command:

`SEEDING -> CLOUD_BUILDUP -> WIND_RISING -> HEAVY_RAIN -> SUPERCELL -> PEAK_STORM -> DECAY -> CLEARING`

## Commands

All commands require operator/cheats permission.

```text
/climateweapon storm start
/climateweapon storm start <x> <z>
/climateweapon storm stop
/climateweapon storm phase <phase>
/climateweapon status
/climateweapon launcher preset <1..3> <x> <z>
/climateweapon missile launch <x> <z>
/climateweapon debug
```

For immediate visual testing:

```mcfunction
/climateweapon missile launch ~200 ~200
/climateweapon status
/climateweapon debug
```

The debug HUD shows phase timing, detonation center, local influence, cloud/rain/lightning
envelopes, wind direction and rain tilt, cloud/rain budgets, active lightning, and graphics backend.

## Configuration

Common/server configuration (`stormweapon-common.toml`):

- `weapon.atmosphericWaveSeconds`, `weapon.atmosphericWaveRadius`, `weapon.effectRampSeconds`,
  `weapon.activeSeconds`
- `launcher.cooldownSeconds`
- Per-phase durations under `phaseSeconds` (used by the plain command storm)
- `physicalLightningMinSeconds`, `physicalLightningMaxSeconds`
- `lightningDamageMultiplier`
- `stormFireEnabled` (default `false`)

Client configuration (`stormweapon-client.toml`), also editable in-game via the `Storm Controller`
item's settings screen:

- `stormQuality`: `LOW`, `MEDIUM`, `HIGH`, or `ULTRA`
- `cloudQuality` (reserved for the dormant experimental custom-cloud renderer)
- `rainDensity`
- `cameraShakeIntensity`: intensity of the post-detonation camera shake, `0` disables it
- `lightningFlashIntensity`
- `stormFog`

## Building

```bash
./gradlew build -Djava.net.useSystemProxies=false
```

The output JAR is created under `build/libs/` and copied to `dist/`. The extra Java networking
property is only needed on environments where an OS proxy interferes with Gradle TLS; omit it
otherwise.

On macOS, the development client run includes `-XstartOnFirstThread`. Forge 65.1.1 userdev may
mis-handle percent-encoded non-ASCII project paths; use an ASCII-only temporary checkout for
`runClient`/`runGameTestServer` if that Forge launcher issue appears. Normal mod installation and
`build` are unaffected.

## Original assets

All code, textures, and audio in this repository are original. The mod does not contain or
redistribute assets, audio, shaders, logos, or code from other games.
