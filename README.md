# IMMERSIVE CCTV — v1.4.0-beta.4 (development candidate)

**Author:** Iustin Martac  
**Minecraft:** Java Edition 1.20.1  
**Mod loader:** Minecraft Forge 47.2.x  
**Java:** 17  
**Release status:** unpublished development candidate derived from the public beta.3 source; do not publish as stable until CI and in-game testing pass.

IMMERSIVE CCTV adds a physical-feeling CCTV installation to Minecraft: an XVR recorder, monitor, fixed camera, motorized PTZ camera, wall outlet, power hardware and cables. The project is designed for security rooms, factories, roleplay builds, research bases and other technical constructions.

## Highlights

- **Native Minecraft live-camera rendering:** when a camera has been authenticated, the monitor console switches Minecraft's camera viewpoint to the CCTV camera. The feed is rendered through the normal world renderer rather than synthesizing the picture from per-pixel block raycasts. While the console is open, the camera temporarily switches to first-person perspective and player HUD overlays are hidden so the monitor viewport is not covered by the hotbar or crosshair. This is intended to preserve the game's actual block models, textures, entity models, animation, lighting, fluids and particles. The camera view is cropped into a 16:9 monitor window in the XVR UI.
- **Faster PTZ controls:** PTZ commands change pan by 60 degrees and tilt by 35 degrees per button press; tilt is clamped to a usable range and HOME resets the PTZ position.
- **First-start XVR setup:** the first working console start asks the player to create an administrator username and password. The setup flag and salted password hash are stored in the XVR block entity; replacing a broken XVR starts setup again.
- **Per-camera credentials:** each camera receives a unique UUID, username `admin`, and initial password `0000`. Hold the **CCTV Service Tool** and right-click a camera to display its ID and credential status. The first successful default-password login requires a new password before video is enabled.
- **Server-validated device commands:** credential checks, password changes, PTZ movements and camera controls are validated by the server. Passwords are stored as salted SHA-256 hashes rather than plaintext NBT values.
- **Crafting recipes:** recipes are provided for the XVR, monitor, fixed camera, PTZ camera, outlet, HDMI cable, PoE Cat6 cable, 12V AC charger, power cable and service tool.
- **Advancements:** crafting the XVR, monitor, cameras, outlet, cables, charger and service tool unlocks custom advancements and toast notifications.
- **Physical connection and power flow:** use compatible power, HDMI and PoE cables to link the camera, recorder and monitor. The XVR and router require a 12V adapter, a power cable and a powered wall outlet.
- **New in this development candidate — router and fibre infrastructure:** the desktop router has a red signal-loss state until its fibre lead is connected to one of the five ports on an Internet Block. Its four LAN ports can be wired individually to the XVR WAN port. The four LAN activity indicators blink when a powered XVR is linked on the corresponding port. The XVR front panel now represents the PoE-camera link and powered-router WAN link separately from power/status indicators.
- **In-game endpoint diagnostics:** use the CCTV Service Tool on an XVR to inspect its simulated IP/port. Both fields show `Cannot obtain!` until the XVR is powered, the router WAN/LAN path is connected and powered, and the optical uplink reaches the Internet Block. These addresses are simulated Minecraft endpoints, not public Internet addresses.
- **Local DVR snapshots:** while an authenticated live console is open, the client saves periodic frames locally for playback. This is a client-side snapshot feature, not server-side surveillance recording while nobody is viewing the console.
- **English and Romanian names:** item/block language files are included for `en_us` and `ro_ro`.

## Installation

1. Install **Minecraft Java Edition 1.20.1**.
2. Install a compatible **Minecraft Forge 1.20.1 / 47.x** profile.
3. Launch once to create the game directory, then close Minecraft.
4. Copy the compiled `immersivecctv-1.4.0.jar` to the `.minecraft/mods` folder.
5. Start Minecraft with the Forge profile.

For development, use **JDK 17**. The build uses ForgeGradle 6 and Forge `1.20.1-47.2.0`.

## First setup and camera onboarding

1. Place the XVR, monitor, camera/PTZ camera, wall outlet and required power hardware.
2. Connect the monitor to the XVR with HDMI and connect cameras to the eight front PoE ports with PoE Cat6.
3. Power the XVR, monitor and router using a 12V charger, wall outlet and Power Cable for each device.
4. (Optional network hardware) Connect a router LAN 1–4 port to the XVR's rear WAN jack with the Ethernet Network Cable. Connect the router's rear fibre jack to one of the five top ports on the Internet Block with the Fibre Optic Cable.
5. Open the monitor console and wait for the first boot animation.
6. On the first active start, create an XVR administrator username and password. This setup is saved for that placed XVR; it is not an XVR login prompt on every subsequent opening.
7. Craft and hold the **CCTV Service Tool**, then right-click a newly placed camera to view its unique ID and initial credentials (`admin` / `0000`).
8. Return to the XVR console, select the connected camera and enter its username and password. After the first successful default login, choose a new password to enable its live feed.
9. PTZ cameras support pan, tilt and HOME controls. Open XVR SETTINGS for camera controls and device management.

**Security note:** the default camera credentials are intentionally predictable for initial setup. Change them immediately when prompted. Do not reuse a personal password.

## Crafting and advancements

The mod supplies recipes and custom crafting advancements for all primary pieces of the CCTV system. Recipes use vanilla materials and the mod's own items, and are stored under `src/main/resources/data/immersivecctv/recipes/`. Advancement JSONs are stored under `src/main/resources/data/immersivecctv/advancements/`.

## Build from source

Prerequisites: JDK 17, Python 3, Gradle 8.x, and FFmpeg with Ogg/Vorbis encoding support.

```text
python3 scripts/generate_textures.py
python3 scripts/generate_sounds.py
gradle --no-daemon build
```

The mod JAR is written under `build/libs/`. A GitHub Actions workflow is included at `.github/workflows/build.yml`; it generates assets, builds the mod and uploads the resulting JAR as a workflow artifact.

This complete **source ZIP is provided for Minecraft/modding enthusiasts** who want to explore the Java code, resources and build configuration. Generated audio and texture assets are also included for convenience.

## Known limits / validation notes

- The live view is rendered by switching the local client renderer's camera entity while the XVR screen is open. If the native framebuffer appears entirely black, the UI now falls back to a progressive block-raycast preview sampled from loaded client chunks. This fallback preserves a visible picture but cannot reproduce entity models/animations, skin textures, particles, fluids or some transparent block details. The native feed and fallback both need in-game testing on Forge 1.20.1, especially with shaders and mods that manipulate the active camera.
- This candidate implements physical router/fibre/LAN/WAN state and endpoint diagnostics, but **does not yet implement the Cloud Access Remote login and remote live-video streaming UI**. A genuine remote view needs server-authoritative authentication and a feed transport for camera chunks that are not loaded on the client; showing a fake successful remote session would be misleading.
- Local recording stores sampled frames from the rendered camera window while the console is open. It does not capture a continuous stream while the UI is closed.
- Camera login status is maintained by the camera block entity, so the current authorization model is device/world state rather than an independent permission list per player.
- Forge compilation and an actual Minecraft client launch must be confirmed on a machine with the required Gradle/Forge dependencies. Static resource checks alone do not prove the game starts without errors.

## Thanks & feedback

Bug reports should include the exact Minecraft version, Forge version, Java version, relevant logs and reliable steps to reproduce the issue. Suggestions and careful testing help prepare the first stable release.
