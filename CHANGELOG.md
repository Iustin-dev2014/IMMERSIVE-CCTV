# Changelog — IMMERSIVE CCTV 1.4.0

## First official-release candidate

- Reworked live camera presentation to use Minecraft's native world renderer through a camera viewpoint, rather than synthesizing the picture with low-resolution per-pixel block raycasts; the active view also temporarily uses first-person camera mode and suppresses player HUD overlays.
- Increased PTZ movement increments to 60° pan and 35° tilt per press, with a HOME reset.
- Added a one-time first-start XVR account setup, stored with a salted password hash in the XVR block entity.
- Added unique per-camera IDs, default `admin` / `0000` credentials, server-side credential checks, and a forced password change after the first successful default-password login.
- Added the CCTV Service Tool, usable by right-clicking a camera to show its ID and credential status.
- Added 10 crafting recipes and 10 crafting-related advancements.
- Added resource-validation tooling, texture/sound generators, and a GitHub Actions build workflow for JDK 17 / Forge 1.20.1.
- Updated English/Romanian naming and release documentation.

## Validation status

Resource JSON, recipe patterns, advancement parents/recipe references, model references, textures, sound references, Python syntax, and ZIP structure are checked locally. The current execution environment does not have Gradle or JDK 17 installed and cannot launch Minecraft, so the Forge compilation and in-game camera-rendering/authentication flows still require confirmation on a proper Minecraft Forge 1.20.1 setup. Do not mark the release as stable solely on the basis of static resource validation.
