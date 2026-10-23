#!/usr/bin/env python3
"""Create and safely restore a user-home snapshot for the temporary Ubuntu VM."""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath

VERSION = 1
ROOT = Path(os.environ.get("GITHUB_WORKSPACE", ".")).resolve()
SAVE_DIR = ROOT / "ubuntu saves"
HOME = Path.home()
MAX_UNPACKED = 5 * 1024 * 1024 * 1024
EXCLUDED_PATHS = {
    ".ssh", ".gnupg", ".aws", ".azure", ".kube", ".docker",
    ".local/share/keyrings", ".local/share/Trash", ".config/gh",
    ".cargo/registry", ".gradle/caches", ".mozilla",
    ".config/google-chrome", ".config/chromium",
    ".config/Code/Cache", ".config/Code/CachedData",
    ".config/Code/Service Worker/CacheStorage",
}
EXCLUDED_DIR_NAMES = {
    ".cache", ".npm", ".rustup", ".thumbnails",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", ".tox",
    "__pycache__", "node_modules", ".venv", "venv", ".next", ".turbo",
}
PACKAGE_RE = re.compile(r"^[a-z0-9][a-z0-9+.-]*$")

def safe_member(name):
    p = PurePosixPath(name)
    return not p.is_absolute() and ".." not in p.parts and bool(p.parts)

def excluded(rel):
    parts = rel.parts
    value = rel.as_posix()
    # Exclude sensitive/root-level paths and regenerable folders anywhere in HOME.
    if any(value == item or value.startswith(item + "/") for item in EXCLUDED_PATHS):
        return True
    return any(part in EXCLUDED_DIR_NAMES for part in parts)

def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()

def save():
    """Write a directory of small, custom-encoded chunks instead of a nested ZIP."""
    import zlib

    out_dir = ROOT / "ubuntu-session-save"
    if out_dir.exists():
        shutil.rmtree(out_dir)
    payload_dir = out_dir / "payload"
    payload_dir.mkdir(parents=True, exist_ok=True)

    # This is a format/obfuscation key, not cryptographic protection.
    # The matching decoder is deliberately kept in this workflow helper.
    key = b"immersive-cctv-session-v1"
    magic = b"ICCTV1D"
    chunk_bytes = 1024 * 1024
    next_chunk = 0
    entries = {}

    def encode_bytes(data):
        packed = zlib.compress(data, level=6)
        return bytes(value ^ key[index % len(key)] for index, value in enumerate(packed))

    def store_logical_file(logical_name, data):
        nonlocal next_chunk
        chunk_names = []
        for offset in range(0, len(data), chunk_bytes):
            part = data[offset:offset + chunk_bytes]
            encoded = magic + encode_bytes(part)
            chunk_name = f"payload/part-{next_chunk:08d}.dat"
            (out_dir / chunk_name).write_bytes(encoded)
            chunk_names.append(chunk_name)
            next_chunk += 1
        if not data:
            chunk_name = f"payload/part-{next_chunk:08d}.dat"
            (out_dir / chunk_name).write_bytes(magic + encode_bytes(b""))
            chunk_names.append(chunk_name)
            next_chunk += 1
        entries[logical_name] = {
            "sha256": sha256_bytes(data),
            "size": len(data),
            "chunks": chunk_names,
        }

    package_result = subprocess.run(
        ["bash", "-lc", "apt-mark showmanual"],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    packages = sorted({line.strip() for line in package_result.stdout.splitlines()
                       if PACKAGE_RE.fullmatch(line.strip())})

    for base, dirs, files in os.walk(HOME, followlinks=False):
        base_path = Path(base)
        rel_base = base_path.relative_to(HOME)
        dirs[:] = sorted(d for d in dirs
                         if not (rel_base / d).is_symlink()
                         and not excluded(rel_base / d))
        for filename in sorted(files):
            source = base_path / filename
            rel = source.relative_to(HOME)
            if excluded(rel) or source.is_symlink() or not source.is_file():
                continue
            try:
                store_logical_file("home/" + rel.as_posix(), source.read_bytes())
            except OSError as exc:
                print(f"Skipping unreadable file {rel}: {exc}", file=sys.stderr)

    package_data = ("\n".join(packages) + "\n").encode()
    store_logical_file("metadata/apt-manual.txt", package_data)

    manifest = {
        "format": "immersive-cctv-chunked-session",
        "version": VERSION,
        "codec": "ICCTV1D + repeating-key XOR + zlib",
        "session_mode": os.environ.get("SESSION_MODE", "unknown"),
        "created_utc": subprocess.run(
            ["date", "-u", "+%Y-%m-%dT%H:%M:%SZ"],
            text=True, stdout=subprocess.PIPE, check=True
        ).stdout.strip(),
        "home_directory": str(HOME),
        "file_count": len(entries),
        "chunk_count": next_chunk,
        "files": entries,
        "notes": "Original files are split into small encoded .dat pieces; manifest.db is the decoder index."
    }
    manifest_data = json.dumps(manifest, separators=(",", ":"), sort_keys=True).encode()
    (out_dir / "manifest.db").write_bytes(b"ICCTVDB1" + encode_bytes(manifest_data))
    (out_dir / "format.info").write_text(
        "ICCTV-SESSION-CHUNKS\nformat=ICCTV1D\nversion=1\nindex=manifest.db\npayload=payload/*.dat\n",
        encoding="utf-8"
    )

    total_bytes = sum(p.stat().st_size for p in out_dir.rglob("*") if p.is_file())
    print(f"Snapshot directory created: {out_dir}")
    print(f"Encoded fragments: {next_chunk}; logical files: {len(entries)}")
    print(f"Artifact payload size: {total_bytes:,} bytes ({total_bytes / (1024 ** 2):.1f} MiB)")
    largest = sorted(
        ((meta["size"], name) for name, meta in entries.items() if name.startswith("home/")),
        reverse=True
    )[:20]
    if largest:
        print("Largest included files (original sizes):")
        for size, name in largest:
            print(f"  {size / (1024 ** 2):8.1f} MiB  {name}")
    print("No nested ZIP is created. GitHub will package this folder as the downloadable artifact.")
    print("The custom XOR encoding is obfuscation only; it is not secure encryption.")

def restore():
    if not SAVE_DIR.is_dir():
        print("No 'ubuntu saves' folder found; starting with a clean Ubuntu home.")
        return 0
    candidates = sorted(p for p in SAVE_DIR.glob("*.zip") if p.is_file())
    if not candidates:
        print("No ZIP snapshot in 'ubuntu saves'; starting with a clean Ubuntu home.")
        return 0
    if len(candidates) != 1:
        raise ValueError("Expected exactly one *.zip in 'ubuntu saves'; nothing was imported or deleted.")
    archive = candidates[0]
    with zipfile.ZipFile(archive, "r") as zf:
        bad = zf.testzip()
        if bad:
            raise ValueError(f"ZIP CRC check failed at {bad!r}; snapshot retained.")
        names = zf.namelist()
        if len(names) > 200000:
            raise ValueError("Snapshot contains too many entries; snapshot retained.")
        total = sum(info.file_size for info in zf.infolist())
        if total > MAX_UNPACKED:
            raise ValueError("Snapshot expands beyond 5 GiB; snapshot retained.")
        if "manifest.json" not in names:
            raise ValueError("Missing manifest.json; snapshot retained.")
        try:
            manifest = json.loads(zf.read("manifest.json"))
        except Exception as exc:
            raise ValueError(f"Invalid manifest JSON ({exc}); snapshot retained.")
        if manifest.get("format") != "immersive-cctv-ubuntu-session" or manifest.get("version") != VERSION:
            raise ValueError("Unsupported snapshot format/version; snapshot retained.")
        snapshot_mode = manifest.get("session_mode")
        requested_mode = os.environ.get("SESSION_MODE", "unknown")
        if snapshot_mode not in {"terminal", "desktop"}:
            raise ValueError(
                "Snapshot does not declare a recognized session_mode (terminal/desktop). "
                "It may be from an older format; ZIP retained. Select the matching mode only "
                "after creating a snapshot with the updated workflow."
            )
        if requested_mode not in {"terminal", "desktop"}:
            raise ValueError(f"Invalid requested SESSION_MODE {requested_mode!r}; snapshot retained.")
        if snapshot_mode != requested_mode:
            raise ValueError(
                f"Session mode mismatch: this ZIP was saved from '{snapshot_mode}', but this run "
                f"selected '{requested_mode}'. Choose '{snapshot_mode}' to restore it, or move "
                "the ZIP out of 'ubuntu saves' if you intentionally want a clean session. "
                "The ZIP has NOT been imported or deleted."
            )
        files = manifest.get("files")
        if not isinstance(files, dict) or manifest.get("file_count") != len(files):
            raise ValueError("Manifest file list/count mismatch; snapshot retained.")
        actual = set(names) - {"manifest.json", "SHA256SUMS.txt"}
        if actual != set(files):
            raise ValueError("Archive entries do not match manifest; snapshot retained.")
        verified = {}
        for name, meta in files.items():
            if not safe_member(name) or not (name.startswith("home/") or name == "metadata/apt-manual.txt"):
                raise ValueError(f"Unsafe or unsupported archive path {name!r}; snapshot retained.")
            info = zf.getinfo(name)
            unix_mode = (info.external_attr >> 16) & 0o170000
            if unix_mode == 0o120000:
                raise ValueError(f"Symlink entry {name!r} is not allowed; snapshot retained.")
            data = zf.read(name)
            if not isinstance(meta, dict) or meta.get("size") != len(data) or meta.get("sha256") != sha256_bytes(data):
                raise ValueError(f"Checksum/size mismatch for {name!r}; snapshot retained.")
            verified[name] = data

    package_data = verified.get("metadata/apt-manual.txt", b"").decode("utf-8")
    packages = sorted({line.strip() for line in package_data.splitlines()
                       if PACKAGE_RE.fullmatch(line.strip())})
    if packages:
        print(f"Restoring {len(packages)} recorded apt packages...")
        subprocess.run(["sudo", "apt-get", "update"], check=True)
        # Install in batches to avoid command-line length limits.
        for start in range(0, len(packages), 100):
            subprocess.run(["sudo", "apt-get", "install", "-y", "--no-install-recommends", *packages[start:start + 100]], check=True)

    for name, data in verified.items():
        if not name.startswith("home/"):
            continue
        rel = PurePosixPath(name).relative_to("home")
        if not safe_member(rel.as_posix()):
            raise ValueError(f"Unsafe home path {name!r}; snapshot retained.")
        target = (HOME / Path(*rel.parts)).resolve()
        if target != HOME and HOME not in target.parents:
            raise ValueError(f"Path escapes home directory: {name!r}; snapshot retained.")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

    # Signal the workflow that validation and restore completed; only then may it consume the ZIP.
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as f:
            f.write("restored=true\n")
            f.write(f"snapshot_name={archive.name}\n")
    print(f"Snapshot imported successfully from {archive.name}. The workflow may now remove it from the repository.")
    return 0

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"save", "restore"}:
        print("Usage: ubuntu-session-state.py save|restore", file=sys.stderr)
        sys.exit(2)
    try:
        if sys.argv[1] == "save":
            save()
        else:
            sys.exit(restore())
    except Exception as exc:
        print(f"::error::Ubuntu snapshot validation/import failed: {exc}", file=sys.stderr)
        sys.exit(1)
