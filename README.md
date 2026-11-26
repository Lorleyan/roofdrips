# Brewtality's Immersive Drips 1.2.0  (Minecraft 26.3, Fabric)

Rain runs off roof edges, overhangs, tree leaves and cave mouths in streams and drips. Client-side only.

Part of a pair with **Brewtality's Immersive Rain** (particle rain and snow with wind). Each works on its own; installed together they
share one wind, and the drips follow how heavy the rain is. **Both mods are version 1.2.0: use matching versions.**
On its own, this mod uses only its own settings, exactly as before.

## 1.2.0
No code changes. The version is raised so it matches Brewtality's Immersive Rain 1.2.0 (heavy-rain effects).

## 1.1.0: renamed, and versions aligned
* Renamed from "Roof Drips" to **Brewtality's Immersive Drips**. Both mods start at 1.1.0 from here on.
* **Your settings are kept.** Only the display name changed; the internal ID (`roofdrips`) and settings file are the same.
* **Remove the old jar before installing the new one:** delete `roofdrips-*.jar` from your `mods` folder. The new file is
  `brewtality-immersive-drips-1.2.0.jar`. The internal ID is unchanged, so an old jar left in place would give Fabric two copies of the same mod.

## Included in this version (since the last release you had)
* Upside-down stairs and other partial blocks: drips now start at the block's real underside along each face (no gap).
* Drips on lower overhangs (balconies, awnings, tower tiers under another roof) when wind-slanted rain can reach them.
  Settings in the Overhang tab: "Drips on lower overhangs", "Lower overhang levels", "Rain slant for lower overhangs".
* With Brewtality's Immersive Rain installed: shared wind, and drip amounts that follow the rain's severity (set in Immersive Rain's menu).

## Console commands
None yet. The name `/BDrip` is reserved for them (Brewtality's Immersive Rain uses `/BRain`).

## Build it
Upload this folder to a GitHub repository (include the hidden `.github` folder), wait for the "Build mod" action,
and download **brewtality-immersive-drips**. Requires Fabric API, YACL and Mod Menu.
