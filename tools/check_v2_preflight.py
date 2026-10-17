"""Validate evidence separately from the V2 generation gate. Never authorizes worldgen."""
import argparse
import hashlib
import json
from pathlib import Path

DIGEST = "521fc8d6e854570ae79f851129109d5948943f9ec372aee50643939d840ad2eb"
SEEDS = (123456789, 8675309, 20261011)


def check(root, require_pass=False):
    manifest = root / "docs/verification/spatial-retune/v2/EXPERIMENT_MANIFEST.json"
    text = manifest.read_text(encoding="utf-8").replace("\r\n", "\n")
    if hashlib.sha256(text.encode()).hexdigest() != DIGEST:
        raise ValueError("FROZEN_MANIFEST_MISMATCH")
    blocked = []
    for index, seed in enumerate(SEEDS):
        file = root / f"run/v2-numerical-preflight/seed-{index}/v2-preparation.json"
        report = json.loads(file.read_text(encoding="utf-8"))
        if report.get("executionStatus") == "NOT_RUN_AFTER_BUDGET_FAILURE":
            if not blocked:
                raise ValueError(f"seed {seed}: unjustified skipped evidence")
            blocked.append(f"seed {seed}: NOT_RUN_AFTER_BUDGET_FAILURE")
            continue
        if report.get("executionStatus") not in ("COMPLETED", "BLOCKED"):
            raise ValueError(f"seed {seed}: INCOMPLETE_EXECUTION")
        if report.get("seed") != seed or report.get("manifest") != DIGEST:
            raise ValueError(f"seed {seed}: PROVENANCE_MISMATCH")
        if report.get("v2WorldsCreated") != 0 or report.get("holdoutsSampled") is not False:
            raise ValueError(f"seed {seed}: SCOPE_VIOLATION")
        if report.get("v2TerrainGenerationAuthorized") is not False:
            raise ValueError(f"seed {seed}: unexpected generation authorization")
        if report.get("numericGate") not in ("PASS", "BLOCKED_R1_ROUNDED_LIPSCHITZ_CONTRACT"):
            raise ValueError(f"seed {seed}: INVALID_NUMERIC_GATE")
        if report.get("executionStatus") == "COMPLETED" and report.get("failure"):
            raise ValueError(f"seed {seed}: CONTRADICTORY_FAILURE")
        if report.get("cacheEntriesAfterClose") != 0:
            raise ValueError(f"seed {seed}: CACHE_NOT_CLOSED")
        ticks = report["ticks"]
        if not ticks or any(t["calls"] < 0 or t["calls"] > 512 or t["wallNanos"] < 0 for t in ticks):
            raise ValueError(f"seed {seed}: INVALID_TICKS")
        if sum(t["calls"] for t in ticks) != report["rawCalls"] or report["rawCalls"] > 3484800:
            raise ValueError(f"seed {seed}: RAW_CALL_BUDGET_OR_COUNT")
        if report["targetNodes"] != 477:
            raise ValueError(f"seed {seed}: FROZEN_ROI_TARGET_COUNT")
        if report["completedNodes"] * 4225 > report["rawCalls"]:
            raise ValueError(f"seed {seed}: IMPOSSIBLE_CERTIFICATE_CALL_COUNT")
        if sum(report["rejections"].values()) + report.get("enabledNodes", 0) != report["completedNodes"]:
            raise ValueError(f"seed {seed}: MISSING_CERTIFICATE_OUTCOMES")
        if not 0 <= report["completedNodes"] <= report["targetNodes"] <= 800:
            raise ValueError(f"seed {seed}: NODE_COUNT")
        completed = report["executionStatus"] == "COMPLETED"
        if completed and (report["completedNodes"] != report["targetNodes"] or any(t["wallNanos"] > 25000000 for t in ticks)):
            raise ValueError(f"seed {seed}: FALSE_COMPLETED_STATUS")
        if not completed and not report.get("failure"):
            raise ValueError(f"seed {seed}: MISSING_FAILURE_REASON")
        if not completed or report.get("numericGate") != "PASS" or report.get("enabledNodes", 0) == 0:
            blocked.append(f"seed {seed}: {report.get('failure', report.get('numericGate', 'MISSING_NUMERIC_GATE'))}")
    if require_pass and blocked:
        raise ValueError("V2_GENERATION_GATE_BLOCKED: " + "; ".join(blocked))
    return {"evidence": "COMPLETE", "generationGate": "BLOCKED" if blocked else "PASS", "reasons": blocked}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--require-pass", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.root, args.require_pass), indent=2))
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"V2_PREFLIGHT_FAIL: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
