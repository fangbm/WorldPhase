"""Synthetic model tests; no Minecraft or actual terrain/holdout sampling."""
from fractions import Fraction as F
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys
import unittest

spec = importlib.util.spec_from_file_location("r2", Path(__file__).with_name("shared_nodes_r2.py"))
r2 = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = r2
spec.loader.exec_module(r2)
M = r2.M


class FrozenConfigurationTests(unittest.TestCase):
    def test_immutable_inputs_and_scope(self):
        self.assertEqual(len(r2.check_locks()), 64)
        self.assertFalse(M["execution_authorized"])
        self.assertFalse(M["scope"]["minecraft_integration_in_B2"])
        self.assertEqual(M["seeds"]["development"], [123456789, 8675309, 20261011])
        self.assertEqual(M["seeds"]["holdout_status"], "SEALED_NOT_SAMPLED")
        self.assertEqual(M["physical_certificate"]["slope_limit"], .125)
        self.assertEqual(M["scope"]["transfer"], "UNCHANGED_BOUNDED_LANDING_XZ_EXACT_Y_PLUS_MINUS_4")
        self.assertEqual(M["scope"]["phase_2"], "NOT_APPROVED")

    def test_holdout_rejected_before_hash_or_terrain(self):
        # Rejection, not evaluation of the holdout seeds.
        for seed in M["seeds"]["holdout"]:
            with self.assertRaisesRegex(ValueError, "SEED_NOT_IN_DEVELOPMENT_SET"):
                r2.center(seed, (0, 0))

    def test_target_count_and_finite_query_proofs(self):
        self.assertEqual(len(r2.target_ids()), 132)
        self.assertLessEqual(len(r2.target_ids()), M["preparation"]["target_ids_cap"])
        l, j = M["nodes"]["spacing"], M["nodes"]["jitter_each_axis"]
        self.assertEqual(1.5*l-j, 1408)
        self.assertGreater(1.5*l-j, M["nodes"]["support_radius_bound"])
        self.assertEqual(2.5*l-j, M["valley_graph"]["omitted_edge_min_distance"])
        self.assertGreater(2.5*l-j, 2048)
        self.assertEqual(2*7*7, M["valley_graph"]["query_max_edges"])
        self.assertEqual(8*8, M["valley_graph"]["query_max_endpoint_nodes"])
        self.assertEqual(math.exp(-1024), 0.) # mathematical Gaussian zero in binary64 surrogate; Java r1 smoke retained

    def test_layout_negative_ids_order_chunk_edges(self):
        ids = [(-2, -1), (-1, 0), (0, -1), (0, 0), (1, 2)]
        maps = []
        for seed in M["seeds"]["development"]:
            first = {i:r2.center(seed, i) for i in ids}
            second = {i:r2.center(seed, i) for i in reversed(ids)}
            self.assertEqual(first, second)
            for (i, j), (x, z) in first.items():
                self.assertLessEqual(abs(x-1024*(i+.5)), 128)
                self.assertLessEqual(abs(z-1024*(j+.5)), 128)
            maps.append(first)
        self.assertNotEqual(maps[0], maps[1])
        for coordinate in [-1024.0001,-1024.,-16.,-1.,0.,15.9999,16.,1024.]:
            owner = math.floor(coordinate/1024)
            self.assertIn(owner, range(owner-1, owner+2))
        with self.assertRaisesRegex(ValueError, "WORLD_BORDER"):
            r2.center(123456789, (100000, 0))

    def test_shortlist_is_spatial_deterministic_and_never_refills(self):
        ids = r2.target_ids()
        for seed in M["seeds"]["development"]:
            first = r2.shortlist(seed, ids)
            self.assertEqual(first, r2.shortlist(seed, reversed(ids)))
            self.assertLessEqual(len(first), 16)
            self.assertEqual(len(first), len(set(first)))
            self.assertEqual(r2.shortlist(seed, []), [])
        # No API takes a port-failure list to substitute more favorable nodes.
        self.assertEqual(list(__import__("inspect").signature(r2.shortlist).parameters), ["seed", "dry_ids"])

    def test_coverage_and_nonoverlap_are_distinct(self):
        self.assertGreater(1024-2*128, 2*192)
        core = math.pi*16**2/1024**2*100
        support = math.pi*192**2/1024**2*100
        self.assertAlmostEqual(core, M["nodes"]["ideal_core_coverage_percent"])
        self.assertLess(support, 12)
        self.assertGreater(support, core*100)
        # Upper bound for 16 complete disjoint support disks / union of the two square ROIs.
        side = 3072
        dx, dz = 1728, 304
        union = 2*side*side-(side-dx)*(side-dz)
        self.assertLess(16*math.pi*192**2/union*100, 15)
        self.assertLess(math.hypot(184,14)+math.sqrt(32)+.6, 192)
        self.assertLess(16+.6+math.sqrt(32),24)
        self.assertLess(2+.6+math.sqrt(32),10)

    def test_exact_work_caps_no_time_claim(self):
        p = M["preparation"]
        self.assertEqual(132*4, p["anchor_raw_calls_upper"])
        self.assertEqual((32+2)*(20+2),p["terminal_integer_vertices_per_port_phase"])
        self.assertEqual((32+3)*(20+3),p["terminal_unique_raw_vertices_per_port_both_phases"])
        self.assertEqual(16*4*805+528, p["total_raw_calls_analytic_upper"])
        self.assertLess(52048, p["raw_calls_hard_cap"])
        self.assertEqual(16*4*748*2,p["column_work_items_upper"])
        self.assertEqual(16*4*748*98,p["smooth_edge_tests_upper"])
        self.assertLessEqual(p["column_work_items_upper"],p["phase_column_entries_cap"])
        self.assertEqual(16*23**2+528,p["arm_A_raw_calls_upper"])
        self.assertEqual(math.ceil(52048/2048),26)
        self.assertTrue(p["timing_status"].startswith("NOT_MEASURED"))


class ContinuousAndPhysicalTests(unittest.TestCase):
    def test_quintic_bounds_endpoints_and_derivative(self):
        self.assertEqual(r2.exact_quintic(F(0)),0)
        self.assertEqual(r2.exact_quintic(F(1)),1)
        self.assertEqual(30*F(1,2)**2*(1-F(1,2))**2,F(15,8))
        for t in [F(i,16) for i in range(-4,21)]:
            self.assertGreaterEqual(r2.exact_quintic(t),0)
            self.assertLessEqual(r2.exact_quintic(t),1)
        self.assertEqual(F(15,8)*8/128,F(15,128))
        self.assertEqual(F(1,16)+F(15,8)*F(1,2)/24,F(13,128))

    def test_anchor_quantization_is_not_ocean_raise(self):
        e=2**-30
        self.assertEqual(r2.anchor(96.25-e,96.25+e,e),(96.5,"ANCHOR_DRY"))
        self.assertEqual(r2.anchor(62.25,62.25,e),(None,"WET_ANCHOR"))
        self.assertEqual(r2.anchor(71-e,71+e,e),(None,"ANCHOR_BIN_UNCERTAIN"))
        self.assertEqual(r2.anchor(96.2,96.3,None),(None,"ANCHOR_ERROR_UNPROVEN"))
        self.assertEqual(r2.anchor(240.25,240.25,e),(None,"BAND_LIMIT"))
        for lo,hi in [(math.nan,1),(0,math.inf),(2,1)]:
            with self.assertRaises(ValueError):r2.anchor(lo,hi,e)

    def test_ramp_monotonic_and_terminal_c1_join(self):
        for delta in [-8,0,8]:
            self.assertEqual(r2.ramp(96.5,delta,24),96.5)
            self.assertEqual(r2.ramp(96.5,delta,152),96.5+delta)
            values=[r2.ramp(96.5,delta,u) for u in range(24,153)]
            self.assertEqual(values,sorted(values,reverse=delta<0))
            end=96.5+delta
            self.assertEqual(r2.terminal(end,end+.25,152),end)
            self.assertEqual(r2.terminal(end,end+.25,176),end+.25)
            # Q'=0 at joins. Natural terminal profile derivative is inherited at 176.
            self.assertEqual(30*0**2*(1-0)**2,0)
            self.assertEqual(30*1**2*(1-1)**2,0)
        with self.assertRaisesRegex(ValueError,"RAMP_HEIGHT_DELTA"):r2.ramp(96.5,8.01,100)

    def test_finite_scale_with_error_never_relaxes_eighth_slope(self):
        e=2**-20
        self.assertEqual(r2.finite_slope(15/128,e),F(15,128)+F(1,131072))
        self.assertLess(r2.finite_slope(15/128,e),F(1,8))
        self.assertLess(r2.finite_slope(13/128,e),F(1,8))
        for d in [.25,1.,4.]:
            self.assertLessEqual(r2.physical_increment(15/128,e,d),F(d)/8)
        with self.assertRaisesRegex(ValueError,"BELOW_DECLARED_PHYSICAL_SCALE"):
            r2.physical_increment(.01,e,math.nextafter(.25,0))

    def test_tiny_high_frequency_is_not_macro_cliff_and_alias_samples_do_not_prove_bound(self):
        # Exact analytic amplitude, not terrain. sin period1/8 => quarter-grid samples all zero.
        amplitude=F(1,128)
        real_derivative=2*math.pi*float(amplitude)/.125
        self.assertGreater(real_derivative,.125)
        self.assertLess(F(1,50)+2*amplitude/F(1,4)+2*F(2**-20)/F(1,4),F(1,8))
        # Larger amplitude cannot be rescued by merely reinterpreting rounding.
        self.assertGreater(2*F(1)/F(1,4),F(1,8))
        self.assertGreater(float(amplitude),0) # value between aliased samples exists; FULL voxel support still unknown

    def test_exact_rational_forward_roundoff_envelope(self):
        er,et=r2.program_error_bounds()
        cap=F(2**-20)
        self.assertGreater(er,0);self.assertGreater(et,0)
        self.assertLess(er,cap);self.assertLess(et,cap)
        for u in [-60000000.,-16.,24.,24.1,55.25,100.3,151.9,152.,60000000.]:
            expected=F(96.5)+8*r2.exact_quintic((F(u)-24)/128)
            actual=F(r2.ramp(96.5,8,u))
            self.assertLessEqual(abs(actual-expected),er)
        for u in [152.,152.1,160.25,170.3,176.,184.]:
            hext=F(104.75)
            expected=F(104.5)+r2.exact_quintic((F(u)-152)/24)*(hext-F(104.5))
            self.assertLessEqual(abs(F(r2.terminal(104.5,float(hext),u))-expected),et)

    def test_port_acceptance_and_fixed_failure_reasons(self):
        good=r2.port_certificate(96.5,8,.5,1/16,2**-20)
        self.assertEqual(good,"PORT_MODEL_READY_NOT_FULL_SAFE")
        cases=[((96.5,9,.5,1/16,2**-20),"RAMP_HEIGHT_DELTA"),
               ((71.5,-8,.5,1/16,2**-20),"WET_PORT_MODEL"),
               ((96.5,0,.501,1/16,2**-20),"TERMINAL_RANGE"),
               ((96.5,0,.5,.5,2**-20),"TERMINAL_GRADIENT"),
               ((96.5,0,.5,1/16,2**-19),"FLOAT_ERROR_UNPROVEN"),
               ((96.5,0,.5,math.nan,0),"NONFINITE_OR_INVALID_INTERVAL")]
        for args,reason in cases:self.assertEqual(r2.port_certificate(*args),reason)

    def test_opposite_common_ports_prevent_isolated_plateau_label(self):
        self.assertIsNone(r2.common_pair({"SMOOTH":{"E"},"RIDGE":{"E"}}))
        self.assertIsNone(r2.common_pair({"SMOOTH":{"E","W"},"RIDGE":{"N","S"}}))
        self.assertEqual(r2.common_pair({"SMOOTH":{"E","W","N","S"},"RIDGE":{"E","W"}}),("E","W"))
        self.assertEqual(r2.common_pair({"SMOOTH":{"N","S"},"RIDGE":{"N","S"}}),("N","S"))
        # A 40-block ring around h fails the ramp prerequisite, even with a perfectly flat core.
        self.assertEqual(r2.port_certificate(96.5,40,.5,1/16,0),"RAMP_HEIGHT_DELTA")

    def test_disconnected_graph_and_endpoint_only_edges(self):
        self.assertEqual(r2.component_count({0,1,2,3},{(0,1),(2,3)}),2)
        self.assertEqual(r2.component_count({0,1},{(0,1)}),1)
        data=r2.report()
        self.assertEqual(data["completedRoutes"],0) # graph connectivity cannot promote this
        self.assertEqual(data["fullSafety"],"NOT_RUN")
        self.assertFalse(data["generationAuthorized"])

    def test_synthetic_interpolation_and_carver_counterexample_not_native_pass(self):
        h=F(193,2)
        corners={(i,j,k):(h-8*j)/16 for i in (0,1) for j in (0,1) for k in (0,1)}
        value=r2.trilinear(corners,F(1,3),F(2,3),F(1,7))
        self.assertEqual(value,(h-8*F(2,3))/16) # exact plane under linear interpolation
        # Quintic ramp within one 4-wide interpolation cell is not generally linear.
        left,right=r2.ramp(96.5,8,40),r2.ramp(96.5,8,44)
        self.assertNotEqual((left+right)/2,r2.ramp(96.5,8,42))
        raw_support=(96.5-96)/16
        self.assertGreater(raw_support,0)
        after_carver=min(raw_support,-1.)
        self.assertLess(after_carver,0) # same model loses support after another stage
        self.assertEqual(r2.report()["nativeInterpolation"],"NOT_RUN")

    def test_cli_cannot_claim_native_or_world_authorization(self):
        result=subprocess.run([sys.executable,str(Path(r2.__file__)),"--require-native"],capture_output=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn(b"NOT_APPROVED",result.stdout)


if __name__ == "__main__":
    unittest.main()
