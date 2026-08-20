# Claude Code Task: Storm MVP Step 3

You are working directly in `/Users/starrail/Desktop/软件项目/ClimateWeaponMC` on a Minecraft Java 26.2 Forge 65.1.1 mod named Storm Weapon (`stormweapon`).

Read `IMPLEMENTATION_PLAN.md` and the complete `CLAUDE_STEP_3_4_HANDOFF.md`, then inspect the current source tree and the locally resolved Forge 26.2 source jar. Step 2 already builds and its exact API is documented in the handoff.

Implement only Step 3 now:

1. A client bootstrap that is never class-loaded by a dedicated server.
2. A deterministic `StormWindField` derived from synchronized seed, game time, phase envelope, and radial influence.
3. A clearly regional multi-layer black/deep-blue-gray `StormCloudRenderer`, with LOW/MEDIUM/HIGH/ULTRA budgets and at least three depth-separated moving layers in HIGH.
4. Smooth storm sky and fog transitions based on phase plus `StormSnapshot.radialInfluence`, without changing world time, chunk light, block light, or server render distance.
5. A compact debug HUD when synchronized `debug` is true, showing phase, elapsed time, center, radius, cloud/wind/rain/lightning envelopes, and graphics backend only if a reliable public Minecraft API exposes it.

Rendering must be graphics-API agnostic. Never import/call `org.lwjgl.opengl.*`, GL constants/functions, raw shaders, or raw framebuffers. Verify every uncertain Minecraft/Forge rendering hook and signature against the local 26.2 sources. Prefer current Blaze3D/Minecraft render abstractions and built-in compatible pipelines. Do not use old-version APIs from memory.

Keep files separated under `com.stormweapon.client` and `com.stormweapon.client.weather`. Do not implement rain, procedural bolts, or physical lightning in this task; those belong to the fresh Step 4 process. You may add original procedural texture resources if necessary, but do not download or copy third-party/game assets.

Run `./gradlew build -Djava.net.useSystemProxies=false` after coherent increments and finish with a successful build. Fix compile/resource errors yourself. Do not leave key behavior as TODO. At the end, report changed files, exact build result, rendering hooks chosen, budgets, and runtime-only checks still needed.
