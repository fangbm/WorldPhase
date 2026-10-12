"""Summarize native WPH-015 evidence, never manufacture a gameplay PASS.

Usage: python tools/summarize_mvp_playtest.py [--require-complete]
Only ignored build/ output is written. Human visual/route review remains necessary.
"""
import argparse
import json
from pathlib import Path

SEEDS = (123456789, 8675309, 20261011)
ROOT = Path(__file__).resolve().parents[1]


def read(path):
    if not path.exists():
        return {"status": "MISSING", "path": str(path.relative_to(ROOT))}
    return json.loads(path.read_text(encoding="utf-8"))


def summarize():
    result = {"status": "INCOMPLETE", "seeds": [], "limits": [
        "Native key-mapping automation is not unrestricted human play.",
        "A completed short straight bypass is not a full tutorial route.",
        "Screenshots and structure/entity safety require human review."]}
    complete = True
    for index, seed in enumerate(SEEDS):
        root = ROOT / "run/mvp-playtest" / f"seed-{index}-client"
        data = read(root / "mvp-client.json")
        routes = data.get("routeTrials", [])
        classes = {kind: sum(r["classification"] == kind for r in routes) for kind in (
            "OBSERVED_TERRAIN_DIFFERENCE", "SHIFT_POSITION_FEASIBLE", "ROUTE_COMPLETED")}
        reasons = {r["reason"]: sum(t["reason"] == r["reason"] for t in routes) for r in routes}
        row = {"seed": seed, "status": data["status"], "photos": len(data.get("photos", [])),
               "routeTrials": len(routes), "classes": classes, "reasons": reasons,
               "survival": data.get("survival", []), "failure": data.get("failure")}
        row["missingScreenshots"] = [p["name"] for p in data.get("photos", [])
            if not (root / "screenshots" / f"wph015-{seed}-{p['name']}.png").exists()]
        metrics = data.get("metrics", [])
        if metrics:
            for field in ("fps", "heapUsedBytes", "integratedAverageTickMs"):
                values = sorted(r[field] for r in metrics)
                row[field] = {"min": values[0], "median": values[len(values)//2], "max": values[-1]}
            groups = {}
            for metric in metrics:
                groups.setdefault(metric.get("jvmStartTime", "original_jvm"), []).append(metric)
            row["gcTimeMsDelta"] = sum(g[-1]["gcTimeMs"] - g[0]["gcTimeMs"] for g in groups.values())
            row["metricJvms"] = len(groups)
        replay = read(root / "mvp-routes.json")
        if replay["status"] != "MISSING":
            row["settledReturnReplay"] = {"status": replay["status"],
                "from": replay.get("routeFrom"), "to": replay.get("routeTo"),
                "routes": [{k: r[k] for k in ("index", "classification", "reason")} for r in replay.get("routeTrials", [])]}
        done = (not row["missingScreenshots"] and data["status"] == "COMPLETED_NOT_A_GAMEPLAY_GATE_PASS" and len(routes) == 36
                and sum(p["name"].startswith("point-") for p in data.get("photos", [])) == 40)
        if index == 0:
            smoke = data.get("survival", [])
            done &= (len(smoke) == 2 and {r["phase"] for r in smoke} == {"FRACTURE", "SMOOTH"}
                     and all(r["status"] == "TIMED_NATIVE_SURVIVAL_COMPLETED"
                             and r["activeSeconds"] >= 900 and r["clientTicks"] >= 18000 for r in smoke))
        complete &= done
        result["seeds"].append(row)
    result["core"] = read(ROOT / "build/core-regression/summary.json")
    reload = read(ROOT / "run/mvp-playtest/seed-0-client/mvp-reload.json")
    same = (reload.get("restoredPlayer") is not None
            and reload.get("restoredPlayer") == reload.get("expectedPlayer"))
    result["nativeReload"] = {"status": reload["status"], "snapshotMatches": same}
    complete &= reload["status"] == "RELOAD_PASS" and same
    complete &= result["core"].get("status") == "PASS" and result["core"].get("loopTransfers", 0) >= 100
    if complete:
        result["status"] = "WORKLOAD_COMPLETE_REVIEW_REQUIRED"
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    result = summarize()
    target = ROOT / "build/mvp-playtest/summary.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    if args.require_complete and result["status"] != "WORKLOAD_COMPLETE_REVIEW_REQUIRED":
        raise SystemExit(1)
