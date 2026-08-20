# Storm Weapon Implementation Plan

## Product target

Build an original Minecraft Java Edition 26.2 / Forge 65.1.1 mod (`stormweapon`) that delivers a server-authoritative artificial storm weapon lifecycle: launcher loading and arming, a physical guided missile, atmospheric seeding, a regional multi-stage storm, enhanced client weather, dangerous lightning, decay, and recovery.

The implementation must build with the Forge 26.2 MDK, run on a dedicated server, and render through Minecraft/Blaze3D abstractions without direct OpenGL calls so that the same jar supports Minecraft's OpenGL and experimental Vulkan backends.

## Architecture

- `StormWeaponMod`: mod entry point and common/client bootstrap.
- `registry/`: Forge deferred registers for blocks, block entities, items, entities, menus, particles, and sounds.
- `block/`, `blockentity/`: the launcher block and its persistent/synchronized launch state.
- `item/`: weather missile and creative debug controller items.
- `entity/`: authoritative missile flight, synchronized state, collision bounds, trajectory, and detonation.
- `storm/`: `StormPhase`, `StormInstance`, saved level state, wind field, timeline, physical lightning, and server tick manager.
- `network/`: compact storm snapshot/start/end, lightning, missile launch, and launcher control payloads. Visuals reconstruct from seed and game time rather than per-tick state packets.
- `command/`: OP-only `/stormweapon` test and debug commands.
- `config/`: Forge common/client configuration, validation, and quality presets.
- `client/model/`, `client/render/`: procedural missile/launcher meshes and renderers.
- `client/weather/`: regional vanilla-thunder sky bridge, wind-driven rain field, visibility fog, deterministic intra-cloud lightning, smoke nodes, and camera shake.
- `client/gui/`: launcher menu/screen and debug HUD.

## Missile entity

- Model design parameters: length 6.8 blocks, body diameter 0.78 blocks, maximum fin span 2.15 blocks.
- Full mesh: 24-sided body, multi-ring tapered nose, structural rings, recessed nozzle, four solid fins, sensors, and warning markings.
- Medium mesh: 16-sided body with reduced rings and sensor geometry.
- Far mesh: low-complexity silhouette plus an always-visible plume/trail.
- LOD thresholds: full at 0-64 blocks, medium at 64-160 blocks, far beyond 160 blocks.
- Server flight states: mounted, ignition, boost, guided climb/cruise, atmospheric detonation, removed.
- The missile follows the 75-degree launcher rail, accelerates visibly, then steers toward the target and detonates at the configured high altitude rather than teleporting or striking the ground.

## Launcher

- Stability-first implementation: one block entity with an oversized renderer and interaction volume; collision remains compact and predictable.
- Persistent/synchronized fields: missile loaded, safe/armed/countdown/ignition state, target X/Z, launch angle, radius, cooldown, and owner/last operator where needed.
- Right-click with a weather missile loads it; otherwise opens the industrial control menu.
- Server validates range, missile availability, permission/state, countdown, and cooldown before spawning the missile.

## Storm system

- Server-authoritative phases: `CLEAR`, `SEEDING`, `CLOUD_BUILDUP`, `WIND_RISING`, `HEAVY_RAIN`, `SUPERCELL`, `PEAK_STORM`, `DECAY`, `CLEARING`.
- Common config contains phase durations and all gameplay parameters. The default sequence is 330 seconds.
- Each storm stores center, core radius, transition radius, seed, start game time, current phase, phase start, and optional source missile.
- Storm influence is radial and smoothly fades through the transition zone; the system does not globally replace dimension weather.
- The server creates only sparse physical lightning and gameplay effects. It never scans the entire storm volume or simulates individual rain drops.
- Physical strikes use area sampling with cheap height/open-sky weighting, normal Minecraft damage/armor behavior where possible, configurable fire, and deterministic event synchronization.

## Client weather and rendering strategy

- Sky/clouds: the regional Storm Weapon envelope drives Minecraft's stock client thunderstorm sky and cloud tint without changing server weather. The earlier custom black sheet deck is retained only as dormant prototype code and is not submitted, avoiding hard layer seams while preserving the familiar vanilla thunder look requested for the MVP.
- Rain: camera-local long streak geometry/particles driven by the synchronized wind vector. Density, distance shells, splashes, and debris are bounded by the quality preset.
- Lightning: frequent deterministic intra-cloud flashes are visual-only; synchronized physical strikes add a short-lived procedural branching bolt, local exposure flash, delayed thunder, and optional camera shake.
- Smoke: distance-spaced trail nodes with bounded lifetimes and low-frequency particle emission; nodes drift and expand under the storm wind field.
- Sky/fog: event-based Minecraft client hooks interpolate blue-gray to deep blue-gray color and fog distance without modifying world time, chunk light, or server render distance.
- Audio uses custom sound events and original placeholder audio where final assets are not yet available; sound delay is derived from distance with gameplay compression.

## Vulkan strategy

- No `org.lwjgl.opengl.*`, GL constants/functions, backend handles, custom native framebuffers, or OpenGL-only shader management in mod source.
- Geometry is submitted only through current Minecraft 26.2 abstractions such as `PoseStack`, `VertexConsumer`, `MultiBufferSource`, current render-state/pipeline APIs, particle APIs, and Forge rendering events after verifying actual signatures from the local dependency sources.
- Custom resource pipelines/shaders are added only if the 26.2 pipeline schema can be compiled and tested on both backends. The first functional implementation uses built-in Minecraft pipelines/materials to reduce backend risk.
- Backend name is logged and shown in the debug HUD only if Minecraft exposes a stable public backend query. No reflection or native-driver hack is permitted.
- Final source scan rejects direct OpenGL symbols in the `stormweapon` source/resources.

## Networking

- A storm start/snapshot payload carries center, radius, transition radius, seed, start game time, timeline/config snapshot, and current phase.
- Clients reconstruct cloud motion, wind, rain envelope, and visual lightning from seed plus game time.
- Separate payloads cover physical lightning, atmospheric detonation/missile launch cues, launcher GUI actions, debug state, and storm end.
- Joining/tracking clients receive current storm and launcher/entity data; full storm state is not broadcast each tick.

## Performance and quality

- `LOW`, `MEDIUM`, `HIGH`, `ULTRA` presets cap rain streaks, smoke nodes, debris, splashes, lightning branches, and total storm particles. The vanilla thunder sky itself has no extra geometry budget.
- All high-volume weather is client-local and camera-bounded.
- No full-radius block iteration, chunk-light rewrites, or per-drop server simulation.
- HIGH is the visual target; ULTRA raises density and distance without changing gameplay.

## Milestones and build gates

1. Bootstrap the official Forge 26.2-65.1.1 MDK and prove `./gradlew build`.
2. Rename/configure the mod and register the initial item, block, block entity, entity, particles, sounds, menus, resources, translations, and configs; build.
3. Implement persistent launcher loading, synchronization, menu/screen, arm/countdown/ignition, collision/selection, and renderer; build.
4. Implement the physical missile lifecycle, guided trajectory, 3D procedural model, LOD, plume, smoke nodes, sounds, and atmospheric detonation; build.
5. Implement saved server storm state, timeline/config, radial influence, wind, lightning gameplay, commands, and packets; build and run server smoke test.
6. Implement the regional vanilla-thunder sky bridge, storm rain field, splashes/debris, visibility fog, visual lightning, audio delays, camera shake, and quality budgets; build.
7. Audit all rendering against the actual Minecraft 26.2 API, remove any backend-specific code, source-scan prohibited GL symbols, build, then test OpenGL and Vulkan in-game.
8. Add recipes/loot/data, original placeholder art/audio, debug HUD, README, changelog, polish, dedicated-server validation, and final jar/checksum.

Each milestone is incremental. Compile, resource, registration, shader/pipeline, and runtime errors are resolved against the local 26.2 dependencies before advancing.
