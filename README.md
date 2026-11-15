# Living Rain 0.3.0  (Minecraft 26.3, Fabric)

Replaces vanilla rain and snow with particle rain that changes from storm to storm and during storms
(drizzle, light, heavy, torrential), with simulated wind. Client-side only: install it on your own game, no server needed.

## Build it
1. Upload everything in this folder to a GitHub repository. Make sure the hidden `.github` folder came along
   (it holds `.github/workflows/build.yml`). If GitHub added its own Gradle workflow file, delete that one.
2. Wait for the "Build mod" action to get a green tick, open it and download **livingrain-mod**.
3. Put the `.jar` in your `mods` folder next to Fabric API, YACL and Mod Menu (and Roof Drips 1.0.9 for the wind link).

If the build fails, copy the red error text from the build log and send it to me.

## What's new in 0.3.0
* **Rain no longer disappears.** Minecraft silently throws away particles when its internal queue is full; the old particle counter
  never noticed and eventually thought the limit was reached, so it stopped making rain. Particles are now counted by whether they are
  really still alive, and the mod detects Minecraft's real limit and stays under it.
* **Vanilla rain is hidden by default** (using the 26.3 weather code, with a safety net if a name is wrong).
  New option "Show vanilla rain too" (Rain Look tab) to see both.
* **Chat commands** (see below).

## Commands (type in chat, they only change your own game)
* `/livingrain start` / `/livingrain stop`  turn Living Rain on / off (stop = vanilla weather fully back)
* `/livingrain preset gentle|balanced|dramatic|chaotic`
* `/livingrain weather real|clear|drizzle|light|heavy|torrential|thunder`  test weather on your screen only
* `/livingrain vanilla on|off`  show/hide vanilla rain
* `/livingrain status`  prints what the mod is doing: particles alive, limits, which hooks work. **If rain ever stops, run this
  and send me the output.**

## Earlier changes (0.2.0)
* Rain streaks now follow the wind correctly from ANY view angle (looking up no longer makes them fall sideways).
* Much denser rain by default; Density goes up to 6.0 and the Particle limit up to 14,000.
* Motion blur setting (soft, longer streaks; faster drops in gusts stretch further), soft fade-in/out at the edges.
* Immersive weather messages ("The rain eases up."), on/off, above the hotbar or in chat.
* Debug tab in the settings: one-click weather tests (clear, drizzle, light, heavy, torrential, thunderstorm).

Note: because defaults changed, your old `config/livingrain.json` is replaced with the new defaults on first start.

## Using the test weather
Mod Menu > Living Rain > Configure > Debug tab. Click e.g. "Test: Heavy rain". The screen closes and your game shows that
weather on your screen only (no effect on the server, no lightning). Open the menu again to change rain-look or wind settings
and compare. Click "Stop testing" to return to real weather. Test mode switches itself off whenever the game restarts.

## Checking it works
Open `logs/latest.log` and search `[livingrain]`. Expected: "Rain-level hook is active", and "Weather messages are shown using ...".
Keep Minecraft's **Particles** option on **All**. If your shader pack draws its own rain, turn that off in the shader's settings.
