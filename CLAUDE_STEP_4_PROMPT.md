# Claude Code Task: Storm MVP Step 4

You are starting with a fresh context in `/Users/starrail/Desktop/软件项目/ClimateWeaponMC` after another process completed Step 3. This is Minecraft Java 26.2 Forge 65.1.1, mod id `stormweapon`.

Read the complete current source tree, `IMPLEMENTATION_PLAN.md`, `CLAUDE_STEP_3_4_HANDOFF.md`, and the final Step 3 implementation before editing. Verify all uncertain APIs in the local Forge/Minecraft 26.2 source jar.

Implement only Step 4:

1. Dense camera-local long rain streaks driven by the existing deterministic wind field, tilted roughly 20-50 degrees at strong wind.
2. Explicit LOW/MEDIUM/HIGH/ULTRA rain/splash/vertex budgets and bounded near-player collision/splash checks.
3. Frequent deterministic intra-cloud visual lightning that never causes gameplay damage.
4. Short-lived blue-white diffuse cloud flashes and distance-scaled screen exposure flash using Minecraft-compatible rendering.
5. Sparse server-authoritative physical lightning across the storm area, with configurable 1.5-4.5 second peak cadence, cheap area/height sampling, no large block scans, and configurable fire behavior.
6. A synchronized custom physical-strike visual: procedural main bolt with 2-5 offsets and 1-3 branches, lasting roughly 100-250 ms. A vanilla lightning entity may assist gameplay damage, but cannot be the only visual.
7. Ensure physical strikes are chosen only on the logical server. Clients may reconstruct visual-only lightning, never decide damage.

If a new packet is needed, extend the current network layer carefully and keep dedicated-server class loading safe. Do not implement missile/launcher/GUI. Never import or call OpenGL-specific APIs (`org.lwjgl.opengl.*`, GL constants, `gl*`). Use actual 26.2 Minecraft/Blaze3D/Forge abstractions after checking signatures.

Run `./gradlew build -Djava.net.useSystemProxies=false` incrementally and finish with a successful build. Fix errors yourself, leave no key TODOs, and report changed files, build results, gameplay/visual split, budgets, and runtime-only checks still required.
