"""Behavioral tests: invalidation, preserved evidence, migration and publication safety."""
import base64
import importlib.util
import io
import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from PIL import Image

SPEC = importlib.util.spec_from_file_location("asset_delivery", Path(__file__).parents[1] / "asset_delivery.py")
delivery = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(delivery)


class AssetDeliveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.asset = self.root / "pump_20261115"
        self.asset.mkdir()
        (self.asset / "reference").mkdir()
        self.put("README.md", "Human design notes")
        self.put("reference/palette.json", {})
        for view in ("front", "back", "side", "oblique"):
            Image.new("RGBA", (1200, 900), "black").save(self.asset / f"preview_{view}.png")
        self.model = {"meta": {"model_format": "bedrock"}, "model_identifier": "pump",
                      "elements": [{"type": "cube", "from": [0, 0, 0], "to": [1, 1, 1]}],
                      "groups": [{"name": "root"}], "textures": []}
        self.paint("red")
        self.put("pump.geo.json", {"minecraft:geometry": [{"description": {"identifier": "geometry.pump"},
                                                          "bones": [{"name": "root", "cubes": [{}]}]}]})
        self.metadata = {"asset_id": "pump", "display_name_zh": "泵", "display_name_en": "Pump",
                         "sources": {"zh": "fixture Chinese XML", "en": "fixture English XML"},
                         "creation_source": "人工（tester）", "contributors": {"human": ["tester"],
                         "ai": [], "division": "Human created the fixture"}}
        self.details = {"comparison": "equal", "bounds": {"min": [0, 0, 0], "max": [1, 1, 1], "unit": "bb_unit"}}

    def put(self, name, data):
        (self.asset / name).write_text(json.dumps(data) if not isinstance(data, str) else data, encoding="utf-8")

    def paint(self, color):
        image = Image.new("RGBA", (16, 16), color)
        image.save(self.asset / "pump_tex.png")
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        self.model["textures"] = [{"source": "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()}]
        self.put("pump.bbmodel", self.model)

    def ready(self):
        delivery.refresh(self.asset, self.metadata)
        for check in delivery.DEPENDENCIES:
            delivery.attest(self.asset, check, "tester", "Blockbench test fixture", "Explicit simulated acceptance",
                            details=self.details if check == "native_reexport" else None)
        return delivery.read_json(self.asset / "validation.json")

    def state(self, report):
        return delivery.evaluate(self.asset, report)

    def test_fresh_report_never_infers_native_or_visual_pass(self):
        report = delivery.refresh(self.asset, self.metadata)
        self.assertEqual(report["status"], "incomplete")
        self.assertEqual(set(report["checks"].values()), {"unverified"})
        self.assertEqual(report["measurements"]["projects"][0]["cubes"], 1)

    def test_document_change_reuses_all_acceptance(self):
        report = self.ready()
        self.put("README.md", "Updated explanation only")
        self.assertEqual(self.state(report)["status"], "complete")

    def test_geometry_change_with_same_cube_count_invalidates_all(self):
        report = self.ready()
        self.model["elements"][0]["to"][0] = 2
        self.put("pump.bbmodel", self.model)
        self.assertEqual(set(self.state(report)["checks"].values()), {"stale"})

    def test_unrecognized_export_fields_invalidate_native_acceptance(self):
        report = self.ready()
        self.model["export_extension"] = {"scale": 2}
        self.put("pump.bbmodel", self.model)
        self.assertEqual(self.state(report)["checks"]["native_reexport"], "stale")

    def test_texture_change_keeps_native_geometry_acceptance(self):
        report = self.ready()
        self.paint("blue")
        states = self.state(report)["checks"]
        self.assertEqual(states["native_reexport"], "passed")
        self.assertEqual(states["editor_reopen"], "stale")
        self.assertEqual(states["visual_preview"], "stale")

    def test_uv_layout_change_invalidates_native_acceptance(self):
        report = self.ready()
        self.model["textures"][0]["uv_width"] = 32
        self.put("pump.bbmodel", self.model)
        self.assertEqual(self.state(report)["checks"]["native_reexport"], "stale")

    def test_extra_state_texture_is_tracked_and_cannot_escape_asset(self):
        Image.new("RGBA", (16, 16), "red").save(self.asset / "reference/half.png")
        self.metadata["tracked_files"] = {"textures": ["reference/half.png"]}
        report = self.ready()
        Image.new("RGBA", (16, 16), "blue").save(self.asset / "reference/half.png")
        self.assertEqual(self.state(report)["checks"]["visual_preview"], "stale")
        self.assertEqual(self.state(report)["checks"]["native_reexport"], "passed")
        report["tracked_files"] = {"textures": ["../outside.png"]}
        with self.assertRaises(ValueError):
            self.state(report)

    def test_preview_change_invalidates_only_visual_acceptance(self):
        report = self.ready()
        Image.new("RGBA", (1200, 900), "green").save(self.asset / "preview_front.png")
        self.assertEqual(self.state(report)["checks"], {"editor_reopen": "passed", "native_reexport": "passed", "visual_preview": "stale"})

    def test_missing_file_blocks_previously_complete_report_and_card(self):
        report = self.ready()
        (self.asset / "pump.geo.json").unlink()
        self.assertEqual(self.state(report)["status"], "incomplete")
        with self.assertRaises(ValueError):
            delivery.card(self.asset, report, "a" * 40, "https://example.com/repo")

    def test_status_does_not_write_cached_report(self):
        report = self.ready()
        before = (self.asset / "validation.json").read_bytes()
        self.model["elements"].clear()
        self.put("pump.bbmodel", self.model)
        self.assertEqual(delivery.summary(self.asset, report)["status"], "incomplete")
        self.assertEqual((self.asset / "validation.json").read_bytes(), before)

    def test_attestation_refuses_changed_inputs_or_missing_evidence(self):
        self.ready()
        self.paint("green")
        with self.assertRaisesRegex(ValueError, "Inputs changed"):
            delivery.attest(self.asset, "editor_reopen", "tester", "BB", "Reopened")
        with self.assertRaises(ValueError):
            delivery.attest(self.asset, "native_reexport", "tester", "BB", "Compared")
        with self.assertRaises(ValueError):
            delivery.attest(self.asset, "editor_reopen", " ", "BB", " ")

    def test_legacy_migration_backs_up_and_does_not_inherit_passes(self):
        old = self.metadata | {"status": "complete", "checks": {"bbmodel_reopens": True}}
        self.put("validation.json", old)
        original = (self.asset / "validation.json").read_bytes()
        with self.assertRaises(ValueError):
            delivery.refresh(self.asset)
        self.assertEqual((self.asset / "validation.json").read_bytes(), original)
        backup = self.root / "backup.json"
        report = delivery.refresh(self.asset, legacy_backup=backup)
        self.assertEqual(backup.read_bytes(), original)
        self.assertEqual(report["status"], "incomplete")
        self.assertEqual(set(report["checks"].values()), {"unverified"})

    def test_forged_native_pass_without_bounds_is_unverified(self):
        report = self.ready()
        del report["acceptance"]["native_reexport"]["details"]
        self.assertEqual(self.state(report)["checks"]["native_reexport"], "unverified")

    def test_mismatched_embedded_pixels_are_detected(self):
        report = self.ready()
        Image.new("RGBA", (16, 16), "blue").save(self.asset / "pump_tex.png")
        self.assertIn("embedded texture differs from external PNG", self.state(report)["unresolved"])

    def test_card_links_to_fixed_commit_and_has_no_copied_counts(self):
        report = self.ready()
        with patch.object(delivery, "ROOT", self.root):
            text = delivery.card(self.asset, report, "a" * 40, "https://example.com/repo")
        self.assertIn("/blob/" + "a" * 40 + "/", text)
        self.assertIn("validation.json", text)
        self.assertNotIn("cubes", text)
        self.assertNotIn("/main/", text)


if __name__ == "__main__":
    unittest.main()
