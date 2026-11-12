"""Validate immutable B3 evidence; --require-pass stays nonzero for any blocked gate."""
import argparse
import hashlib
import json
from pathlib import Path

DIGEST = "82a3e9676d7de3e223299feff88c7a9ea67e8e4e1c25bb1f3aa134deb59ec9bf"
SEEDS = (123456789, 8675309, 20261011)
WINDOWS = ((-384, 896), (1344, 1200))
DIRECTORY = Path("docs/verification/spatial-retune/v2/r2")

def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()

def validate_row(row):
    if row["manifest"] != DIGEST or row["seed"] not in SEEDS:
        raise ValueError("PROVENANCE_OR_SEED_MISMATCH")
    if not isinstance(row["sourceCommit"], str) or len(row["sourceCommit"]) != 40:
        raise ValueError("MISSING_SOURCE_COMMIT")
    for key in ("worldGenerationAuthorized", "holdoutsSampled", "fullPairValidated", "shiftPositionFeasible"):
        if row[key] is not False:
            raise ValueError("FORBIDDEN_AUTHORIZATION_OR_SCOPE: " + key)
    if row["v2WorldsCreated"] != 0 or row["preflightFullChunkRequests"] != 0 or row["routeCompleted"] != 0:
        raise ValueError("FORBIDDEN_WORLD_FULL_OR_ROUTE_CLAIM")
    complete = row["executionStatus"] == "PREPARATION_COMPLETED"
    if not complete and (row["executionStatus"] != "FAILED" or not row["failure"]):
        raise ValueError("INCOMPLETE_OR_UNEXPLAINED_EXECUTION")
    if complete and row["failure"]:
        raise ValueError("FALSE_COMPLETED")
    if row["anchors"] != 132 or len(row["nodes"]) != 132:
        raise ValueError("ANCHOR_SET_COUNT")
    ids = [(n["id"]["i"], n["id"]["j"]) for n in row["nodes"]]
    if len(set(ids)) != 132:
        raise ValueError("DUPLICATE_ANCHOR_IDS")
    ready = [n for n in row["nodes"] if n["status"] == "MATH_NODE_READY"]
    if (not complete and ready) or row["publishedMathNodes"] != len(ready):
        raise ValueError("PARTIAL_READY_PUBLICATION")
    if len(row["shortlistIds"]) != row["shortlistedNodes"] or row["shortlistedNodes"] > 16:
        raise ValueError("SHORTLIST_COUNT")
    for key in ("rawCalls", "cacheHits", "cacheMisses", "heightEvaluations", "smoothValleyEdgeTests", "intervalDerivativeWork", "intervalJetConstructions", "ownedAllocationBytes", "activePreparationCpuNanos", "scheduledWallNanos", "maxTickWallNanos"):
        if not isinstance(row[key], int) or row[key] < 0:
            raise ValueError("MISSING_OR_INVALID_METRIC: " + key)
    if row["rawCalls"] != row["cacheMisses"] or sum(t["rawCalls"] for t in row["ticks"]) != row["rawCalls"]:
        raise ValueError("RAW_ACCOUNTING")
    if row["tickCount"] != len(row["ticks"]) + 1:
        raise ValueError("MISSING_INITIALIZATION_SLICE")
    caps = {"rawCalls": 52048, "heightEvaluations": 95744, "smoothValleyEdgeTests": 4691456,
            "ownedAllocationBytes": 268435456, "activePreparationCpuNanos": 1500000000,
            "scheduledWallNanos": 15000000000, "maxTickWallNanos": 25000000, "tickCount": 256}
    if complete and (any(row[k] > cap for k, cap in caps.items()) or any(t["rawCalls"] > 2048 for t in row["ticks"])):
        raise ValueError("FALSE_COMPLETED_OVER_BUDGET")
    if complete and (row.get("retainedContextBytes", 33554433) > 33554432 or row["examinedAnchors"] != 132):
        raise ValueError("FALSE_COMPLETED_MEMORY_OR_ANCHORS")
    if len(row["windows"]) != 2:
        raise ValueError("MISSING_WINDOW")
    expected_yield = complete
    for window, expected in zip(row["windows"], WINDOWS):
        if (window["x"], window["z"]) != expected:
            raise ValueError("WINDOW_MISMATCH")
        count = sum(abs(n["center"]["x"]-expected[0]) <= 1344 and abs(n["center"]["z"]-expected[1]) <= 1344 for n in ready)
        label = "FAIL_INCOMPLETE_PREPARATION" if not complete else "PASS" if count >= 2 else "FAIL_INSUFFICIENT_NODE_YIELD"
        if window["mathNodes"] != count or window["gate"] != label:
            raise ValueError("WINDOW_GATE_MISMATCH")
        expected_yield &= count >= 2
    if row["nodeYieldGate"] != ("PASS" if expected_yield else "FAIL"):
        raise ValueError("YIELD_GATE_MISMATCH")
    return complete and expected_yield and row["nativeNumericValidated"] is True

def check(root, require_pass=False):
    directory = root / DIRECTORY
    if digest(directory / "EXPERIMENT_MANIFEST.json") != DIGEST:
        raise ValueError("R2_FREEZE_MISMATCH")
    index = json.loads((directory / "B3_EVIDENCE.json").read_text(encoding="utf-8"))
    if index["manifestSha256"] != DIGEST or index["outcome"]["worldGenerationAuthorized"] is not False or index["outcome"]["holdoutsSampled"] is not False:
        raise ValueError("INDEX_PROVENANCE_OR_AUTHORIZATION_MISMATCH")
    for file, expected in index["protectedSha256LF"].items():
        if digest(root / file) != expected:
            raise ValueError("HISTORICAL_INPUT_CHANGED: " + file)
    for file, expected in index["runtimeSourceSha256LF"].items():
        if digest(root / file) != expected:
            raise ValueError("RUNTIME_SOURCE_CHANGED: " + file)
    for file, expected in index.get("additionalValidationSourceSha256LF", {}).items():
        if digest(root / file) != expected:
            raise ValueError("VALIDATION_SOURCE_CHANGED: " + file)
    for label in ("regression", "nativeSyntheticProbes", "geometryParityProbes", "junitSummary"):
        item = index[label]
        if digest(root / item["path"]) != item["sha256LF"]:
            raise ValueError("AUXILIARY_EVIDENCE_HASH_MISMATCH: " + label)
    found = {"cold": set(), "server": set()}
    blocked = []
    for item in index["reports"]:
        if item["mode"] not in found or item["seed"] in found[item["mode"]]:
            raise ValueError("DUPLICATE_OR_UNKNOWN_MODE")
        found[item["mode"]].add(item["seed"])
        file = root / item["path"]
        if digest(file) != item["sha256LF"]:
            raise ValueError("EVIDENCE_HASH_MISMATCH: " + str(file))
        row = json.loads(file.read_text(encoding="utf-8"))
        if row["seed"] != item["seed"] or row["sourceCommit"] != index["runtimeSourceCommit"]:
            raise ValueError("REPORT_INDEX_PROVENANCE_MISMATCH")
        if not validate_row(row):
            reason = row["failure"]
            if not reason and row["nodeYieldGate"] != "PASS":
                reason = "FAIL_INSUFFICIENT_NODE_YIELD: " + "; ".join(
                    f"window({w['x']},{w['z']})={w['mathNodes']} ({w['gate']})" for w in row["windows"])
            if not reason:
                reason = "NATIVE_NUMERIC_NOT_VALIDATED"
            blocked.append({"mode": item["mode"], "seed": row["seed"], "reason": reason})
    if any(seeds != set(SEEDS) for seeds in found.values()):
        raise ValueError("MISSING_SEED_REPORT")
    if require_pass and blocked:
        raise ValueError("R2_B3_GENERATION_GATE_BLOCKED: " + json.dumps(blocked))
    return {"evidenceIntegrity": "PASS", "nativeGenerationGate": "BLOCKED" if blocked else "NUMERIC_ONLY_NO_AUTHORIZATION", "blocked": blocked}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--require-pass", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.root, args.require_pass), indent=2))
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("R2_B3_EVIDENCE_FAIL: " + str(error))
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
