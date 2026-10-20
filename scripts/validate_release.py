#!/usr/bin/env python3
"""Semantic release checks for the IMMERSIVE CCTV 1.4.0 source bundle."""
from __future__ import annotations
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
JAVA = ROOT / "src/main/java/ro/immersivecctv"


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

# Parse every JSON resource, not just recipes and advancements.
json_files = list(RES.rglob("*.json"))
for path in json_files:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"Invalid JSON in {path.relative_to(ROOT)}: {exc}")

recipes_dir = RES / "data/immersivecctv/recipes"
adv_dir = RES / "data/immersivecctv/advancements"
recipe_files = sorted(recipes_dir.glob("*.json"))
adv_files = sorted(adv_dir.glob("*.json"))
if len(recipe_files) < 10:
    fail(f"Expected at least 10 crafting recipes, found {len(recipe_files)}")
if len(adv_files) < 10:
    fail(f"Expected at least 10 custom advancements, found {len(adv_files)}")
recipe_ids = {f"immersivecctv:{p.stem}" for p in recipe_files}

for path in recipe_files:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("type") not in {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}:
        fail(f"Unexpected crafting recipe type in {path.name}")
    result = data.get("result", {}).get("item")
    if not result:
        fail(f"Recipe {path.name} has no result item")

for path in adv_files:
    data = json.loads(path.read_text(encoding="utf-8"))
    criteria = data.get("criteria", {})
    for criterion in criteria.values():
        if criterion.get("trigger") == "minecraft:recipe_crafted":
            recipe_id = criterion.get("conditions", {}).get("recipe_id")
            if recipe_id not in recipe_ids:
                fail(f"Advancement {path.name} references missing recipe {recipe_id}")

registry = (JAVA / "ImmersiveCCTV.java").read_text(encoding="utf-8")
for token in ["PTZ_CAMERA", "CCTV_SERVICE_TOOL", "POWER_OUTLET", "AC_CHARGER", "POWER_CABLE", "HDMI_CABLE", "POE_CABLE", "ROUTER", "INTERNET_BLOCK", "ETHERNET_CABLE", "FIBRE_OPTIC_CABLE", "CLOUD_ACCESS_REMOTE"]:
    if token not in registry:
        fail(f"Item/block registry is missing {token}")

screen = (JAVA / "client/XvrScreen.java").read_text(encoding="utf-8")
fallback = (JAVA / "client/CameraFallbackRenderer.java").read_text(encoding="utf-8")
router = (JAVA / "blockentity/RouterBlockEntity.java").read_text(encoding="utf-8")
internet = (JAVA / "blockentity/InternetBlockEntity.java").read_text(encoding="utf-8")
controller = (JAVA / "client/CameraViewController.java").read_text(encoding="utf-8")
hooks = (JAVA / "client/ClientHooks.java").read_text(encoding="utf-8")
network = (JAVA / "CctvNetwork.java").read_text(encoding="utf-8")
camera_entity = (JAVA / "blockentity/CameraBlockEntity.java").read_text(encoding="utf-8")
xvr_entity = (JAVA / "blockentity/XvrBlockEntity.java").read_text(encoding="utf-8")
service_tool = (JAVA / "item/CctvServiceTool.java").read_text(encoding="utf-8")
linking = (JAVA / "LinkingManager.java").read_text(encoding="utf-8")
cloud_item = (JAVA / "item/CloudAccessRemoteItem.java").read_text(encoding="utf-8")
cloud_screen = (JAVA / "client/CloudRemoteLoginScreen.java").read_text(encoding="utf-8")

checks = {
    "monitor screen activates native camera controller": "CameraViewController.show(" in screen,
    "camera fallback prevents permanently black feed": "fallbackRenderer.render(g, cameraPos, filter" in screen and "feedCursor >= feedPixels.length" in fallback,
    "router stores optical uplink and four LAN ports": "private final BlockPos[] lanXvrs = new BlockPos[4]" in router and "connectFibre" in router,
    "Internet Block manages five fibre ports": "new BlockPos[5]" in internet and "firstFreePort" in internet,
    "native renderer uses a remote camera entity": "EntityType.ARMOR_STAND.create(level)" in controller,
    "native renderer temporarily forces first person": "CameraType.FIRST_PERSON" in controller,
    "HUD is hidden inside XVR console": "RenderGuiOverlayEvent.Pre" in hooks and "event.setCanceled(true)" in hooks,
    "PTZ horizontal step is responsive": "camera.adjustPtz(60.0F, 0.0F)" in network,
    "PTZ vertical step is responsive": "camera.adjustPtz(0.0F, 35.0F)" in network,
    "camera factory username and password exist": 'credentialUsername = "admin"' in camera_entity and 'credentialSalt, "0000"' in camera_entity,
    "camera credentials are authenticated": "camera.authenticate(msg.username, msg.password)" in network,
    "password change is enforced": "camera.mustChangePassword()" in network and "camera.changePassword(msg.password)" in network,
    "XVR first account persists": "AccountConfigured" in xvr_entity and "configureAccount" in xvr_entity,
    "service tool tooltip gives instructions": "Right-click a CCTV camera" in service_tool,
    "camera ID shown automatically when linked": "Camera ID: " in linking,
    "cloud remote item is registered and opens a login screen": "CloudAccessRemoteItem" in registry and "openCloudRemoteLogin" in cloud_item,
    "cloud remote authenticates credentials on the server": "xvr.authenticateAccount(msg.username, msg.password)" in network and "sendCloudLoginResult" in network,
    "remote session requires reachable simulated network": "canObtainCloudEndpoint()" in network and "Host unreachable" in network,
    "remote XVR console reuses camera console UI": "new XvrScreen(xvrPos, true)" in cloud_screen and "private boolean isRemote()" in screen,
    "unique endpoint ID is synced to client": 'tag.putString("NetworkAddressId", networkAddressId)' in xvr_entity,
}
for description, ok in checks.items():
    if not ok:
        fail(f"Implementation check failed: {description}")

required_recipes = {"xvr", "monitor", "camera", "ptz_camera", "power_outlet", "cctv_service_tool", "cloud_access_remote"}
actual_recipes = {p.stem for p in recipe_files}
if not required_recipes.issubset(actual_recipes):
    fail(f"Missing core recipes: {sorted(required_recipes - actual_recipes)}")
required_advancements = {"root", "monitor", "camera", "ptz_camera", "power_outlet", "cctv_service_tool", "cloud_access_remote"}
actual_advancements = {p.stem for p in adv_files}
if not required_advancements.issubset(actual_advancements):
    fail(f"Missing core advancements: {sorted(required_advancements - actual_advancements)}")

print(f"Release checks passed: {len(json_files)} JSON resources, {len(recipe_files)} recipes, {len(adv_files)} advancements.")
for description in checks:
    print(f"  [OK] {description}")
