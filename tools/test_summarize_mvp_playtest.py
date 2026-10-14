"""Synthetic validator fixtures only; these tests are never native playtest evidence."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import struct
import zlib
import unittest

import summarize_mvp_playtest as subject

def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))


# Synthetic blank test fixture, never copied into the native evidence archive.
PNG = (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", struct.pack(">IIBBBBB", 640, 360, 8, 2, 0, 0, 0))
       + png_chunk(b"IDAT", zlib.compress((b"\x00" + bytes(640 * 3)) * 360)) + png_chunk(b"IEND", b""))


class EvidenceValidationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.contract = {"schemaVersion": 1, "protocol": "WPH015_SUPPLEMENT_20261014",
                         "immutablePrimarySha256": {}, "notBeforeEpochMs": 1000,
                         "routeReplay": {"path": "replay", "runId": "replay", "seed": 8675309,
                                         "index": 35, "source": "CELL", "targetPhase": "FRACTURE",
                                         "entranceX": -383.5, "entranceZ": 896.5, "direction": 3},
                         "exploration": {"path": "exploration", "runId": "exploration", "seed": 123456789}}
        for index, seed in enumerate(subject.SEEDS):
            directory = f"run/mvp-playtest/seed-{index}-client"
            photos = [{"name": f"point-{point}-{phase}"} for point in range(10) for phase in ("fracture", "smooth", "ridge", "cell")]
            routes = [{"index": i, "classification": "OBSERVED_TERRAIN_DIFFERENCE", "reason": "ENTRY_SHIFT_REFUSED"} for i in range(35 if index == 1 else 36)]
            data = {"world": {"seed": seed}, "status": "FAIL" if index == 1 else "COMPLETED_NOT_A_GAMEPLAY_GATE_PASS",
                    "stage": "ROUTES" if index == 1 else "DONE", "failure": "Native player died in ROUTES" if index == 1 else None,
                    "photos": photos, "routeTrials": routes,
                    "survival": [{"phase": p, "status": "TIMED_NATIVE_SURVIVAL_COMPLETED", "activeSeconds": 900, "clientTicks": 18000} for p in ("FRACTURE", "SMOOTH")]}
            self.put(directory + "/mvp-client.json", data)
            self.pin(directory + "/mvp-client.json")
            for photo in photos:
                self.png(f"{directory}/screenshots/wph015-{seed}-{photo['name']}.png")
        self.put("run/mvp-playtest/seed-0-client/mvp-reload.json", {"status": "RELOAD_PASS", "expectedPlayer": {"uuid": "u"}, "restoredPlayer": {"uuid": "u"}})
        self.put("build/core-regression/summary.json", {"status": "PASS", "loopTransfers": 400, "successfulTransfers": 456,
                 "completeCycles": 100, "inventoryIdentityAndContentChecks": 495, "maxXzDeviation": 0, "maxYawPitchDeviation": 0,
                 "maxYCorrection": 1, "duplicatePlayerEntities": 0, "droppedTestItemEntities": 0, "saveReloadCheck": "PASS: restored",
                 "minecraft": "26.1", "neoforge": "26.1.0.19-beta", "java": "25.0.3+9-LTS"})
        trial = {k: self.contract["routeReplay"][k] for k in ("index", "source", "targetPhase", "entranceX", "entranceZ", "direction")}
        trial.update(classification="OBSERVED_TERRAIN_DIFFERENCE", reason="ENTRY_SHIFT_REFUSED", baselineProgress=17.2, entryShift={"result": "NO_SAFE_POSITION"})
        world = {"seed": 8675309, "minecraft": "26.1", "neoforge": "26.1.0.19-beta"}
        self.put("replay/mvp-routes.json", {"kind": "SUPPLEMENTAL_REPLAY", "runId": "replay", "world": world,
                 "routeFrom": 35, "routeTo": 36, "stage": "DONE", "status": "COMPLETED_NOT_A_GAMEPLAY_GATE_PASS",
                 "clientTicks": 400, "metrics": [{}], "routeTrials": [trial]})
        self.png("replay/screenshots/wph015-8675309-settled-route-35-entrance.png")
        self.png("replay/screenshots/wph015-8675309-settled-route-35-baseline.png")
        caves = []
        for phase in ("FRACTURE", "SMOOTH"):
            points = [{"x": i, "y": 70, "z": 900} for i in range(6)]
            cave = {"phase": phase, "status": "NATIVE_CAVE_RETURN_COMPLETED", "inspected": 100,
                    "fullChunks": 9, "waypoints": points, "inwardWalkTicks": 50, "returnWalkTicks": 60}
            for key, point in (("mouth", points[0]), ("interior", points[-1]), ("returned", points[0])):
                cave[key] = dict(point, phase=phase, health=20, safe=True)
                self.png(f"exploration/screenshots/wph015-supplement-cave-{phase}-{ {'mouth':'mouth','interior':'interior','returned':'return'}[key]}.png")
            caves.append(cave)
        start = {"phase": "SMOOTH", "x": 0.5, "y": 70, "z": 900.5, "health": 20, "safe": True}
        self.put("exploration/mvp-exploration.json", {"kind": "SUPPLEMENTAL_EXPLORATION", "runId": "exploration",
                 "world": dict(world, seed=123456789), "status": "EXPLORATION_RECORDED_REVIEW_REQUIRED",
                 "startedAt": 1000, "updatedAt": 2000, "clientTicks": 200,
                 "metrics": [{"fps": 60, "heapUsedBytes": 1000, "gcCount": 1, "gcTimeMs": 2, "integratedAverageTickMs": 5}],
                 "caves": caves, "travelSegments": [{"nativeWalk": "SEGMENT_WALKED"}],
                 "travel": {"start": start, "end": dict(start, z=870, inspected=100, plannedWaypoints=32),
                            "status": "BOUNDED_GROUND_ATTEMPT_RECORDED", "declaredBeforeMovement": True,
                            "destinationX": 0.5, "destinationZ": 516.5},
                 "adaptiveChoice": {"before": start, "after": start, "result": "NO_SAFE_POSITION", "classification": "OBSERVED_TERRAIN_DIFFERENCE"}})
        for name in ("travel-start", "travel-end", "adaptive-choice"):
            self.png(f"exploration/screenshots/wph015-supplement-{name}.png")
        self.save_contract()

    def put(self, relative, data):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data))

    def png(self, relative):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(PNG)

    def pin(self, relative):
        self.contract["immutablePrimarySha256"][relative] = hashlib.sha256((self.root / relative).read_bytes()).hexdigest()

    def save_contract(self):
        self.put("docs/verification/mvp-playtest/supplemental-contract.json", self.contract)

    def change(self, relative, mutation):
        data = json.loads((self.root / relative).read_text())
        mutation(data)
        self.put(relative, data)

    def rejected(self, fragment):
        result = subject.summarize(self.root)
        self.assertEqual("INCOMPLETE", result["PLAYTEST_EVIDENCE_COMPLETE"])
        self.assertTrue(any(fragment in error for error in result["failedInvariants"]), result["failedInvariants"])
        command = subprocess.run([sys.executable, subject.__file__, "--root", str(self.root), "--require-complete"], capture_output=True, text=True)
        self.assertEqual(1, command.returncode, command.stderr)
        self.assertIn(fragment, command.stdout)

    def add_split_evidence(self):
        source = json.loads((self.root / "exploration/mvp-exploration.json").read_text())
        for cave in source["caves"]:
            cave["interior"].update(gameMode="survival", surfaceHeight=76)
            for angle in range(1, 5):
                self.png(f"exploration/screenshots/wph015-supplement-cave-{cave['phase']}-survey-{angle}.png")
        self.put("exploration/mvp-exploration.json", source)
        self.contract["caveExploration"] = self.contract["exploration"].copy()
        self.contract["streamingExploration"] = dict(self.contract["exploration"], path="streaming", runId="streaming")
        source["runId"] = "streaming"
        trip = source["travel"]
        trip["start"].update(phase="RIDGE", x=-383.5, z=896.5)
        trip["end"].update(phase="RIDGE")
        trip.update(destinationZ=512.5, newServerChunksDuringWalk=100, receivedClientChunksDuringWalk=100,
                    startedAt=1000, endedAt=2000, northProgress=30)
        self.put("streaming/mvp-exploration.json", source)
        for name in ("travel-start", "travel-end"):
            self.png(f"streaming/screenshots/wph015-supplement-{name}.png")
        self.save_contract()

    def test_split_evidence_validates_independent_runs(self):
        self.add_split_evidence()
        self.assertEqual([], subject.summarize(self.root)["failedInvariants"])

    def test_split_cave_camera_missing(self):
        self.add_split_evidence()
        (self.root / "exploration/screenshots/wph015-supplement-cave-SMOOTH-survey-3.png").unlink()
        self.rejected("screenshot.missing")

    def test_split_cave_not_survival(self):
        self.add_split_evidence()
        self.change("exploration/mvp-exploration.json", lambda d: d["caves"][0]["interior"].update(gameMode="spectator"))
        self.rejected("covered_survival_interior")

    def test_split_streaming_missing(self):
        self.add_split_evidence()
        (self.root / "streaming/mvp-exploration.json").unlink()
        self.rejected("missing/unreadable JSON")

    def test_new_chunk_generation_not_observed(self):
        self.add_split_evidence()
        self.change("streaming/mvp-exploration.json", lambda d: d["travel"].update(newServerChunksDuringWalk=0))
        self.rejected("streaming.new_chunks_not_observed")

    def test_streaming_start_mismatch(self):
        self.add_split_evidence()
        self.change("streaming/mvp-exploration.json", lambda d: d["travel"]["start"].update(x=99))
        self.rejected("streaming.frozen_route")

    def test_streaming_phase_mismatch(self):
        self.add_split_evidence()
        self.change("streaming/mvp-exploration.json", lambda d: d["travel"]["end"].update(phase="SMOOTH"))
        self.rejected("streaming.phase")

    def test_stale_replay(self):
        import os
        os.utime(self.root / "replay/mvp-routes.json", (0, 0))
        self.rejected("replay.stale")

    def test_complete_negative_study_is_retune_not_go(self):
        result = subject.summarize(self.root)
        self.assertEqual([], result["failedInvariants"])
        self.assertEqual("PASS", result["PLAYTEST_EVIDENCE_COMPLETE"])
        self.assertEqual("PASS", result["CORE_TECHNICAL_REGRESSION"])
        self.assertEqual("RETUNE", result["GAMEPLAY_RECOMMENDATION"])
        self.assertEqual("NOT_APPROVED", result["PHASE_2_GATE"])

    def test_missing_report(self):
        (self.root / "exploration/mvp-exploration.json").unlink()
        self.rejected("missing/unreadable JSON")

    def test_malformed_report(self):
        (self.root / "replay/mvp-routes.json").write_text("{")
        self.rejected("missing/unreadable JSON")

    def test_missing_field_is_named_failure_not_success(self):
        self.change("replay/mvp-routes.json", lambda d: d.pop("world"))
        self.rejected("supplementalReplay")

    def test_truncated_png_is_not_screenshot_evidence(self):
        (self.root / "replay/screenshots/wph015-8675309-settled-route-35-entrance.png").write_bytes(PNG[:8])
        self.rejected("screenshot.incomplete_png")

    def test_corrupt_png_crc_is_rejected(self):
        image = bytearray(PNG)
        image[29] ^= 1
        (self.root / "replay/screenshots/wph015-8675309-settled-route-35-entrance.png").write_bytes(image)
        self.rejected("screenshot.crc")

    def test_modified_primary_rejected(self):
        self.change("run/mvp-playtest/seed-1-client/mvp-client.json", lambda d: d.update(failure="rewritten"))
        self.rejected("baseline.modified")

    def test_unknown_contract(self):
        self.contract["schemaVersion"] = 2
        self.save_contract()
        self.rejected("contract.unsupported_version")

    def test_wrong_replay_seed(self):
        self.change("replay/mvp-routes.json", lambda d: d["world"].update(seed=99))
        self.rejected("replay.world_or_version")

    def test_wrong_run_identity(self):
        self.change("replay/mvp-routes.json", lambda d: d.update(runId="old-run"))
        self.rejected("replay.identity")

    def test_wrong_trial_direction(self):
        self.change("replay/mvp-routes.json", lambda d: d["routeTrials"][0].update(direction=1))
        self.rejected("replay.frozen_trial_identity")

    def test_incomplete_trial(self):
        self.change("replay/mvp-routes.json", lambda d: d.update(routeTrials=[]))
        self.rejected("routes.indices")

    def test_replay_result_classification_mismatch(self):
        self.change("replay/mvp-routes.json", lambda d: d["routeTrials"][0]["entryShift"].update(result="SUCCESS"))
        self.rejected("replay.outcome_mismatch")

    def test_running_is_not_complete(self):
        self.change("exploration/mvp-exploration.json", lambda d: d.update(status="RUNNING"))
        self.rejected("exploration.incomplete")

    def test_stale_exploration(self):
        self.change("exploration/mvp-exploration.json", lambda d: d.update(startedAt=1))
        self.rejected("exploration.stale")

    def test_cave_discovery_is_not_native_exploration(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["caves"][0].update(status="DISCOVERY_FAILED"))
        self.rejected("ingress_or_return_incomplete")

    def test_cave_return_not_reached(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["caves"][1]["returned"].update(x=100))
        self.rejected("mouth_not_returned")

    def test_cave_screenshot_missing(self):
        (self.root / "exploration/screenshots/wph015-supplement-cave-FRACTURE-interior.png").unlink()
        self.rejected("screenshot.missing")

    def test_no_actual_ground_walk(self):
        self.change("exploration/mvp-exploration.json", lambda d: d.update(travelSegments=[]))
        self.rejected("travel.no_ground_segment_walked")

    def test_travel_destination_rewritten(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["travel"].update(destinationZ=100))
        self.rejected("travel.destination_changed")

    def test_travel_budget_overrun(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["travel"]["end"].update(inspected=18001))
        self.rejected("travel.budget")

    def test_refusal_changed_source(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["adaptiveChoice"]["after"].update(y=72))
        self.rejected("adaptive.refusal_changed_source")

    def test_entry_does_not_prove_route(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["adaptiveChoice"].update(classification="ROUTE_COMPLETED"))
        self.rejected("adaptive.entry_is_not_route_completion")

    def test_success_requires_alternate_and_exit(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["adaptiveChoice"].update(result="SUCCESS"))
        self.rejected("adaptive.alternate_or_exit_missing")

    def test_nonfinite_metrics(self):
        self.change("exploration/mvp-exploration.json", lambda d: d["metrics"][0].update(integratedAverageTickMs=float("nan")))
        self.rejected("exploration.metrics_invalid")

    def test_core_failure(self):
        self.change("build/core-regression/summary.json", lambda d: d.update(status="FAIL"))
        self.rejected("core.incomplete")

    def test_core_missing_field_does_not_leave_partial_pass(self):
        self.change("build/core-regression/summary.json", lambda d: d.pop("inventoryIdentityAndContentChecks"))
        self.rejected("inventoryIdentityAndContentChecks")
        self.assertEqual("INCOMPLETE", subject.summarize(self.root)["CORE_TECHNICAL_REGRESSION"])

    def test_core_inventory_checks_cannot_be_omitted(self):
        self.change("build/core-regression/summary.json", lambda d: d.update(inventoryIdentityAndContentChecks=0))
        self.rejected("core.identity_or_transfer_counts")

    def test_reload_mismatch(self):
        self.change("run/mvp-playtest/seed-0-client/mvp-reload.json", lambda d: d["restoredPlayer"].update(uuid="changed"))
        self.rejected("native_reload.snapshot_mismatch")

    def test_server_confirmed_death_is_terminal_failure_not_route_success(self):
        def dead(d):
            trial = d["routeTrials"].pop()
            trial.update(routeStep=2, walkTicks=50, death={"serverDead": True, "health": 0, "cause": "fall", "level": "worldphase:cell", "uuid": "u", "position": "(-400,139,896.5)"})
            d.update(status="RECORDED_NEGATIVE_OUTCOME", stage="ROUTES", abortedTrial=trial)
        self.change("replay/mvp-routes.json", dead)
        self.png("replay/screenshots/wph015-8675309-settled-route-35-death.png")
        self.assertEqual("PASS", subject.summarize(self.root)["PLAYTEST_EVIDENCE_COMPLETE"])
        self.change("replay/mvp-routes.json", lambda d: d["abortedTrial"]["death"].update(serverDead=False))
        self.rejected("replay.death_not_server_confirmed")


if __name__ == "__main__":
    unittest.main()
