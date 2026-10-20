# IMMERSIVE CCTV — network hardware and build notes

## Router installation

1. Place the Router and an Internet Block.
2. Attach the AC Charger 12V to the Router’s DC jack, then use the Power Cable to connect the adapter to a powered Power Outlet.
3. Use the router’s front POWER button to start it. With no fibre uplink, the router remains in SIGNAL LOSS state.
4. Use the Fibre Optic Cable on the dedicated rear fibre jack of the Router, then select one of the five marked top ports on the Internet Block. A port can serve only one router.
5. Use the Network Cable on one of the Router’s four LAN sockets, then click the dedicated rear WAN socket on the XVR. Each LAN socket can be assigned to one XVR.
6. The simulated WAN route is available only when the router is powered, its 12V supply and wall outlet are present, the fibre connection reaches the Internet Block, and the XVR is powered and connected on a LAN port. The XVR’s blue WAN LED reflects the router/WAN link; it does not claim that an external internet service exists.

## LEDs and endpoint

- The Router distinguishes powered/offline, powered/online and signal-loss states. Four independent LAN link indicators animate while a powered XVR is linked to a powered Router.
- The Internet Block maintains five distinct optical ports. Each port can be connected to at most one Router.
- The XVR’s yellow PoE indicator reflects linked cameras; its blue WAN indicator reflects the live router connection.
- Right-clicking the XVR away from its POWER button reports its unique simulated IP address and port. When the route is unavailable, the endpoint reads `Cannot obtain`.
- Cloud Access Remote authenticates using the account created in the XVR’s first-boot setup. It reuses the console screen and camera controls after login. A missing route produces `Host unreachable`. This is an in-world simulation; it does not bridge players or devices across separate Minecraft worlds or servers.

## Camera feed

The source includes the native camera-view controller plus a raycast/block-outline fallback to avoid a permanently empty feed when native view rendering cannot be used. The fallback is approximate and cannot reproduce all entity animations, skins, particles, fluids or transparency. The workflow can compile and verify the required classes and resources, but only an in-game client test can confirm the rendered result for a specific Forge/mod combination.

## Automated build

The self-contained GitHub Actions workflow restores this embedded project, uses Java 17 and Gradle 8.8, generates textures and synthetic OGG sound assets, validates JSON/resources and requested source features, runs `gradle --no-daemon clean build`, checks the output JAR for the CCTV/router/fibre/cloud classes and recipes, then uploads the JAR as the workflow artifact. The workflow is designed to fail rather than upload a missing or incomplete JAR.
