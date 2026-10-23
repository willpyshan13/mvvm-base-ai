#!/usr/bin/env python3
"""Inspect an existing APK and write a JSON size/quality report; does not build or modify APKs."""

from __future__ import annotations

import argparse
import html
import hashlib
import json
import re
import struct
import zipfile
from collections import defaultdict
from pathlib import Path


def dex_counts(data: bytes) -> tuple[int, int]:
    if len(data) < 112 or not data.startswith(b"dex\n"):
        return 0, 0
    method_count = struct.unpack_from("<I", data, 88)[0]
    class_count = struct.unpack_from("<I", data, 96)[0]
    return method_count, class_count


def png_has_alpha(data: bytes) -> bool | None:
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 26:
        return None
    color_type = data[25]
    if color_type in (4, 6):
        return True
    if color_type in (0, 2):
        return False
    offset = 8
    while offset + 12 <= len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        chunk = data[offset + 4:offset + 8]
        if chunk == b"tRNS":
            return True
        offset += 12 + size
        if chunk == b"IEND":
            break
    return False


def inspect_apk(apk_path: Path, large_file_bytes: int = 1_000_000) -> dict:
    if not zipfile.is_zipfile(apk_path):
        raise ValueError(f"Not a readable APK/ZIP file: {apk_path}")
    with zipfile.ZipFile(apk_path) as archive:
        entries = [item for item in archive.infolist() if not item.is_dir()]
        large_files = [
            {"path": item.filename, "bytes": item.file_size}
            for item in entries if item.file_size >= large_file_bytes
        ]
        large_files.sort(key=lambda item: item["bytes"], reverse=True)
        abi_names = sorted({
            parts[1] for item in entries
            if len(parts := item.filename.split("/")) >= 3 and parts[0] == "lib"
        })
        uncompressed = sorted(
            item.filename for item in entries
            if item.compress_type == zipfile.ZIP_STORED
            and Path(item.filename).suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".json", ".xml"}
        )
        duplicate_hashes: dict[str, list[str]] = defaultdict(list)
        no_alpha_pngs = []
        method_count = 0
        class_count = 0
        r_class_descriptors = set()
        resource_names = defaultdict(list)
        dex_strings = set()
        dex_names = [item for item in entries if re.fullmatch(r"classes(?:\d+)?\.dex", item.filename)]
        for item in entries:
            data = archive.read(item)
            duplicate_hashes[hashlib.sha256(data).hexdigest()].append(item.filename)
            if item.filename.lower().endswith(".png") and png_has_alpha(data) is False:
                no_alpha_pngs.append({"path": item.filename, "bytes": item.file_size})
            if item in dex_names:
                methods, classes = dex_counts(data)
                method_count += methods
                class_count += classes
                text = data.decode("latin1", errors="ignore")
                r_class_descriptors.update(re.findall(r"L[^\x00\s;]+/R\$[^\x00\s;]+;", text))
                dex_strings.update(re.findall(r"[a-z][a-z0-9_]{2,}", text))
            if item.filename.startswith("res/"):
                resource_names[Path(item.filename).stem].append(item.filename)
        duplicates = [sorted(names) for names in duplicate_hashes.values() if len(names) > 1]
        issues = []
        if len(abi_names) > 1:
            issues.append({"rule": "multiple_abis", "detail": abi_names})
        if no_alpha_pngs:
            issues.append({"rule": "png_without_alpha", "detail": no_alpha_pngs})
        if uncompressed:
            issues.append({"rule": "compressible_files_stored", "detail": uncompressed})
        if duplicates:
            issues.append({"rule": "duplicate_file_content", "detail": duplicates})
        duplicate_resources = [paths for paths in resource_names.values() if len(paths) > 1]
        if duplicate_resources:
            issues.append({"rule": "duplicate_resource_basename", "detail": duplicate_resources})
        resource_candidates = sorted(
            name for name in resource_names if name not in dex_strings and not name.startswith("abc_")
        )
        return {
            "apk": apk_path.name,
            "apk_bytes": apk_path.stat().st_size,
            "entry_count": len(entries),
            "dex_files": [item.filename for item in dex_names],
            "method_count": method_count,
            "class_count": class_count,
            "r_class_count": len(r_class_descriptors),
            "abi_count": len(abi_names),
            "abis": abi_names,
            "large_files": large_files,
            "resource_basename_candidates_not_found_in_dex_strings": resource_candidates,
            "resource_candidate_note": "Heuristic only: reflection, XML references, navigation, and generated resource IDs can be missed.",
            "issues": issues,
        }


def render_html(report: dict) -> str:
    rows = "".join(
        f"<tr><td>{html.escape(issue['rule'])}</td><td><pre>{html.escape(json.dumps(issue['detail'], ensure_ascii=False, indent=2))}</pre></td></tr>"
        for issue in report["issues"]
    )
    resources = "".join(f"<li>{html.escape(name)}</li>" for name in report["resource_basename_candidates_not_found_in_dex_strings"])
    return (
        "<!doctype html><meta charset='utf-8'><title>APK inspection</title>"
        "<style>body{font:15px system-ui;max-width:1000px;margin:2rem auto;padding:0 1rem}"
        "table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:.5rem;vertical-align:top}pre{white-space:pre-wrap}</style>"
        f"<h1>{html.escape(report['apk'])}</h1><p>{report['apk_bytes']:,} bytes · {report['method_count']:,} DEX methods · {report['class_count']:,} classes</p>"
        f"<h2>Findings</h2><table><tr><th>Rule</th><th>Details</th></tr>{rows}</table>"
        f"<h2>Resource reachability candidates (heuristic)</h2><ul>{resources}</ul>"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="path to an already existing APK")
    parser.add_argument("--output", type=Path, default=Path("apm-apk-report.json"))
    parser.add_argument("--large-file-bytes", type=int, default=1_000_000)
    parser.add_argument("--html", type=Path, help="also write a human-readable HTML report")
    args = parser.parse_args()
    report = inspect_apk(args.apk, args.large_file_bytes)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"APK report written: {args.output} ({len(report['issues'])} issue categories)")
    if args.html:
        args.html.write_text(render_html(report), encoding="utf-8")
        print(f"HTML report written: {args.html}")


if __name__ == "__main__":
    main()
