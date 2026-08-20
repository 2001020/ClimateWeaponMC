# Claude Code Handoff: Storm MVP Steps 3 and 4

## Objective

Implement the first visually convincing Storm Weapon weather slice on top of the already-buildable Step 2 server state. This assignment is specifically for:

- Step 3: regional black storm clouds, sky/fog transition, and client wind field.
- Step 4: wind-driven heavy rain, intra-cloud visual lightning, and sparse server-authoritative physical lightning.

Do not implement the missile, launcher, GUI, recipes, or the later full weapon lifecycle in this assignment.

## Required working method

1. Read `IMPLEMENTATION_PLAN.md`, this handoff, the complete current source tree, and current `build.gradle`/`mods.toml` before editing.
2. Inspect the resolved Minecraft 26.2 / Forge 65.1.1 dependency sources for every uncertain rendering/event/network/config API. Do not copy signatures from older Minecraft versions.
3. Preserve the Step 2 public contracts and server authority. If a small supporting change is essential, keep it minimal and document it in your final response.
4. Work incrementally and run `./gradlew build` after each coherent subsystem.
5. Fix all compile/resource errors before returning control. Do not leave key functionality as TODOs.

## Architecture and ownership

Expected client packages (adapt names to the actual Step 2 code, but keep responsibilities separated):

- `com.stormweapon.client.StormClientManager`
  - Receives/reads the synchronized storm snapshot.
  - Computes interpolated phase intensity at current client game time.
  - Owns client-only visual event queues and deterministic random helpers.
- `com.stormweapon.client.weather.StormWindField`
  - Deterministically derives direction, base strength, gust, and turbulence from storm seed plus time slice.
  - Provides pure query methods used by clouds, rain, fog, and debris.
- `com.stormweapon.client.weather.StormCloudRenderer`
  - Renders an original regional storm layer centered on the storm center.
  - Uses at least three moving/depth-separated layers in HIGH quality.
  - Has visible height/darkness variation and a lower rotating storm base.
- `com.stormweapon.client.weather.StormFogController`
  - Interpolates fog color and visual distance by phase and radial influence.
  - Does not change server view distance, world time, chunk lighting, or block light.
- `com.stormweapon.client.weather.StormRainRenderer` and/or custom client particles
  - Produces long, fast, camera-local rain streaks tilted by wind.
  - Uses bounded near/mid distance shells and optional low-probability splashes.
- `com.stormweapon.client.weather.StormLightningVisualManager`
  - Generates frequent deterministic intra-cloud flashes that are strictly visual.
  - Renders short-lived diffuse cloud illumination and, for synchronized physical strikes, a procedural branched bolt.
- `com.stormweapon.storm.StormLightningManager`
  - Server-only decision maker for sparse damaging strikes inside the storm area.
  - Never performs client rendering and never asks clients to choose gameplay strikes.

Do not place client-only Minecraft classes in common/server-loaded initializers. Dedicated-server class loading must remain safe.

## Step 2 integration contract

Step 2 is implemented and `./gradlew build -Djava.net.useSystemProxies=false` succeeds. Use these exact compiled contracts:

- `com.stormweapon.storm.StormPhase`
  - Phases: `CLEAR`, `SEEDING`, `CLOUD_BUILDUP`, `WIND_RISING`, `HEAVY_RAIN`, `SUPERCELL`, `PEAK_STORM`, `DECAY`, `CLEARING`.
  - `durationTicks()` and `next()` use common config values.
- `com.stormweapon.storm.StormSnapshot`
  - Immutable synchronized record.
  - `phaseProgress(long gameTime, float partialTick)`.
  - `radialInfluence(double x, double z)` with smooth core/transition fade.
  - `cloudIntensity(...)`, `windIntensity(...)`, `rainIntensity(...)`, and `lightningIntensity(...)` provide phase envelopes.
- `com.stormweapon.client.StormClientState`
  - Pure data holder safe to load on dedicated server.
  - `StormClientState.snapshot()` returns the latest synchronized `StormSnapshot`.
  - `accept(...)` and `clear()` are network lifecycle methods; render code should normally only query `snapshot()`.
- `com.stormweapon.storm.StormSavedData`
  - Codec-backed per-dimension saved state.
  - `get(ServerLevel)`, `start`, `stop`, `forcePhase`, `tick`, `toggleDebug`, and `snapshot`.
- `com.stormweapon.network.StormNetwork`
  - Forge `SimpleChannel` dimension/player synchronization.
  - `syncLevel(ServerLevel)` and `syncPlayer(ServerPlayer)`.
- `com.stormweapon.config.StormConfig`
  - Common radius/timeline/lightning settings.
  - Client quality, cloud quality, rain density, camera shake, flash intensity, and fog settings.
- `com.stormweapon.config.StormQuality`
  - `LOW`, `MEDIUM`, `HIGH`, `ULTRA`; `StormConfig.quality()` parses the active client setting.

The synchronized snapshot contains:

- active flag
- phase
- center X/Z
- core radius and transition radius
- seed
- start game time / current phase time
- cloud, rain, wind, and lightning envelope values or enough timeline data to reconstruct them

The build currently emits only a deprecation note for the intentionally simple Forge `SimpleChannel` message builder; there are no errors.

Commands must remain OP-only:

- `/stormweapon storm start`
- `/stormweapon storm start <x> <z>`
- `/stormweapon storm stop`
- `/stormweapon storm phase <phase>`
- `/stormweapon debug`

Do not replace the independent regional controller with a simple global `/weather thunder` implementation. Vanilla rain/thunder may be used only as a low-level auxiliary if it does not become the primary visual or lifecycle system.

## Rendering strategy

### Graphics API requirements

- The mod must be graphics-API agnostic.
- Never import or invoke `org.lwjgl.opengl.*`, `GL11`, `GL20`, `GL30`, `GL40`, `GL45`, `glBind*`, `glUseProgram`, `glUniform*`, or raw framebuffer/shader functions.
- Submit all geometry using the actual 26.2 Minecraft/Blaze3D abstractions: current render events/state or pipeline API, `PoseStack`, `VertexConsumer`, `MultiBufferSource`, and Minecraft particle APIs as appropriate.
- Prefer built-in compatible pipelines/materials for this MVP. Add custom pipeline resources only after verifying the 26.2 schema and build/runtime registration path.
- Do not use reflection or native-driver hacks to identify the backend.

### Regional storm influence

- Core radius defaults to the common config value (target 768 blocks).
- Transition zone fades to normal between core radius and transition radius (target 1024 blocks).
- All sky, fog, cloud, rain, wind, and lightning visual intensity must multiply by smooth radial influence relative to the camera/player.
- Outside the transition zone, the mod must leave visuals normal.

### Clouds

- HIGH quality must have at least upper, middle, and lower storm-base layers around Y 180-260.
- Layers need independent UV/noise movement, scale, opacity, tint, and movement vector.
- The cloud region must be visibly centered around the storm rather than repeating across the whole dimension.
- The lower base should be deepest blue-gray, not pure black.
- Use original procedural/noise resources generated in the repository; do not download or copy game assets.
- Apply explicit render-distance and tile budgets for LOW/MEDIUM/HIGH/ULTRA.

### Fog and sky

- Buildup transitions from normal daylight toward blue-gray; peak is deep blue-gray while retaining low ambient readability.
- Core-storm visual fog should target roughly 80-160 blocks; medium phases roughly 150-250 blocks.
- Effects must interpolate smoothly when phases or radial influence change.

### Rain

- Rain streaks are long, fast, and clearly tilted 20-50 degrees at strong wind.
- Most rain is client-only and camera-local. Do not simulate individual drops on the server.
- Ground/water/leaves/roof splash checks are low probability, near the player, and bounded per frame/tick.
- HIGH should read as a dense rain curtain but must have a hard particle/vertex budget.

### Visual lightning

- Frequent intra-cloud lightning is visual-only and deterministic from storm seed + time slice + coarse position.
- Flash duration is approximately 100-250 ms with optional quick re-flash.
- Synchronized physical strikes add a main bolt with 2-5 random polyline offsets and 1-3 smaller branches.
- Use a near-white core with pale blue outer appearance through Minecraft-compatible rendering.
- Short exposure/screen flash decays within about 50-120 ms and scales down with distance.

### Physical lightning

- Only the logical server chooses physical strike times/positions.
- Peak target cadence is configurable, approximately one strike every 1.5-4.5 seconds across the entire storm area, not per player.
- Use cheap random area sampling and bounded height/open-sky checks; never scan the whole radius.
- Real strikes cause damage through normal server gameplay logic and obey the common config for damage multiplier and fire.
- If the MVP reuses a vanilla `LightningBolt` for damage, the custom synchronized visual remains required.

## Quality budgets

Implement or consume `LOW`, `MEDIUM`, `HIGH`, `ULTRA` settings. Values may be tuned, but each preset must explicitly cap:

- cloud layers/tiles
- rain streaks or particles near the camera
- splash attempts
- debris particles (if included)
- simultaneous visual lightning events
- bolt segments/branches

No per-tick scan of a 768-1024 block radius is acceptable.

## Resources

- Put original textures/noise/particle resources under `src/main/resources/assets/stormweapon/`.
- Add or update both `en_us.json` and `zh_cn.json` for any player-visible text.
- Resource names must be lowercase and namespace-correct.
- Do not embed or copy Delta Force assets, sounds, shaders, logos, code, or UI.

## Acceptance checklist before handoff back

- `./gradlew build` exits 0.
- `/stormweapon storm start` causes a visibly regional artificial storm progression.
- Dark multi-layer cloud cover is visibly distinct from vanilla clouds.
- Rain is dense, streak-like, and wind-tilted.
- Fog/sky darkening fades smoothly and stays blue-gray rather than pure black.
- Frequent cloud-only flashes occur without gameplay damage.
- Sparse server-decided strikes produce damage plus custom visual enhancement.
- `/stormweapon storm stop` fades or clears the client effect without stale state.
- No direct OpenGL imports/calls exist under `src/main`.
- Client-only code is not loaded by a dedicated server.
- Final response lists changed files, build commands/results, known runtime-only items still requiring in-game validation, and any Step 2 contract changes.
