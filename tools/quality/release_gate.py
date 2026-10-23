#!/usr/bin/env python3
"""Check an existing release artifact and optional measured quality report."""

import argparse
import json
from pathlib import Path
from typing import Optional

MAX_CRASH_RATE = 0.01
MAX_ANR_RATE = 0.005
MAX_STARTUP_P95_MS = 2500
MAX_FRAME_P95_MS = 32


def check(bundle: Path, max_bytes: int, metrics: Optional[Path] = None) -> list[str]:
    issues = []
    if not bundle.is_file():
        return [f"Missing release artifact: {bundle}"]
    if bundle.stat().st_size > max_bytes:
        issues.append(f"Bundle is {bundle.stat().st_size:,} bytes; budget is {max_bytes:,}")
    if metrics is not None:
        report = json.loads(metrics.read_text(encoding="utf-8"))
        limits = {
            "crash_rate": MAX_CRASH_RATE,
            "anr_rate": MAX_ANR_RATE,
            "startup_p95_ms": MAX_STARTUP_P95_MS,
            "frame_p95_ms": MAX_FRAME_P95_MS,
        }
        for name, maximum in limits.items():
            value = report.get(name)
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                issues.append(f"Missing numeric quality metric: {name}")
            elif value < 0 or value > maximum:
                issues.append(f"{name}={value} exceeds budget {maximum}")
        if not isinstance(report.get("sample_count"), int) or report["sample_count"] < 100:
            issues.append("Quality report requires at least 100 measured samples")
    return issues


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path, help="path to an already built AAB")
    parser.add_argument("--max-bytes", type=int, default=83_886_080)
    parser.add_argument("--metrics", type=Path, help="optional measured JSON quality report")
    args = parser.parse_args()
    issues = check(args.bundle, args.max_bytes, args.metrics)
    if issues:
        parser.exit(1, "\n".join(issues) + "\n")
    print("Release quality gate passed")


if __name__ == "__main__":
    main()
