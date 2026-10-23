#!/usr/bin/env python3
"""Refresh the bundled catalogue from Vosk's official machine-readable list."""

from __future__ import annotations

import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen


CATALOGUE_URL = "https://alphacephei.com/vosk/models/model-list.json"
OUTPUT_PATH = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/atri/palaash/jvosk/models/model-list.json"
)
MAX_CATALOGUE_BYTES = 2_000_000
MD5_PATTERN = re.compile(r"^[0-9a-fA-F]{32}$")


def validate_catalogue(raw: bytes, response_url: str) -> None:
    """Reject unexpected or unsafe upstream data before replacing the snapshot."""
    if len(raw) > MAX_CATALOGUE_BYTES:
        raise ValueError(f"Catalogue exceeds {MAX_CATALOGUE_BYTES} bytes")

    parsed_url = urlparse(response_url)
    host = (parsed_url.hostname or "").lower()
    if parsed_url.scheme != "https" or not (
        host == "alphacephei.com" or host.endswith(".alphacephei.com")
    ):
        raise ValueError(f"Unexpected catalogue response URL: {response_url}")

    entries = json.loads(raw)
    if not isinstance(entries, list) or not entries:
        raise ValueError("Catalogue must be a non-empty JSON array")

    names: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"Entry {index} must be a JSON object")

        name = entry.get("name")
        if not isinstance(name, str) or not re.fullmatch(
            r"vosk-[A-Za-z0-9][A-Za-z0-9._-]{0,194}", name
        ) or ".." in name:
            raise ValueError(f"Entry {index} has an unsafe model name")
        if name in names:
            raise ValueError(f"Duplicate model name: {name}")
        names.add(name)

        model_url = urlparse(entry.get("url", ""))
        model_host = (model_url.hostname or "").lower()
        if model_url.scheme != "https" or not (
            model_host == "alphacephei.com" or model_host.endswith(".alphacephei.com")
        ):
            raise ValueError(f"Entry {name} has an untrusted download URL")

        size = entry.get("size")
        if not isinstance(size, int) or isinstance(size, bool) or size < 0:
            raise ValueError(f"Entry {name} has an invalid archive size")

        checksum = entry.get("md5")
        if checksum is not None and (
            not isinstance(checksum, str) or not MD5_PATTERN.fullmatch(checksum)
        ):
            raise ValueError(f"Entry {name} has an invalid MD5 checksum")


def update_catalogue() -> Path:
    """Fetch, validate and atomically replace the bundled JSON snapshot."""
    request = Request(
        CATALOGUE_URL,
        headers={
            "Accept": "application/json",
            "User-Agent": "jvosk-catalogue-snapshot-updater/1.0",
        },
    )
    with urlopen(request, timeout=30) as response:
        raw = response.read(MAX_CATALOGUE_BYTES + 1)
        final_url = response.geturl()

    validate_catalogue(raw, final_url)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb", dir=OUTPUT_PATH.parent, prefix=".model-list-", suffix=".tmp", delete=False
        ) as temporary_file:
            temporary_path = temporary_file.name
            temporary_file.write(raw)
            temporary_file.flush()
            os.fsync(temporary_file.fileno())
        os.replace(temporary_path, OUTPUT_PATH)
        temporary_path = None
    finally:
        if temporary_path is not None:
            try:
                os.unlink(temporary_path)
            except FileNotFoundError:
                pass

    return OUTPUT_PATH


if __name__ == "__main__":
    updated_path = update_catalogue()
    print(f"Updated {updated_path}")
