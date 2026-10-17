import copy
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("preflight", Path(__file__).with_name("check_v2_preflight.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class PreflightEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        target = self.root / "docs/verification/spatial-retune/v2/EXPERIMENT_MANIFEST.json"
        target.parent.mkdir(parents=True)
        shutil.copyfile(Path(__file__).resolve().parents[1] / "docs/verification/spatial-retune/v2/EXPERIMENT_MANIFEST.json", target)
        self.rows = []
        for i, seed in enumerate(m.SEEDS):
            row = dict(seed=seed, manifest=m.DIGEST, executionStatus="COMPLETED", v2WorldsCreated=0,
                       holdoutsSampled=False, v2TerrainGenerationAuthorized=False, cacheEntriesAfterClose=0,
                       ticks=[dict(calls=512, wallNanos=100)] * 4058 + [dict(calls=116, wallNanos=100)],
                       rawCalls=2077812, completedNodes=477, targetNodes=477, rejections={"MODEL_SLOPE_REJECTED":477},
                       enabledNodes=0, numericGate="BLOCKED_R1_ROUNDED_LIPSCHITZ_CONTRACT")
            self.rows.append(row)
        self.save()

    def save(self):
        for i, row in enumerate(self.rows):
            file = self.root / f"run/v2-numerical-preflight/seed-{i}/v2-preparation.json"
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(json.dumps(row))

    def cli(self, *args):
        return subprocess.run([sys.executable, str(Path(m.__file__)), "--root", str(self.root), *args], capture_output=True)

    def test_complete_negative_evidence_is_not_generation_pass(self):
        self.assertEqual(m.check(self.root)["generationGate"], "BLOCKED")
        self.assertEqual(self.cli().returncode, 0)
        self.assertNotEqual(self.cli("--require-pass").returncode, 0)

    def test_missing_corrupt_mismatch_incomplete_and_false_success_nonzero(self):
        original = copy.deepcopy(self.rows)
        for key, value in [("seed", 99), ("executionStatus", "RUNNING"), ("numericGate", None), ("failure", "ERROR"), ("targetNodes", 476), ("cacheEntriesAfterClose", 1), ("rejections", {}), ("rawCalls", 2),
                           ("ticks", [dict(calls=513, wallNanos=100)]),
                           ("ticks", [dict(calls=1, wallNanos=25000001)]), ("holdoutsSampled", True)]:
            self.rows = copy.deepcopy(original)
            self.rows[0][key] = value
            self.save()
            with self.subTest(key=key, value=value):
                self.assertNotEqual(self.cli().returncode, 0)
        file = self.root / "run/v2-numerical-preflight/seed-0/v2-preparation.json"
        file.write_text("{")
        self.assertNotEqual(self.cli().returncode, 0)
        file.unlink()
        self.assertNotEqual(self.cli().returncode, 0)

    def test_recorded_budget_failure_and_skipped_seeds_remain_blocked(self):
        self.rows[0].update(executionStatus="BLOCKED", failure="TICK_BUDGET_EXCEEDED", rawCalls=1, completedNodes=0, rejections={}, ticks=[dict(calls=1, wallNanos=26000000)])
        self.rows[1] = self.rows[2] = dict(executionStatus="NOT_RUN_AFTER_BUDGET_FAILURE")
        self.save()
        self.assertEqual(m.check(self.root)["generationGate"], "BLOCKED")
        self.assertNotEqual(self.cli("--require-pass").returncode, 0)


if __name__ == "__main__":
    unittest.main()
