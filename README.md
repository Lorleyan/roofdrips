# Brewtality's Immersive Rain 1.1.2  (Minecraft 26.3, Fabric)

Replaces vanilla rain and snow with particle rain that changes from storm to storm and during storms
(drizzle, light, heavy, torrential, thunderstorm), with simulated wind. Client-side only: install it on your own game, no server needed.

Part of a pair with **Brewtality's Immersive Drips** (roof, overhang and cave drips). Each works on its own; installed together they
share one wind and the drips follow how heavy the rain is. Works with Brewtality's Immersive Drips 1.1.0 or newer (Immersive Drips has had no changes since 1.1.0).

## 1.1.2: rain sheets, lightning flash, curtain view
* **Rain sheets** (Mod Menu > Rain Look > Heavy-rain effects): in heavy rain, torrential rain and thunderstorms, many extra thin streaks are
  drawn as big flat pictures, each holding dozens of drops. That is what gives a downpour its dense, thin-streak look. Drizzle and light rain
  are unchanged (plain particles). Fewer individual drops are made while sheets are on, which saves CPU. Sheets fade out before they reach a
  roof or the ground and never appear under cover. Settings: "Rain sheets", "Sheet amount" (red above 150%), "Sheet size".
  Not used when "See rain through windows" is on.
* **Rain curtains are visible now.** Sheets show the thick and thin patches clearly. To see exactly what the curtains do, use the new
  debug view: Debug tab > "Show rain curtains (tint)" or `/BRain curtains show on|off` (red = thick rain, blue = thin rain; it shows the
  curtains at full strength even in light rain; it switches itself off when you restart the game).
* **Lightning flash:** the rain lights up for an instant when lightning strikes. Settings: "Lightning flash", "Flash strength". Test it with `/BRain flash`.
* Fixed: the particle limit could quietly cut the amount of rain when curtains were on; limits now apply to particles actually created.
* `/BRain status` now also shows sheets alive, sheet strength, whether the lightning-flash timer was found, and the curtain view state.

## 1.1.1: heavy-rain effects, and a drizzle fix
* **Drizzle is visible again.** Each drizzle drop was getting only 12% opacity (the faintest of any rain, and it has the fewest drops).
  Drizzle and light rain drops are now clearly more visible; heavy and torrential rain look as before.
  The experimental "See rain through windows" mode also no longer makes drizzle all but vanish.
* **New heavy-rain effects** (Mod Menu > Rain Look > "Heavy-rain effects"). They fade in as rain gets heavier; **drizzle and light rain stay plain particles**:
  * **Rain curtains:** patches of thicker and thinner rain drift along the wind, so you see walls of rain sweeping past. The average amount of rain stays what Density says.
  * **Glinting drops:** a share of drops get a brighter segment, like a streak catching the light, and sparkle slightly.
  * **Backlight glow:** rain glows when it is between you and the sun or moon.
* `/BRain status` shows the effect strength, curtain contrast, share of glinting drops, and whether the sun/moon direction could be read.

## 1.1.0: renamed, and versions aligned
* Renamed from "Living Rain" to **Brewtality's Immersive Rain** (and "Roof Drips" is now **Brewtality's Immersive Drips**).
  Both mods start at 1.1.0 from here on.
* **Your settings are kept.** Only the display name changed; the mod's internal ID (`livingrain`) and settings file are the same.
* **Remove the old jar files before installing the new ones.** Delete `livingrain-*.jar` and `roofdrips-*.jar` from your `mods` folder.
  The new files are called `brewtality-immersive-rain-<version>.jar` and `brewtality-immersive-drips-<version>.jar`. Because the internal IDs are
  the same, leaving an old jar in place would give Fabric two copies of the same mod.
* The chat commands are now `/BRain ...` (see "Console commands" below). The old `/livingrain` name no longer exists.

## Build it
1. Upload everything in this folder to a GitHub repository. Make sure the hidden `.github` folder came along
   (it holds `.github/workflows/build.yml`). If GitHub added its own Gradle workflow file, delete that one.
2. Wait for the "Build mod" action to get a green tick, open it and download **brewtality-immersive-rain**.
3. Put the `.jar` in your `mods` folder next to Fabric API, YACL and Mod Menu (and Brewtality's Immersive Drips 1.1.0 or newer for the link).

If the build fails, copy the red error text from the build log and send it to me.

## Console commands
Type these in chat. They only change your own game. Minecraft treats command names as case-sensitive, so `/brain` works too.
Type `/BRain` and press Tab to see them. The same list is shown in the Debug tab of the mod's settings.

| Command | What it does |
|---|---|
| `/BRain start` / `/BRain stop` | Turn Immersive Rain on / off (stop = vanilla weather fully back) |
| `/BRain preset gentle\|balanced\|dramatic\|chaotic` | Apply a storm and wind preset |
| `/BRain weather real\|clear\|drizzle\|light\|heavy\|torrential\|thunder` | Test weather on your screen only (`real` stops testing) |
| `/BRain vanilla on\|off` | Show or hide vanilla rain alongside the particle rain |
| `/BRain drips on\|off` | Immersive Drips' drip amounts follow the rain's severity, or not |
| `/BRain curtains show on\|off` | Debug view: tints rain red where the rain curtain is thick and blue where it is thin |
| `/BRain flash` | Test the lightning flash (the rain brightens for an instant) |
| `/BRain status` | Shows what the mod is doing: particles alive, limits, which hooks work. **If rain ever stops, run this and send me the output.** |

Reserved name: `/BDrip` is for console commands in Brewtality's Immersive Drips, which has none yet.

## Earlier versions (numbered 0.x before the rename)
### 0.7.0
* Default opacity is now 45%. Drops are smaller as rain gets heavier (drizzle unchanged at x1.5; light ~x1.0, heavy ~x0.7,
  torrential and thunderstorm x0.4 by default). Existing settings still on the old defaults are updated automatically.
* New "Rain brightness" slider (default 85%) and a little brightness variation between drops, to help the rain sit better
  under shader packs' lighting.
* Immersive Drips now also receives the rain's visible slant, so drips on lower overhangs (balconies, awnings, tower tiers) react
  to the same wind direction you see in the rain. `/BRain status` shows it.

### 0.6.0
* **Immersive Drips drip amount follows the rain's severity.** Few drips in a drizzle, more in a downpour, the most in a thunderstorm.
  Settings in Mod Menu > Immersive Rain > Configure > Immersive Drips tab: "Scale Immersive Drips with rain severity" (on/off) and
  "Drip amount: drizzle / torrential rain / thunderstorm" (defaults 20% / 100% / 130% of Immersive Drips' own density setting;
  light and heavy rain are blended evenly in between).
* **Off = Immersive Drips behaves exactly as it does without Immersive Rain** (full drips whenever it rains).
  Without Immersive Rain installed, Immersive Drips never changes: it keeps using its own settings.
* Commands: `/BRain drips on|off`; `/BRain status` shows the rain level being sent to Immersive Drips.

### 0.5.0
* **Drop size now shrinks as rain gets heavier.** Two settings (both capped at 2.0): "Drop size: drizzle" (default 1.5) and
  "Drop size: thunderstorm" (default 0.5). Light and heavy rain are blended evenly between them:
  drizzle 1.5, light ~1.17, heavy ~0.83, torrential 0.5, thunderstorm 0.5. The old single slider is now "Snowflake size".
* **More ground splashes in heavier rain**, most during a thunderstorm, plus a "Splash amount" slider (red above 2.0).
* **See rain through windows (experimental)** in the Rain Look tab: for shader packs that hide rain behind glass.
  Draws rain in a different way (thin hard-edged lines). Off by default.
* `/BRain status` now also shows the current drop-size multiplier and splash target.

### 0.4.0
* **Faster rain, and heavier rain falls faster.** Fall speed now depends on storm severity on top of your slider:
  torrential rain falls about 3x as fast as a drizzle. New default is x2.0 (the old maximum); the slider goes up to x6.0.
  Drizzle ~16 blocks/s, light ~24, heavy ~34, torrential ~49 at the default.
* **Much denser by default.** Density default is now 6.0 (the old maximum); slider up to 20. Particle limit default 20,000, slider up to 60,000.
* **Red warnings.** Density above 6.0, Particle limit above 25,000, Radius above 26 and Rain height above 20 show in red:
  they cost more performance than the defaults.
* **Opacity default 50%.**
* Wind slant keeps the same angle at any fall speed; streaks stretch a little longer when drops are very fast;
  splashes are rationed in dense rain so they do not flood Minecraft's particle queue.
* Your existing config is upgraded automatically: only fall speed, density, particle limit and opacity are reset to the new defaults.
  Everything else you set is kept.

Note: Minecraft may discard particles beyond an internal limit (around 16,000). The mod detects this and stays under it,
so a Particle limit above that may not add more rain. `/BRain status` shows whether Minecraft is capping it.

### 0.3.0
* **Rain no longer disappears.** Minecraft silently throws away particles when its internal queue is full; the old particle counter
  never noticed and eventually thought the limit was reached, so it stopped making rain. Particles are now counted by whether they are
  really still alive, and the mod detects Minecraft's real limit and stays under it.
* **Vanilla rain is hidden by default** (using the 26.3 weather code, with a safety net if a name is wrong).
  New option "Show vanilla rain too" (Rain Look tab) to see both.
* **Chat commands** (see below).

## Commands (type in chat, they only change your own game)
* `/BRain start` / `/BRain stop`  turn Immersive Rain on / off (stop = vanilla weather fully back)
* `/BRain preset gentle|balanced|dramatic|chaotic`
* `/BRain weather real|clear|drizzle|light|heavy|torrential|thunder`  test weather on your screen only
* `/BRain vanilla on|off`  show/hide vanilla rain
* `/BRain status`  prints what the mod is doing: particles alive, limits, which hooks work. **If rain ever stops, run this
  and send me the output.**

### 0.2.0
* Rain streaks now follow the wind correctly from ANY view angle (looking up no longer makes them fall sideways).
* Much denser rain by default; Density goes up to 6.0 and the Particle limit up to 14,000.
* Motion blur setting (soft, longer streaks; faster drops in gusts stretch further), soft fade-in/out at the edges.
* Immersive weather messages ("The rain eases up."), on/off, above the hotbar or in chat.
* Debug tab in the settings: one-click weather tests (clear, drizzle, light, heavy, torrential, thunderstorm).

Note: because defaults changed, your old `config/livingrain.json` is replaced with the new defaults on first start.

## Using the test weather
Mod Menu > Immersive Rain > Configure > Debug tab. Click e.g. "Test: Heavy rain". The screen closes and your game shows that
weather on your screen only (no effect on the server, no lightning). Open the menu again to change rain-look or wind settings
and compare. Click "Stop testing" to return to real weather. Test mode switches itself off whenever the game restarts.

## Checking it works
Open `logs/latest.log` and search `[livingrain]`. Expected: "Rain-level hook is active", and "Weather messages are shown using ...".
Keep Minecraft's **Particles** option on **All**. If your shader pack draws its own rain, turn that off in the shader's settings.
