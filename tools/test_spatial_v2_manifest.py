"""Milestone A contract tests: synthetic geometry only, no Minecraft/world access."""
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
M = json.loads((ROOT/'docs/verification/spatial-retune/v2/EXPERIMENT_MANIFEST.json').read_text(encoding='utf-8'))
MASK = (1 << 64)-1
TAGS = {k.rsplit('/', 1)[-1]: int(v['tag'], 16) for k, v in M['channels'].items() if 'tag' in v}


def mix(v):
    v &= MASK
    v = ((v ^ (v >> 30))*0xbf58476d1ce4e5b9) & MASK
    v = ((v ^ (v >> 27))*0x94d049bb133111eb) & MASK
    return v ^ (v >> 31)


def rot(v, n):
    v &= MASK
    return ((v << n) | (v >> (64-n))) & MASK


def hashed(seed, i, j, tag):
    return mix((seed & MASK) ^ mix((i & MASK) ^ 0x9e3779b97f4a7c15)
               ^ rot(mix((j & MASK) ^ 0xd1b54a32d192ed03), 32) ^ tag)


def unit(v): return (v >> 11)*2**-53


def node(seed, i, j, jitter=None):
    length, delta = M['nodes']['spacing'], M['nodes']['jitter_each_axis']
    if jitter is None:
        jitter = (2*unit(hashed(seed, i, j, TAGS['node_x_r1']))-1,
                  2*unit(hashed(seed, i, j, TAGS['node_z_r1']))-1)
    return length*(i+.5)+delta*jitter[0], length*(j+.5)+delta*jitter[1]


def edge_on(seed, a, b, eligible):
    if not eligible(*a) or not eligible(*b): return False
    if a[1] == b[1]: return True
    i, j = a
    k, l = b
    value = mix((seed & MASK) ^ TAGS['edge_z_r1'] ^ mix(i) ^ rot(mix(j), 16)
                ^ rot(mix(k), 32) ^ rot(mix(l), 48))
    return unit(value) < M['valley_graph']['z_edge_probability']


def distance2(p, a, b):
    dx, dz = b[0]-a[0], b[1]-a[1]
    q = min(1, max(0, ((p[0]-a[0])*dx+(p[1]-a[1])*dz)/(dx*dx+dz*dz)))
    return (p[0]-(a[0]+q*dx))**2+(p[1]-(a[1]+q*dz))**2


def gaussian(seed, p, eligible, extent, jitter=None):
    bx, bz = (math.floor(v/M['nodes']['spacing']) for v in p)
    best = math.inf
    for i in range(bx-extent, bx+extent+1):
        for j in range(bz-extent, bz+extent+1):
            a = (i, j)
            for b in [(i+1, j), (i, j+1)]:
                if edge_on(seed, a, b, eligible):
                    best = min(best, distance2(p, node(seed, *a, jitter), node(seed, *b, jitter)))
    if best >= M['valley_graph']['double_zero_radius']**2: return 0.
    d = math.sqrt(best)/M['valley_graph']['gaussian_width']
    return math.exp(-d*d)


def step(a, b, v):
    t = min(1, max(0, (v-a)/(b-a)))
    return t*t*(3-2*t)


def node_weight(seed, p, eligible, extent):
    bx, bz = (math.floor(v/M['nodes']['spacing']) for v in p)
    best = M['nodes']['influence_radius']
    for i in range(bx-extent, bx+extent+1):
        for j in range(bz-extent, bz+extent+1):
            if eligible(i, j):
                c = node(seed, i, j)
                best = min(best, math.hypot(p[0]-c[0], p[1]-c[1]))
    return step(*M['nodes']['m_smoothstep'], best)


def verify_locks(locks):
    for name, expected in locks.items():
        actual = hashlib.sha256((ROOT/name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
        if actual != expected: raise ValueError('source lock mismatch: '+name)


class ConfigurationTests(unittest.TestCase):
    def test_scope_and_sealed_inputs(self):
        self.assertFalse(M['execution_authorized'])
        self.assertEqual(M['scope']['phases'], ['SMOOTH', 'RIDGE'])
        self.assertEqual(M['seeds']['development'], [123456789, 8675309, 20261011])
        self.assertEqual(M['seeds']['holdout'], [606102026, -77331057, 424242424242])
        self.assertEqual(M['seeds']['holdout_status'], 'SEALED_NOT_SAMPLED')
        self.assertTrue(all(v.startswith('NOT_') for v in M['evidence_status'].values()))
        self.assertEqual(M['scope']['phase_2'], 'NOT_APPROVED')

    def test_source_locks_and_failure_path(self):
        locks = M['locks']['tracked_text_sha256']
        verify_locks(locks)
        bad = dict(locks)
        bad[next(iter(bad))] = '0'*64
        with self.assertRaisesRegex(ValueError, 'source lock mismatch'): verify_locks(bad)

    def test_arm_paths_mapping_and_control(self):
        arms = M['arms']
        self.assertEqual([a['id'] for a in arms], ['LEGACY_V1', 'C1_R1', 'V2_R1'])
        self.assertEqual(len({a['world_path_template'] for a in arms}), 3)
        self.assertEqual(len({v for a in arms for v in a['settings_ids'].values()}), 6)
        self.assertTrue(all(a['level_ids'] == {'smooth':'worldphase:smooth', 'ridge':'worldphase:ridge'} for a in arms))
        self.assertEqual(arms[1]['overrides'], {'smooth_noise.amplitude':[.1,.11], 'ridge_noise.amplitude':[1.,1.1]})
        self.assertEqual(len(TAGS), len(set(TAGS.values())))
        self.assertFalse(M['codec_contract']['runtime_seed_json_field'])

    def test_budgets_and_area(self):
        n, g, p = M['nodes'], M['valley_graph'], M['preparation_budget']
        self.assertEqual((2*n['mask_query_half_extent']+1)**2, n['mask_query_max_nodes'])
        self.assertEqual(2*(2*g['query_start_half_extent']+1)**2, g['query_max_edges'])
        self.assertEqual((2*g['query_start_half_extent']+2)**2, g['query_max_endpoint_nodes'])
        cells = 2*p['roi_radius']//n['spacing']+1
        bound = 2*(cells+13)**2
        self.assertEqual(bound, p['max_node_certificates_per_seed'])
        self.assertEqual(bound*n['envelope_max_vertices_per_node'], p['max_raw_C_calls_per_seed'])
        self.assertGreaterEqual(p['certificate_cache_capacity'], bound)
        for radius, key in [(16,'ideal_core_coverage_percent'), (32,'ideal_influence_coverage_percent')]:
            self.assertAlmostEqual(math.pi*radius**2/n['spacing']**2*100, n[key])
        self.assertAlmostEqual(math.pi*24**2/256**2*100, 2.761165418194154)


class NeighborhoodTests(unittest.TestCase):
    def test_omitted_geometry_lower_bounds(self):
        length, jitter = M['nodes']['spacing'], M['nodes']['jitter_each_axis']
        self.assertGreater(1.5*length-jitter, M['nodes']['influence_radius'])
        k = M['valley_graph']['query_start_half_extent']
        self.assertGreater((k+1-1.5)*length-jitter, M['valley_graph']['double_zero_radius'])
        self.assertGreater(length-2*jitter, 0)

    def test_underflow_theorem_and_nonzero_short_distance(self):
        self.assertLess(Fraction(3,8)**1024, Fraction(1,2)**1075)
        self.assertGreaterEqual((M['valley_graph']['double_zero_radius']/M['valley_graph']['gaussian_width'])**2, 1024)
        self.assertEqual(math.exp(-1024), 0)
        self.assertGreater(math.exp(-(128/64)**2), 0)

    def test_distance_is_to_finite_segment(self):
        self.assertEqual(distance2((20,0), (0,0), (10,0)), 100)
        self.assertEqual(distance2((5,3), (0,0), (10,0)), 9)

    def test_oracle_with_dry_holes(self):
        masks = [lambda i,j: True, lambda i,j: False, lambda i,j: abs(i)>=4,
                 lambda i,j: (i+2*j)%5==0, lambda i,j: j==0]
        points = [(-.001,0),(0,-.001),(-512,-512),(511.999,16),(512,16),
                  (-16.001,-15.999),node(19,-1,0),node(19,0,0)]
        for index, eligible in enumerate(masks):
            for p in points:
                with self.subTest(mask=index, p=p):
                    self.assertEqual(gaussian(19,p,eligible,6), gaussian(19,p,eligible,10))
                    self.assertEqual(node_weight(19,p,eligible,1), node_weight(19,p,eligible,5))

    def test_adversarial_jitter_and_negative_coordinates(self):
        for jitter in [(-1,-1),(1,1),(1,-1),(-1,1)]:
            for p in [(-.0001,-.0001),(-512.0001,511.9999),(512,-1024)]:
                eligible = lambda i,j: abs(i)>=4
                self.assertEqual(gaussian(-23,p,eligible,6,jitter), gaussian(-23,p,eligible,10,jitter))

    def test_order_repetition_and_chunk_crossing(self):
        points = [node(19,i,j) for i in range(-2,3) for j in range(-1,2)]
        def values(ordered):
            return {p:struct.pack('!dd',node_weight(19,p,lambda i,j:True,1),gaussian(19,p,lambda i,j:True,6)) for p in ordered}
        first = values(points)
        self.assertEqual(first, values(reversed(points)))
        self.assertEqual(first, values(points[::2]+points[1::2]))
        self.assertNotEqual(node(19,-1,0), node(-23,-1,0))
        for axis in [0,1]:
            for boundary in [-512,-16,0,16,512]:
                a,b = [5.,5.],[5.,5.]
                a[axis],b[axis] = boundary-1e-6,boundary+1e-6
                self.assertLess(abs(gaussian(19,a,lambda i,j:True,6)-gaussian(19,b,lambda i,j:True,6)),1e-6)

    def test_synthetic_graph_can_be_empty_or_disconnected(self):
        self.assertTrue(all(edge_on(19,(i,0),(i+1,0),lambda i,j:True) for i in range(-2,2)))
        for i in range(-2,3):
            for j in range(-2,3):
                self.assertFalse(edge_on(19,(i,j),(i+1,j),lambda i,j:False))
                islands = lambda i,j: i%2==0 and j%2==0
                self.assertFalse(edge_on(19,(i,j),(i+1,j),islands))
                self.assertFalse(edge_on(19,(i,j),(i,j+1),islands))


class DensityTests(unittest.TestCase):
    def test_bilinear_bounds_on_hand_data(self):
        vertices = [Fraction(-1,10),Fraction(1,10),Fraction(3,10),Fraction(1,2)]
        a,b,c,d = vertices
        bx,bz = max(abs(b-a),abs(d-c)),max(abs(c-a),abs(d-b))
        for x in [Fraction(0),Fraction(1,7),Fraction(1)]:
            for z in [Fraction(0),Fraction(4,5),Fraction(1)]:
                value = (1-x)*(1-z)*a+x*(1-z)*b+(1-x)*z*c+x*z*d
                self.assertLessEqual(min(vertices),value)
                self.assertLessEqual(value,max(vertices))
                self.assertLessEqual(abs((1-z)*(b-a)+z*(d-c)),bx)
                self.assertLessEqual(abs((1-x)*(c-a)+x*(d-b)),bz)
        for reference, expected in [(60,False),(90,True)]:
            lo,hi = reference-6,reference+6
            self.assertEqual(lo>=71 and lo-32>=-40 and hi+20<=240, expected)
        self.assertLess(6*(53/8)/900,.125)

    def test_density_sign_core_and_vertical_fade(self):
        hc = 90.
        for y in [hc-16,hc,hc+4]:
            p = (1-step(16,32,hc-y))*(1-step(4,20,y-hc))
            self.assertEqual(p,1)
            for native in [-.4,.4]: self.assertEqual((1-p)*native+p*.2,.2)
        for y in [hc-32,hc+20]:
            self.assertEqual((1-step(16,32,hc-y))*(1-step(4,20,y-hc)),0)
        self.assertGreater((hc-(hc-1))/16,0)
        self.assertLess((hc-(hc+1))/16,0)
        self.assertEqual(step(24,32,24),0)
        self.assertEqual(step(24,32,32),1)

    def test_bounds_and_preliminary_upper(self):
        for name, detail in [('smooth',.04),('ridge',.12)]:
            cfg = M[name]
            lo,hi = cfg.get('height_bounds',cfg.get('height_bounds_conservative'))
            actual = [(lo-320)/16-detail,(hi+64)/16+detail]
            for a,b in zip(actual,cfg['world_density_bounds']): self.assertAlmostEqual(a,b)
            lower,upper = M['codec_contract']['global_leaf_bounds'][name+'_core']
            self.assertGreaterEqual((lo-(2**31-1))/16-detail,lower)
            self.assertLessEqual((hi+2**31)/16+detail,upper)
            scan_top = math.ceil((max(133,hi)+16*detail+8)/8)*8
            self.assertEqual(scan_top,M['preliminary_surface']['upper_bound'][name])
            self.assertLess(scan_top,240)

    def test_wall_mask_selectivity(self):
        a,b,c,d = M['ridge']['wall_mask']
        def wall(r): return step(a,b,r)*(1-step(c,d,r))
        self.assertEqual(wall(0),0)
        self.assertEqual(wall(1),0)
        self.assertEqual(wall(.5),1)
        for i in range(101):
            self.assertGreaterEqual(wall(i/100),0)
            self.assertLessEqual(wall(i/100),1)
        ridge = M['ridge']
        perturbation = ridge['period']*ridge['curve_amplitude']*(53/8)/ridge['curve_length']
        self.assertAlmostEqual(perturbation, ridge['curve_coordinate_jacobian_perturbation_bound'])
        self.assertLess(perturbation,1)


if __name__ == '__main__': unittest.main()
