import importlib.util
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("apk_inspector.py")
SPEC = importlib.util.spec_from_file_location("apk_inspector", MODULE_PATH)
apk_inspector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(apk_inspector)


class ApkInspectorTest(unittest.TestCase):
    def test_dex_header_counts(self):
        dex = bytearray(112)
        dex[:8] = b"dex\n035\0"
        struct.pack_into("<I", dex, 88, 321)
        struct.pack_into("<I", dex, 96, 45)
        self.assertEqual((321, 45), apk_inspector.dex_counts(bytes(dex)))

    def test_inspects_existing_apk_entries_and_issues(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libsample.so", b"native")
                archive.writestr("lib/armeabi-v7a/libsample.so", b"native")
                archive.writestr("assets/config.json", b"{}", compress_type=zipfile.ZIP_STORED)
                archive.writestr("assets/copy.json", b"{}")
            report = apk_inspector.inspect_apk(apk, large_file_bytes=1)
            self.assertEqual(["arm64-v8a", "armeabi-v7a"], report["abis"])
            self.assertIn("multiple_abis", {issue["rule"] for issue in report["issues"]})
            self.assertIn("duplicate_file_content", {issue["rule"] for issue in report["issues"]})
            self.assertEqual(4, report["entry_count"])
            self.assertTrue(report["resource_candidate_note"].startswith("Heuristic only"))
            self.assertIn("assets", apk_inspector.render_html(report))

    def test_rejects_non_zip_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "not-an-apk"
            path.write_text("nope", encoding="utf-8")
            with self.assertRaises(ValueError):
                apk_inspector.inspect_apk(path)


if __name__ == "__main__":
    unittest.main()
