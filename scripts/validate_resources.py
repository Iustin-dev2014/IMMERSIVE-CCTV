#!/usr/bin/env python3
"""Fast, offline consistency checks for IMMERSIVE CCTV resource JSON and IDs."""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets"
errors = []
json_files = list(RES.rglob("*.json"))
parsed = {}
for path in json_files:
    try:
        parsed[path] = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"Invalid JSON: {path.relative_to(ROOT)}: {exc}")

mod_items = {
    "xvr", "monitor", "camera", "ptz_camera", "power_outlet",
    "hdmi_cable", "poe_cat6_cable", "ac_charger", "power_cable", "cctv_service_tool",
    "router", "internet_block", "ethernet_cable", "fibre_optic_cable", "cloud_access_remote",
}

# Validate recipe IDs, crafting pattern/key consistency, outputs, and mod ingredients.
recipe_dir = RES / "data/immersivecctv/recipes"
recipe_ids = {f"immersivecctv:{p.stem}" for p in recipe_dir.glob("*.json")}
for path in recipe_dir.glob("*.json"):
    data = parsed.get(path)
    if not isinstance(data, dict):
        continue
    if data.get("type") != "minecraft:crafting_shaped":
        errors.append(f"Unsupported/incorrect recipe type in {path.relative_to(ROOT)}")
        continue
    pattern = data.get("pattern", [])
    keys = data.get("key", {})
    if not pattern or any(not isinstance(row, str) or len(row) > 3 for row in pattern):
        errors.append(f"Invalid crafting pattern in {path.relative_to(ROOT)}")
    used = {ch for row in pattern for ch in row if ch != " "}
    if used != set(keys.keys()):
        errors.append(f"Pattern/key mismatch in {path.relative_to(ROOT)}: used={used}, keys={set(keys)}")
    result = data.get("result", {}).get("item", "")
    if result not in {f"immersivecctv:{i}" for i in mod_items}:
        errors.append(f"Recipe has unknown mod result {result!r} in {path.relative_to(ROOT)}")
    for key, ingredient in keys.items():
        item = ingredient.get("item") if isinstance(ingredient, dict) else None
        if not item or ":" not in item:
            errors.append(f"Malformed ingredient {key!r} in {path.relative_to(ROOT)}")
        elif item.startswith("immersivecctv:") and item.split(":", 1)[1] not in mod_items:
            errors.append(f"Unknown mod ingredient {item!r} in {path.relative_to(ROOT)}")

# Validate advancement parents and recipe_crafted criteria.
adv_dir = RES / "data/immersivecctv/advancements"
adv_ids = {f"immersivecctv:{p.stem}" for p in adv_dir.glob("*.json")}
for path in adv_dir.glob("*.json"):
    data = parsed.get(path)
    if not isinstance(data, dict):
        continue
    parent = data.get("parent")
    if parent and parent.startswith("immersivecctv:") and parent not in adv_ids:
        errors.append(f"Unknown advancement parent {parent!r} in {path.relative_to(ROOT)}")
    for criterion, value in data.get("criteria", {}).items():
        if value.get("trigger") == "minecraft:recipe_crafted":
            rid = value.get("conditions", {}).get("recipe_id")
            if rid not in recipe_ids:
                errors.append(f"Advancement {path.stem} references missing recipe {rid!r}")

# Check model parent, blockstate model references and texture identifiers.
def check_model(identifier, source):
    if not isinstance(identifier, str) or ":" not in identifier:
        errors.append(f"Malformed model identifier {identifier!r} in {source}")
        return
    namespace, path = identifier.split(":", 1)
    if namespace == "minecraft":
        return
    model_path = ASSETS / namespace / "models" / f"{path}.json"
    if not model_path.is_file():
        errors.append(f"Missing model {identifier!r}, referenced by {source}")

for path, data in parsed.items():
    rel = path.relative_to(RES).as_posix()
    if "/blockstates/" in f"/{rel}":
        def walk_models(obj):
            if isinstance(obj, dict):
                for key, value in obj.items():
                    if key == "model" and isinstance(value, str):
                        check_model(value, rel)
                    else:
                        walk_models(value)
            elif isinstance(obj, list):
                for value in obj:
                    walk_models(value)
        walk_models(data)
    if "/models/" in f"/{rel}":
        parent = data.get("parent") if isinstance(data, dict) else None
        if parent and parent.startswith("immersivecctv:"):
            check_model(parent, rel)
        textures = data.get("textures", {}) if isinstance(data, dict) else {}
        for texture in textures.values():
            if not isinstance(texture, str) or texture.startswith("#") or ":" not in texture:
                continue
            namespace, name = texture.split(":", 1)
            if namespace == "minecraft":
                continue
            texture_file = ASSETS / namespace / "textures" / f"{name}.png"
            if not texture_file.is_file():
                errors.append(f"Missing texture {texture!r}, referenced by {rel}")

# Check that every sound entry resolves to an OGG produced by the asset generator.
sounds_path = ASSETS / "immersivecctv/sounds.json"
sounds_data = parsed.get(sounds_path, {})
for event_name, event in sounds_data.items():
    if not isinstance(event, dict):
        continue
    for sound in event.get("sounds", []):
        name = sound.get("name", "")
        if name.startswith("immersivecctv:"):
            name = name.split(":", 1)[1]
        if name and not (ASSETS / "immersivecctv/sounds" / f"{name}.ogg").is_file():
            errors.append(f"Missing sound file for {event_name}: {name}.ogg")

if errors:
    print("RESOURCE VALIDATION FAILED")
    for issue in errors:
        print(f" - {issue}")
    sys.exit(1)

print(f"Resource validation passed: {len(json_files)} JSON files, "
      f"{len(list(recipe_dir.glob('*.json')))} recipes, "
      f"{len(list(adv_dir.glob('*.json')))} advancements, "
      f"{len(list((ASSETS / 'immersivecctv/sounds').glob('*.ogg')))} OGG files.")
