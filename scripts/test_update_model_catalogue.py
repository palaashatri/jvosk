"""Unit tests for the bundled-catalogue refresh script."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("update-model-catalogue.py")
SPEC = importlib.util.spec_from_file_location("update_model_catalogue", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
updater = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(updater)


def valid_entry() -> dict[str, object]:
    return {
        "name": "vosk-model-small-en-us-0.15",
        "url": "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        "size": 41_205_931,
        "md5": "09ab50ccd62b674cbaa231b825f9c1cb",
    }


class UpdateModelCatalogueTest(unittest.TestCase):
    def test_valid_catalogue_is_accepted(self) -> None:
        raw = json.dumps([valid_entry()]).encode()
        updater.validate_catalogue(raw, updater.CATALOGUE_URL)

    def test_rejects_unsafe_entries(self) -> None:
        invalid = valid_entry() | {"name": "vosk-../../outside"}
        with self.assertRaises(ValueError):
            updater.validate_catalogue(json.dumps([invalid]).encode(), updater.CATALOGUE_URL)

    def test_rejects_duplicate_names_and_untrusted_urls(self) -> None:
        raw = json.dumps([valid_entry(), valid_entry()]).encode()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            updater.validate_catalogue(raw, updater.CATALOGUE_URL)

        invalid = valid_entry() | {"url": "https://example.org/model.zip"}
        with self.assertRaisesRegex(ValueError, "untrusted"):
            updater.validate_catalogue(json.dumps([invalid]).encode(), updater.CATALOGUE_URL)

    def test_does_not_replace_snapshot_when_validation_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            destination = Path(temp_dir) / "model-list.json"
            destination.write_bytes(b"existing snapshot")

            class Response:
                def __enter__(self):
                    return self

                def __exit__(self, *_args):
                    return False

                def read(self, _limit: int) -> bytes:
                    return json.dumps([valid_entry() | {"name": "../bad"}]).encode()

                def geturl(self) -> str:
                    return updater.CATALOGUE_URL

            with patch.object(updater, "OUTPUT_PATH", destination), patch.object(
                updater, "urlopen", return_value=Response()
            ):
                with self.assertRaises(ValueError):
                    updater.update_catalogue()

            self.assertEqual(b"existing snapshot", destination.read_bytes())
            self.assertEqual(["model-list.json"], [path.name for path in Path(temp_dir).iterdir()])


if __name__ == "__main__":
    unittest.main()
