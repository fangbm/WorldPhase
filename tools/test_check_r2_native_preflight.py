"""Integrity/failure-path tests only; no terrain or candidate queries."""
import json
import shutil
import tempfile
from pathlib import Path
import unittest
import check_r2_native_preflight as check


def failed_row():
    row = dict(manifest=check.DIGEST,seed=123456789,sourceCommit="a"*40,
               worldGenerationAuthorized=False,holdoutsSampled=False,fullPairValidated=False,
               shiftPositionFeasible=False,v2WorldsCreated=0,preflightFullChunkRequests=0,
               routeCompleted=0,executionStatus="FAILED",failure="BUDGET_EXCEEDED:INITIALIZATION_HARD_TICK_25MS",
               anchors=132,nodes=[dict(id=dict(i=i,j=0),status="UNEXAMINED") for i in range(132)],
               publishedMathNodes=0,shortlistIds=[],shortlistedNodes=0,rawCalls=0,cacheHits=0,
               cacheMisses=0,heightEvaluations=0,smoothValleyEdgeTests=0,intervalDerivativeWork=0,
               intervalJetConstructions=0,ownedAllocationBytes=2400000,activePreparationCpuNanos=30000000,
               scheduledWallNanos=30000000,maxTickWallNanos=30000000,tickCount=1,ticks=[],
               windows=[dict(x=x,z=z,mathNodes=0,gate="FAIL_INCOMPLETE_PREPARATION") for x,z in check.WINDOWS],
               nodeYieldGate="FAIL",nativeNumericValidated=False)
    return row

class EvidenceFailureTests(unittest.TestCase):
    def test_complete_negative_evidence_is_not_a_gate_pass(self):
        self.assertFalse(check.validate_row(failed_row()))
    def test_missing_report_fields_fail(self):
        row=failed_row();del row["ticks"]
        with self.assertRaises(KeyError):check.validate_row(row)
    def test_partial_ready_is_rejected(self):
        row=failed_row();row["nodes"][0]["status"]="MATH_NODE_READY"
        with self.assertRaisesRegex(ValueError,"PARTIAL_READY_PUBLICATION"):check.validate_row(row)
    def test_incomplete_window_cannot_report_pass(self):
        row=failed_row();row["windows"][0]["gate"]="PASS"
        with self.assertRaisesRegex(ValueError,"WINDOW_GATE_MISMATCH"):check.validate_row(row)
    def test_wrong_seed_or_scope_is_rejected(self):
        for key,value in [("seed",-1),("holdoutsSampled",True),("worldGenerationAuthorized",True)]:
            row=failed_row();row[key]=value
            with self.assertRaises(ValueError):check.validate_row(row)
    def test_false_completed_and_over_budget_are_rejected(self):
        row=failed_row();row["executionStatus"]="PREPARATION_COMPLETED"
        with self.assertRaisesRegex(ValueError,"FALSE_COMPLETED"):check.validate_row(row)
        row["failure"]=""
        with self.assertRaisesRegex(ValueError,"OVER_BUDGET"):check.validate_row(row)

    def archive_fixture(self,root):
        directory=root/check.DIRECTORY;directory.mkdir(parents=True)
        original=Path(__file__).resolve().parents[1]/check.DIRECTORY/"EXPERIMENT_MANIFEST.json"
        shutil.copyfile(original,directory/"EXPERIMENT_MANIFEST.json")
        marker=root/"runtime.py";marker.write_text("# synthetic source\n")
        index=dict(manifestSha256=check.DIGEST,outcome=dict(worldGenerationAuthorized=False,holdoutsSampled=False),
                   runtimeSourceCommit="a"*40,protectedSha256LF={},runtimeSourceSha256LF={"runtime.py":check.digest(marker)},reports=[])
        auxiliary=directory/"auxiliary.json";auxiliary.write_text('"synthetic integrity fixture only"')
        for label in ("regression","nativeSyntheticProbes","geometryParityProbes","junitSummary"):
            index[label]=dict(path=auxiliary.relative_to(root).as_posix(),sha256LF=check.digest(auxiliary))
        for mode in ("cold","server"):
            for seed in check.SEEDS:
                row=failed_row();row["seed"]=seed
                file=directory/f"{mode}-{seed}.json";file.write_text(json.dumps(row))
                index["reports"].append(dict(mode=mode,seed=seed,path=file.relative_to(root).as_posix(),sha256LF=check.digest(file)))
        (directory/"B3_EVIDENCE.json").write_text(json.dumps(index))
        return directory

    def test_full_negative_archive_validates_but_require_pass_blocks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.archive_fixture(root)
            self.assertEqual(check.check(root)["evidenceIntegrity"],"PASS")
            with self.assertRaisesRegex(ValueError,"R2_B3_GENERATION_GATE_BLOCKED"):check.check(root,True)

    def test_missing_file_or_changed_source_cannot_validate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);directory=self.archive_fixture(root)
            report=directory/"cold-123456789.json";report.unlink()
            with self.assertRaises(OSError):check.check(root)
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.archive_fixture(root);(root/"runtime.py").write_text("changed")
            with self.assertRaisesRegex(ValueError,"RUNTIME_SOURCE_CHANGED"):check.check(root)

if __name__=="__main__":unittest.main()
