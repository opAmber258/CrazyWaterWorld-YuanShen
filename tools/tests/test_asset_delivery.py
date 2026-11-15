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

    def accept_all(self):
        for check in delivery.DEPENDENCIES:
            delivery.attest(self.asset, check, "tester", "Blockbench test fixture", "Explicit simulated acceptance",
                            details=self.details if check == "native_reexport" else None)
        return delivery.read_json(self.asset / "validation.json")

    def migrate_historical_previews(self, published_on="2026-11-09"):
        for view in ("front", "back", "side", "oblique"):
            Image.new("RGBA", (640, 480), "black").save(self.asset / f"preview_{view}.png")
        self.put("validation.json", self.metadata | {"date": published_on, "status": "complete"})
        proof = {"published_on": published_on, "reference": "historical commit fixture",
                 "by": "tester", "note": "Geometry and previews unchanged since historical publication"}
        return delivery.refresh(self.asset, {"preview_legacy": proof}, self.root / "historical-before.json")

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

    def test_historical_previews_survive_migration_and_document_only_change(self):
        self.migrate_historical_previews()
        report = self.accept_all()
        self.put("README.md", "Description changed; geometry and previews untouched")
        self.assertEqual(self.state(report)["status"], "complete")
        with Image.open(self.asset / "preview_front.png") as image:
            self.assertEqual(image.size, (640, 480))

    def test_preview_exemption_rejects_cutoff_date(self):
        with self.assertRaisesRegex(ValueError, "pre-2026-11-10"):
            self.migrate_historical_previews("2026-11-10")
        self.assertFalse((self.root / "historical-before.json").exists())
        self.assertNotIn("schema_version", delivery.read_json(self.asset / "validation.json"))

    def test_historical_preview_exemption_expires_after_geometry_change(self):
        old = self.migrate_historical_previews()
        self.model["elements"][0]["to"][0] = 2
        self.put("pump.bbmodel", self.model)
        report = delivery.refresh(self.asset)
        self.assertTrue(any("1200x900" in item for item in report["unresolved"]))
        self.assertEqual(report["preview_legacy"]["inputs"], old["preview_legacy"]["inputs"])

    def test_historical_preview_exemption_expires_after_preview_replacement(self):
        self.migrate_historical_previews()
        Image.new("RGBA", (640, 480), "green").save(self.asset / "preview_front.png")
        report = delivery.refresh(self.asset)
        self.assertTrue(any("1200x900" in item for item in report["unresolved"]))

    def test_new_asset_cannot_claim_historical_preview_exemption(self):
        for view in ("front", "back", "side", "oblique"):
            Image.new("RGBA", (640, 480), "black").save(self.asset / f"preview_{view}.png")
        self.metadata["preview_legacy"] = {"published_on": "2026-11-09", "reference": "fixture",
                                            "by": "tester", "note": "Unsupported historical claim"}
        report = delivery.refresh(self.asset, self.metadata)
        self.assertTrue(any("1200x900" in item for item in report["unresolved"]))

    def test_missing_java_export_blocks_completion_and_card(self):
        self.model["meta"]["model_format"] = "java_block"
        self.put("pump.bbmodel", self.model)
        (self.asset / "pump.geo.json").unlink()
        report = self.ready()
        self.assertEqual(self.state(report)["status"], "incomplete")
        self.assertTrue(any("pump.json" in item for item in report["unresolved"]))
        with self.assertRaises(ValueError):
            delivery.card(self.asset, report, "a" * 40, "https://example.com/repo")

    def test_matching_java_export_satisfies_file_requirement(self):
        self.model["meta"]["model_format"] = "java_block"
        self.put("pump.bbmodel", self.model)
        (self.asset / "pump.geo.json").unlink()
        self.put("pump.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "test:pump"}})
        self.assertEqual(self.state(self.ready())["status"], "complete")

    def test_unrelated_json_does_not_satisfy_java_export_requirement(self):
        self.model["meta"]["model_format"] = "java_block"
        self.put("pump.bbmodel", self.model)
        self.put("other.json", {"parent": "minecraft:block/cube_all"})
        report = self.ready()
        self.assertEqual(self.state(report)["status"], "incomplete")

    def test_each_java_variant_needs_its_own_export(self):
        self.model["meta"]["model_format"] = "java_block"
        self.put("pump.bbmodel", self.model)
        self.put("pump_variant.bbmodel", self.model)
        self.put("pump.json", {"parent": "minecraft:block/cube_all"})
        report = self.ready()
        self.assertTrue(any("pump_variant.json" in item for item in report["unresolved"]))

    def test_geometry_attestation_cannot_refresh_unrelated_texture_baseline(self):
        report = self.ready()
        baseline = dict(report["inputs"])
        measurements = report["measurements"]
        self.paint("blue")
        updated = delivery.attest(self.asset, "native_reexport", "tester", "fixture", "Geometry-only check",
                                   details=self.details)
        self.assertEqual(updated["inputs"], baseline)
        self.assertEqual(updated["measurements"], measurements)
        with self.assertRaisesRegex(ValueError, "Inputs changed"):
            delivery.attest(self.asset, "editor_reopen", "tester", "fixture", "Attempt without refresh")
        with patch("sys.stdout", new_callable=io.StringIO) as output:
            self.assertEqual(delivery.main(["status", str(self.asset), "--full"]), 1)
            expanded = json.loads(output.getvalue())
        self.assertEqual(expanded["inputs"], baseline)
        self.assertNotEqual(expanded["current_inputs"]["textures"], baseline["textures"])
        refreshed = delivery.refresh(self.asset)
        self.assertNotEqual(refreshed["inputs"]["textures"], baseline["textures"])
        accepted = delivery.attest(self.asset, "editor_reopen", "tester", "fixture", "Rechecked after refresh")
        self.assertEqual(accepted["checks"]["editor_reopen"], "passed")


if __name__ == "__main__":
    unittest.main()
