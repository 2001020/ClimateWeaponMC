# Changelog

## Unreleased - Storm Weather Visual Repair

- Replaced the custom black cloud-sheet ceiling with Minecraft's stock thunderstorm sky and cloud
  tint, driven client-side by the regional Storm Weapon envelope. Custom rain, wind, debris and
  lightning remain independent and the server weather is not changed.
- Retired the rendered custom cloud-sheet deck after in-world Vulkan inspection still showed hard
  overhead geometry. The dormant renderer remains available for future experimentation but is no
  longer submitted by the active weather pass.
- Added `StormRenderTypes`, a pair of cutout-free Blaze3D render types built on the vanilla beacon
  beam shader pair. Every entity/particle pipeline discards fragments below 0.1 alpha, which was
  hard-edging every soft cloud and rain boundary.
- Lightened the storm fog target and lowered composite deck opacity so the sky reads as a deep
  blue-gray overcast with internal structure rather than a flat black ceiling.
- Replaced the camera-relative rain cylinder with a world-anchored cell field; walking now uncovers
  new world cells instead of dragging the same rain along, and rain is clipped to the local surface.
- Added `StormDebrisRenderer`, a bounded world-anchored field of wind-driven debris flecks, and
  raised the wind speed range so the rain tilt uses the full 20-50 degree design band.
- Added `StormLightningField`: intra-cloud flashes are now rolled per world cell per time slice in a
  bounded neighbourhood of each observer, at a preset target rate, instead of once per storm.
  Flashes are drawn additively over the vanilla thunder sky.
- Fixed cloud drift jumping by hundreds of blocks at phase transitions; the flow distance is now an
  exact integral rather than elapsed time times the current speed.
- Added cloud quad, rain cell, debris and lightning cell counters to the debug HUD.

## 0.1.0 - Initial Storm Weather Prototype

- Added a persistent, server-authoritative regional storm state machine.
- Added synchronized storm center, radius, seed, timing, phase, and debug state.
- Added three depth-separated procedural cloud sheets and a high sky wash.
- Added bounded camera-local wind-driven rain streaks and nearby splash particles.
- Added storm fog and phase-based sky/visibility transitions.
- Added deterministic intra-cloud lightning and procedural physical strike channels.
- Added sparse server-authoritative damaging lightning with configurable damage and fire.
- Added LOW, MEDIUM, HIGH, and ULTRA cloud/rain/lightning budgets.
- Added OpenGL and experimental Vulkan backend logging and validation.
- Added English and Simplified Chinese command/HUD localization.
- Added operator commands and an in-game debug HUD.
