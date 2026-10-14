"""Validate WPH-015 native evidence, including explicitly linked negative outcomes.

Usage: python tools/summarize_mvp_playtest.py --require-complete
Coverage is separate from gameplay utility and Phase 2 approval. Runtime output is ignored.
"""
import argparse
import hashlib
import json
import math
import struct
import zlib
from pathlib import Path

SEEDS = (123456789, 8675309, 20261011)
ROOT = Path(__file__).resolve().parents[1]
CLASSES = ("OBSERVED_TERRAIN_DIFFERENCE", "SHIFT_POSITION_FEASIBLE", "ROUTE_COMPLETED")


class EvidenceError(ValueError):
    pass


def read(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise EvidenceError(f"{path}: missing/unreadable JSON: {error}") from error
    if not isinstance(data, dict):
        raise EvidenceError(f"{path}: expected object")
    return data


def require(condition, invariant):
    if not condition:
        raise EvidenceError(invariant)


def image(path):
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise EvidenceError(f"screenshot.missing: {path}") from error
    require(raw[:8] == b"\x89PNG\r\n\x1a\n", f"screenshot.invalid: {path}")
    offset, header, pixels, ended = 8, False, False, False
    while offset + 12 <= len(raw):
        size = struct.unpack_from(">I", raw, offset)[0]
        kind = raw[offset + 4:offset + 8]
        end = offset + 12 + size
        require(end <= len(raw), f"screenshot.truncated: {path}")
        payload = raw[offset + 8:offset + 8 + size]
        crc = struct.unpack_from(">I", raw, offset + 8 + size)[0]
        require(zlib.crc32(kind + payload) == crc, f"screenshot.crc: {path}")
        if kind == b"IHDR":
            require(offset == 8 and size == 13, f"screenshot.header: {path}")
            width, height = struct.unpack_from(">II", payload)
            require(width >= 640 and height >= 360, f"screenshot.resolution: {path}")
            header = True
        if kind == b"IDAT":
            pixels = pixels or size > 0
        if kind == b"IEND":
            ended = size == 0 and end == len(raw)
            break
        offset = end
    require(header and pixels and ended, f"screenshot.incomplete_png: {path}")


def validate_routes(routes, expected):
    require(isinstance(routes, list) and [r["index"] for r in routes] == list(expected), "routes.indices_incomplete_or_reordered")
    require(all(r["classification"] in CLASSES and r["reason"] for r in routes), "routes.invalid_classification")


def replay(root, contract):
    spec = contract["routeReplay"]
    directory = root / spec["path"]
    data = read(directory / "mvp-routes.json")
    require((directory / "mvp-routes.json").stat().st_mtime * 1000 >= contract["notBeforeEpochMs"], "replay.stale")
    require(data["kind"] == "SUPPLEMENTAL_REPLAY" and data["runId"] == spec["runId"], "replay.identity")
    require(data["world"]["seed"] == spec["seed"] and data["world"]["minecraft"] == "26.1"
            and data["world"]["neoforge"] == "26.1.0.19-beta", "replay.world_or_version")
    require(data["routeFrom"] == 35 and data["routeTo"] == 36 and data["stage"] in ("ROUTES", "DONE"), "replay.frozen_interval")
    require(data["clientTicks"] > 0 and data["metrics"], "replay.native_work_missing")
    if data["status"] == "RECORDED_NEGATIVE_OUTCOME":
        require(data["stage"] == "ROUTES" and not data["routeTrials"], "replay.death_not_terminal_trial")
        trial = data["abortedTrial"]
        death = trial["death"]
        require(death["serverDead"] is True and death["health"] <= 0 and death["cause"], "replay.death_not_server_confirmed")
        require(death["level"] == "worldphase:cell" and death["uuid"] and death["position"], "replay.death_source")
        require(trial["routeStep"] == 2 and trial["walkTicks"] > 0, "replay.death_not_original_baseline")
        require(trial["classification"] != "ROUTE_COMPLETED", "replay.death_is_not_success")
        image(directory / "screenshots/wph015-8675309-settled-route-35-death.png")
    else:
        require(data["status"] == "COMPLETED_NOT_A_GAMEPLAY_GATE_PASS" and data["stage"] == "DONE", "replay.incomplete")
        validate_routes(data["routeTrials"], [35])
        trial = data["routeTrials"][0]
        require(math.isfinite(trial["baselineProgress"]), "replay.baseline_missing")
        if trial["reason"] == "ENTRY_SHIFT_REFUSED":
            require(trial["classification"] == "OBSERVED_TERRAIN_DIFFERENCE" and trial["entryShift"]["result"] != "SUCCESS", "replay.outcome_mismatch")
        image(directory / "screenshots/wph015-8675309-settled-route-35-baseline.png")
    require(all(trial[key] == spec[key] for key in ("index", "source", "targetPhase", "entranceX", "entranceZ", "direction")), "replay.frozen_trial_identity")
    image(directory / "screenshots/wph015-8675309-settled-route-35-entrance.png")
    return {"status": data["status"], "runId": data["runId"], "trial": trial,
            "coverage": "TERMINAL_OBSERVATION_RECORDED", "primaryOutcomeChanged": False}


def native_exploration(root, contract, spec):
    directory = root / spec["path"]
    data = read(directory / "mvp-exploration.json")
    require(data["kind"] == "SUPPLEMENTAL_EXPLORATION" and data["runId"] == spec["runId"], "exploration.identity")
    require(data["world"]["seed"] == spec["seed"] and data["world"]["minecraft"] == "26.1"
            and data["world"]["neoforge"] == "26.1.0.19-beta", "exploration.world_or_version")
    require(data["status"] == "EXPLORATION_RECORDED_REVIEW_REQUIRED", "exploration.incomplete")
    require(data["updatedAt"] >= data["startedAt"] >= contract["notBeforeEpochMs"], "exploration.stale")
    require(data["metrics"] and data["clientTicks"] > 0, "exploration.metrics_missing")
    for metric in data["metrics"]:
        require(all(math.isfinite(metric[k]) and metric[k] >= 0 for k in ("fps", "heapUsedBytes", "gcCount", "gcTimeMs", "integratedAverageTickMs")), "exploration.metrics_invalid")
    return data, directory


def exploration(root, contract):
    data, directory = native_exploration(root, contract, contract["exploration"])
    cave_data, cave_directory = native_exploration(root, contract, contract.get("caveExploration", contract["exploration"]))
    data["originalDiscoveryCaves"] = data["caves"]
    data["caves"] = cave_data["caves"]
    data["caveRunId"] = cave_data["runId"]
    require([c["phase"] for c in data["caves"]] == ["FRACTURE", "SMOOTH"], "caves.phases_missing")
    for cave in data["caves"]:
        phase = cave["phase"]
        require(cave["status"] == "NATIVE_CAVE_RETURN_COMPLETED", f"cave.{phase}.ingress_or_return_incomplete")
        require(0 < cave["inspected"] <= 12000 and 0 < cave["fullChunks"] <= 81, f"cave.{phase}.setup_budget")
        require(6 <= len(cave["waypoints"]) <= 96 and cave["inwardWalkTicks"] > 0 and cave["returnWalkTicks"] > 0, f"cave.{phase}.native_walk_missing")
        for name in ("mouth", "interior", "returned"):
            require(cave[name]["phase"] == phase and cave[name]["health"] > 0 and cave[name]["safe"], f"cave.{phase}.{name}.unsafe_or_wrong_phase")
            image(cave_directory / f"screenshots/wph015-supplement-cave-{phase}-{ {'mouth':'mouth','interior':'interior','returned':'return'}[name]}.png")
        if "caveExploration" in contract:
            require(cave["interior"]["gameMode"] == "survival" and cave["interior"]["surfaceHeight"] - cave["interior"]["y"] >= 6, f"cave.{phase}.covered_survival_interior")
            for angle in range(1, 5):
                image(cave_directory / f"screenshots/wph015-supplement-cave-{phase}-survey-{angle}.png")
        first, end = cave["waypoints"][0], cave["waypoints"][-1]
        require(abs(cave["interior"]["x"] - end["x"]) < 1 and abs(cave["interior"]["z"] - end["z"]) < 1, f"cave.{phase}.interior_not_reached")
        require(abs(cave["returned"]["x"] - first["x"]) < 1 and abs(cave["returned"]["z"] - first["z"]) < 1, f"cave.{phase}.mouth_not_returned")
    travel = data["travel"]
    require(travel["status"] == "BOUNDED_GROUND_ATTEMPT_RECORDED" and travel["declaredBeforeMovement"] is True, "travel.incomplete_or_undeclared")
    require(travel["start"]["phase"] == "SMOOTH" and travel["end"]["phase"] == "SMOOTH", "travel.phase")
    require(travel["destinationX"] == travel["start"]["x"] and travel["destinationZ"] == travel["start"]["z"] - 384, "travel.destination_changed")
    require(0 < travel["end"]["inspected"] <= 18000 and travel["end"]["plannedWaypoints"] <= 256, "travel.budget")
    require(any(s.get("nativeWalk") == "SEGMENT_WALKED" for s in data["travelSegments"]), "travel.no_ground_segment_walked")
    for name in ("travel-start", "travel-end", "adaptive-choice"):
        image(directory / f"screenshots/wph015-supplement-{name}.png")
    choice = data["adaptiveChoice"]
    require(choice["classification"] in CLASSES[:2], "adaptive.entry_is_not_route_completion")
    require(choice["result"] in ("SUCCESS", "NO_SAFE_POSITION", "OUT_OF_WORLD", "CHUNK_NOT_READY", "CHUNK_FAILURE", "ERROR"), "adaptive.invalid_result")
    require(choice["before"]["x"] == choice["after"]["x"] and choice["before"]["z"] == choice["after"]["z"], "adaptive.horizontal_movement")
    if choice["result"] == "SUCCESS":
        require(choice.get("alternatePlan") and choice.get("exitAttempt"), "adaptive.alternate_or_exit_missing")
        require(abs(choice["before"]["y"] - choice["after"]["y"]) <= 4 and choice["after"]["safe"], "adaptive.bounded_safety")
    else:
        require(choice["before"] == choice["after"], "adaptive.refusal_changed_source")
    if "streamingExploration" in contract:
        stream, stream_dir = native_exploration(root, contract, contract["streamingExploration"])
        trip = stream["travel"]
        require(trip["status"] == "BOUNDED_GROUND_ATTEMPT_RECORDED" and trip["declaredBeforeMovement"] is True, "streaming.incomplete")
        require(trip["start"]["phase"] == trip["end"]["phase"] == "RIDGE", "streaming.phase")
        require(trip["start"]["x"] == -383.5 and trip["start"]["z"] == 896.5 and trip["destinationZ"] == 512.5, "streaming.frozen_route")
        require(trip["newServerChunksDuringWalk"] > 0 and trip["receivedClientChunksDuringWalk"] > 0, "streaming.new_chunks_not_observed")
        require(0 < trip["end"]["inspected"] <= 18000 and trip["end"]["plannedWaypoints"] <= 256, "streaming.budget")
        require(any(s.get("nativeWalk") == "SEGMENT_WALKED" for s in stream["travelSegments"]), "streaming.no_native_walk")
        require(trip["endedAt"] > trip["startedAt"] and trip["northProgress"] > 0, "streaming.duration_or_progress")
        image(stream_dir / "screenshots/wph015-supplement-travel-start.png")
        image(stream_dir / "screenshots/wph015-supplement-travel-end.png")
        data["streamingAttempt"] = stream
    return data


def summarize(root=ROOT):
    result = {"status": "INCOMPLETE", "PLAYTEST_EVIDENCE_COMPLETE": "INCOMPLETE",
              "CORE_TECHNICAL_REGRESSION": "INCOMPLETE", "GAMEPLAY_RECOMMENDATION": "RETUNE",
              "PHASE_2_GATE": "NOT_APPROVED", "seeds": [], "failedInvariants": [],
              "limits": ["Scripted native exploration is not human playtested.",
                         "Negative-evidence coverage amendment requires independent review.",
                         "Coverage PASS is neither a successful route nor Phase 2 approval."]}
    def check(name, function):
        try:
            return function()
        except (EvidenceError, KeyError, TypeError, IndexError, ValueError) as error:
            result["failedInvariants"].append(f"{name}: {error}")
            return None
    contract = check("contract", lambda: read(root / "docs/verification/mvp-playtest/supplemental-contract.json"))
    if not contract:
        return result
    def baseline():
        require(contract["schemaVersion"] == 1 and contract["protocol"] == "WPH015_SUPPLEMENT_20261014", "contract.unsupported_version")
        for relative, digest in contract["immutablePrimarySha256"].items():
            try:
                actual = hashlib.sha256((root / relative).read_bytes()).hexdigest()
            except OSError as error:
                raise EvidenceError(f"baseline.missing: {relative}") from error
            require(actual == digest, f"baseline.modified: {relative}")
    check("immutableBaseline", baseline)
    for index, seed in enumerate(SEEDS):
        def primary(index=index, seed=seed):
            directory = root / f"run/mvp-playtest/seed-{index}-client"
            data = read(directory / "mvp-client.json")
            require(data["world"]["seed"] == seed, "primary.seed_mismatch")
            validate_routes(data["routeTrials"], range(35 if index == 1 else 36))
            require(data["status"] == ("FAIL" if index == 1 else "COMPLETED_NOT_A_GAMEPLAY_GATE_PASS"), "primary.status_mismatch")
            if index == 1:
                require("died in ROUTES" in data["failure"] and data["stage"] == "ROUTES", "primary.failed_for_different_reason")
            require(sum(p["name"].startswith("point-") for p in data["photos"]) == 40, "primary.views_incomplete")
            for photo in data["photos"]:
                image(directory / f"screenshots/wph015-{seed}-{photo['name']}.png")
            if index == 0:
                smoke = data["survival"]
                require(len(smoke) == 2 and {s["phase"] for s in smoke} == {"FRACTURE", "SMOOTH"}, "survival.phases_incomplete")
                require(all(s["status"] == "TIMED_NATIVE_SURVIVAL_COMPLETED" and s["activeSeconds"] >= 900 and s["clientTicks"] >= 18000 for s in smoke), "survival.duration_incomplete")
            return {"seed": seed, "status": data["status"], "photos": len(data["photos"]), "routeTrials": len(data["routeTrials"]),
                    "classes": {c: sum(r["classification"] == c for r in data["routeTrials"]) for c in CLASSES}, "failure": data.get("failure")}
        row = check(f"primary.seed{seed}", primary)
        if row:
            result["seeds"].append(row)
    result["supplementalReplay"] = check("supplementalReplay", lambda: replay(root, contract))
    result["supplementalExploration"] = check("supplementalExploration", lambda: exploration(root, contract))
    def core():
        data = read(root / "build/core-regression/summary.json")
        require(data["status"] == "PASS" and data["loopTransfers"] >= 100, "core.incomplete")
        require((root / "build/core-regression/summary.json").stat().st_mtime * 1000 >= contract["notBeforeEpochMs"], "core.stale")
        require(data["maxXzDeviation"] == 0 and data["maxYawPitchDeviation"] == 0 and data["maxYCorrection"] <= 4, "core.position_rotation")
        require(data["duplicatePlayerEntities"] == 0 and data["droppedTestItemEntities"] == 0, "core.duplication")
        require(data["saveReloadCheck"].startswith("PASS"), "core.reload")
        require(data["inventoryIdentityAndContentChecks"] >= data["loopTransfers"] and data["successfulTransfers"] >= data["loopTransfers"], "core.identity_or_transfer_counts")
        require(data["completeCycles"] * 4 == data["loopTransfers"], "core.cycle_count")
        row = {k: data[k] for k in ("status", "minecraft", "neoforge", "java", "loopTransfers", "successfulTransfers", "completeCycles", "inventoryIdentityAndContentChecks", "maxXzDeviation", "maxYawPitchDeviation", "maxYCorrection", "duplicatePlayerEntities", "droppedTestItemEntities", "saveReloadCheck")}
        result["CORE_TECHNICAL_REGRESSION"] = "PASS"
        return row
    result["core"] = check("core", core)
    def reload():
        data = read(root / "run/mvp-playtest/seed-0-client/mvp-reload.json")
        require(data["status"] == "RELOAD_PASS" and data.get("restoredPlayer") and data["restoredPlayer"] == data["expectedPlayer"], "native_reload.snapshot_mismatch")
        return {"status": "RELOAD_PASS", "snapshotMatches": True}
    result["nativeReload"] = check("nativeReload", reload)
    if not result["failedInvariants"]:
        result["status"] = "WORKLOAD_COMPLETE_REVIEW_REQUIRED"
        result["PLAYTEST_EVIDENCE_COMPLETE"] = "PASS"
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-complete", action="store_true")
    parser.add_argument("--root", type=Path, default=ROOT, help="Evidence tree; also used by isolated failure-path tests")
    args = parser.parse_args()
    result = summarize(args.root)
    target = args.root / "build/mvp-playtest/summary.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "supplementalExploration"}, indent=2))
    return int(args.require_complete and result["PLAYTEST_EVIDENCE_COMPLETE"] != "PASS")


if __name__ == "__main__":
    raise SystemExit(main())
