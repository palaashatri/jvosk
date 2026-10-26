#!/usr/bin/env python3
"""Build jpackage installers (and a zipped app-image) from the shaded jar."""

from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


def jpackage_bin() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / ("jpackage.exe" if os.name == "nt" else "jpackage")
        if candidate.exists():
            return str(candidate)
    found = shutil.which("jpackage")
    if not found:
        raise SystemExit("jpackage not found on PATH or JAVA_HOME/bin")
    return found


def run(cmd: list[str]) -> None:
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True)


def zip_dir(source: Path, zip_path: Path) -> None:
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in source.rglob("*"):
            archive.write(path, path.relative_to(source.parent))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path, help="Shaded application jar")
    parser.add_argument("--version", required=True, help="App version without a leading v")
    parser.add_argument("--dest", required=True, type=Path, help="Output directory")
    parser.add_argument("--name", default="jvosk")
    parser.add_argument("--main-class", default="atri.palaash.jvosk.App")
    args = parser.parse_args()

    jar = args.jar.resolve()
    if not jar.is_file():
        raise SystemExit(f"jar not found: {jar}")

    dest = args.dest.resolve()
    dest.mkdir(parents=True, exist_ok=True)
    input_dir = dest / "jpackage-input"
    if input_dir.exists():
        shutil.rmtree(input_dir)
    input_dir.mkdir(parents=True)
    shutil.copy2(jar, input_dir / jar.name)

    system = platform.system().lower()
    native_type = {"darwin": "dmg", "linux": "deb", "windows": "msi"}.get(system, "app-image")

    common = [
        jpackage_bin(),
        "--name", args.name,
        "--app-version", args.version,
        "--input", str(input_dir),
        "--main-jar", jar.name,
        "--main-class", args.main_class,
        "--dest", str(dest),
        "--vendor", "Palaash Atri",
        "--description", "Offline speech-to-text with Vosk",
        "--copyright", "Copyright (c) Palaash Atri",
    ]

    image_dir = dest / "app-image"
    if image_dir.exists():
        shutil.rmtree(image_dir)
    image_dir.mkdir()
    run(common + ["--type", "app-image", "--dest", str(image_dir)])
    zip_path = dest / f"{args.name}-{args.version}-{system}-app-image.zip"
    # jpackage writes NAME/ under dest
    produced = next(image_dir.iterdir())
    zip_dir(produced, zip_path)
    print(f"Wrote {zip_path}")

    try:
        run(common + ["--type", native_type])
        print(f"Wrote native installer type {native_type} under {dest}")
    except subprocess.CalledProcessError as error:
        print(f"Native installer ({native_type}) skipped: {error}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
