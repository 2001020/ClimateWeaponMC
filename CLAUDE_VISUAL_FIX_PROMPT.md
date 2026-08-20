# Storm Weapon weather visual repair — implement, build, and verify

You are working directly in `/Users/starrail/Desktop/软件项目/ClimateWeaponMC`, a Minecraft Java 26.2 / Forge 65.1.1 mod. Use the existing architecture and actual local Forge/Minecraft 26.2 sources; do not invent old-version APIs. You own the implementation for this repair. Inspect current code and the user screenshot at `/Users/starrail/Desktop/Screenshot 2026-08-16 at 08.08.54.png`, apply fixes, and repeatedly build until successful.

The user observed four concrete failures:

1. After the sky darkens, the artificial cloud/darkness layer has highly visible hard divisions: giant rectangular sheet/tile boundaries, polygonal bands, abrupt seams, and a black strip at the top of the screenshot. The cloud mass must become visually continuous from below and at oblique viewing angles. Eliminate hard tile/layer edges and avoid a solid black horizon/overhead strip. Keep a deep blue-gray storm, not pure black.
2. Rain streaks visibly follow the player/camera. The current renderer hashes slots and adds them around the camera, so the pattern translates with the camera. Replace this with a world-anchored deterministic regional rain field. It is acceptable and required for performance to submit only cells near the camera, but streak identity, X/Z placement, phase, and falling animation must be based on world/storm coordinates, not camera-relative slot offsets. Walking must reveal adjacent world cells rather than dragging the same rain cylinder. Every observer anywhere inside the storm radius should see rain around them, with radial fade only at the regional boundary.
3. Wind is not perceptible. Make wind unmistakable but non-disruptive: visibly stronger/slanted/gusting rain, clearly animated cloud drift, plus a bounded camera-local field of wind-driven ambient debris/dust/leaf-like particles or mesh flecks in strong phases. Anchor debris to world cells as well. Do not damage blocks or require a custom sound asset. Keep all quality budgets bounded and add/debug relevant counters if useful.
4. Lightning is effectively absent. Frequent visual intra-cloud flashes must be visible near each observer within the storm, not sampled once across the full 768-block radius where most events are out of view. Use deterministic world-grid/time-slice candidates in the observer's visible neighborhood while respecting radial storm influence and LOW/MEDIUM/HIGH/ULTRA budgets. Preserve sparse server-authoritative physical damaging strikes, but enhance their client procedural channel and near flash. Ensure at least several visible cloud flashes per minute in HIGH peak storm. Do not reduce this to vanilla lightning only.

Technical constraints:

- Graphics-API agnostic. Absolutely no direct `org.lwjgl.opengl.*`, GL constants, native backend handles, or raw shader/framebuffer calls.
- Use existing Minecraft 26.2 Blaze3D render types, frame graph, `VertexConsumer`, `StagedVertexBuffer`, Forge events, particles, and existing abstractions.
- Must continue to work on both OpenGL and native experimental Vulkan.
- Do not turn on vanilla global rain/thunder as the implementation.
- Preserve server-authoritative storm lifecycle/networking and dedicated-server safety.
- Do not scan the whole storm radius or simulate every drop on the server.
- Maintain hard per-quality budgets. HIGH should be visually strong; LOW must remain safe.
- Do not implement missile/launcher work in this repair.
- Preserve unrelated user changes and do not revert other work.

Likely relevant files:

- `src/main/java/com/stormweapon/client/weather/StormCloudRenderer.java`
- `StormCloudBudget.java`, `StormWeatherPass.java`, `StormGeometryBatch.java`
- `StormRainRenderer.java`, `StormRainBudget.java`
- `StormWindField.java`
- `StormLightningRenderer.java`, `StormLightningBudget.java`
- `StormFogController.java`
- `StormClientManager.java`, `StormDebugHud.java`
- procedural textures and `tools/generate_storm_textures.py`

Validation requirements:

1. Run `./gradlew build -Djava.net.useSystemProxies=false` after meaningful increments and finish with success.
2. Run JSON/resource sanity checks.
3. Search `src/main` for prohibited OpenGL symbols and leave zero mod-owned matches.
4. If practical, use the existing ASCII-path runtime approach documented in README for a peak-storm client smoke test; do not accept any EULA or modify security/network settings.
5. Report exact files changed, root causes, visual behavior after repair, budgets, build results, and remaining runtime risks.

Do not merely analyze or propose a plan. Implement the complete repair now.
