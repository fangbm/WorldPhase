"""B2 pure mathematical oracle. No Minecraft, noise sampling, worlds or authorization."""
import argparse
from dataclasses import dataclass
from fractions import Fraction as F
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / "docs/verification/spatial-retune/v2/r2"
M = json.loads((DIRECTORY / "EXPERIMENT_MANIFEST.json").read_text(encoding="utf-8"))
MASK = (1 << 64) - 1


def mix(v):
    v &= MASK
    v = ((v ^ (v >> 30)) * 0xbf58476d1ce4e5b9) & MASK
    v = ((v ^ (v >> 27)) * 0x94d049bb133111eb) & MASK
    return v ^ (v >> 31)


def rot(v, n):
    v &= MASK
    return ((v << n) | (v >> (64 - n))) & MASK


def unit(seed, i, j, tag):
    v = mix((seed & MASK) ^ mix((i & MASK) ^ 0x9e3779b97f4a7c15)
            ^ rot(mix((j & MASK) ^ 0xd1b54a32d192ed03), 32) ^ tag)
    return (v >> 11) * 2**-53


def center(seed, node_id):
    if seed not in M["seeds"]["development"]:
        raise ValueError("SEED_NOT_IN_DEVELOPMENT_SET: no holdout evaluation")
    i, j = node_id
    length, jitter = M["nodes"]["spacing"], M["nodes"]["jitter_each_axis"]
    result = tuple(length * (k + .5) + jitter * (2 * unit(seed, i, j, int(M["channels"][tag], 16)) - 1)
                 for k, tag in [(i, "node_x_r2"), (j, "node_z_r2")])
    if any(abs(v)+M["nodes"]["support_radius_bound"] > M["domain"]["world_border_abs"] for v in result):
        raise ValueError("ROI_MISS_WORLD_BORDER")
    return result


def target_ids():
    ids = set()
    length = M["nodes"]["spacing"]
    radius = M["preparation"]["roi_half_extent"]
    extent = M["valley_graph"]["query_start_half_extent"]
    for w in M["windows"]:
        lo = [(w[a] - radius) // length for a in ("x", "z")]
        hi = [(w[a] + radius) // length for a in ("x", "z")]
        ids.update((i, j) for i in range(lo[0]-extent, hi[0]+extent+2)
                   for j in range(lo[1]-extent, hi[1]+extent+2))
    return sorted(ids)


def shortlist(seed, dry_ids):
    selected = set()
    radius = M["preparation"]["roi_half_extent"] - M["nodes"]["support_radius_bound"]
    for w in M["windows"]:
        ranked = []
        for node_id in sorted(dry_ids):
            x, z = center(seed, node_id)
            dx, dz = F(x)-w["x"], F(z)-w["z"]
            if abs(dx) <= radius and abs(dz) <= radius:
                ranked.append((dx*dx+dz*dz, node_id))
        selected.update(v[1] for v in sorted(ranked)[:M["preparation"]["shortlist_per_window"]])
    return sorted(selected)


def anchor(lo, hi, proven_error):
    if not math.isfinite(lo) or not math.isfinite(hi) or lo > hi:
        raise ValueError("NONFINITE_OR_INVALID_INTERVAL")
    if proven_error is None or not math.isfinite(proven_error) or proven_error < 0 or proven_error > M["reference"]["error_cap"]:
        return None, "ANCHOR_ERROR_UNPROVEN"
    if math.floor(lo) != math.floor(hi):
        return None, "ANCHOR_BIN_UNCERTAIN"
    h = math.floor(lo) + .5
    if h < M["reference"]["dry_min"]:
        return None, "WET_ANCHOR"
    if h-32 < -40 or h+20 > 240:
        return None, "BAND_LIMIT"
    return h, "ANCHOR_DRY"


def quintic(t):
    t = min(1., max(0., t))
    return (t*t*t) * (10 + t*(-15 + 6*t))


def exact_quintic(t):
    t = min(F(1), max(F(0), t))
    return (t*t*t) * (10 + t*(-15 + 6*t))


def ramp(h, delta, u):
    if abs(delta) > M["ports"]["ramp_max_abs_delta"]:
        raise ValueError("RAMP_HEIGHT_DELTA")
    a, b = M["ports"]["ramp"]
    return h + delta * quintic((u-a)/(b-a))


def terminal(h_end, h_external, u):
    a, b = M["ports"]["terminal"]
    return h_end + quintic((u-a)/(b-a)) * (h_external-h_end)


def port_certificate(h, delta, terminal_range, terminal_gradient, error):
    values = (h, delta, terminal_range, terminal_gradient, error)
    if any(not math.isfinite(v) for v in values) or min(terminal_range, terminal_gradient, error) < 0:
        return "NONFINITE_OR_INVALID_INTERVAL"
    p = M["ports"]
    if abs(delta) > p["ramp_max_abs_delta"]: return "RAMP_HEIGHT_DELTA"
    if min(h, h+delta-terminal_range) < p["minimum_model_height"]: return "WET_PORT_MODEL"
    if terminal_range > p["terminal_height_error_from_endpoint_max"]: return "TERMINAL_RANGE"
    if terminal_gradient > p["terminal_model_gradient_max"]: return "TERMINAL_GRADIENT"
    if error > M["physical_certificate"]["height_program_error_cap"]: return "FLOAT_ERROR_UNPROVEN"
    return "PORT_MODEL_READY_NOT_FULL_SAFE"


def common_pair(phase_ports):
    # Both phases must expose the SAME opposite pair, never one unrelated exit per phase.
    for pair in [("E", "W"), ("N", "S")]:
        if all(all(d in phase_ports.get(p, set()) for d in pair) for p in M["scope"]["prototype_phases"]):
            return pair
    return None


def component_count(nodes, edges):
    remaining = set(nodes)
    count = 0
    while remaining:
        stack = [min(remaining)]
        count += 1
        while stack:
            a = stack.pop()
            if a not in remaining: continue
            remaining.remove(a)
            stack.extend(b if a == c else c for c, b in edges if a in (c, b))
    return count


def physical_increment(lipschitz, error, distance):
    if not all(math.isfinite(v) for v in (lipschitz, error, distance)) or min(lipschitz, error) < 0:
        raise ValueError("INVALID_BOUND")
    if distance < M["physical_certificate"]["minimum_separation"]:
        raise ValueError("BELOW_DECLARED_PHYSICAL_SCALE")
    return F(lipschitz)*F(distance) + 2*F(error)


def finite_slope(lipschitz, error):
    return physical_increment(lipschitz, error, .25) / F(.25)


@dataclass(frozen=True)
class Error:
    """Exact rational intervals + forward binary64 error; full ulp per operation."""
    lo: F
    hi: F
    e: F = F(0)

    def finish(self, lo, hi, propagated):
        magnitude = max(abs(lo), abs(hi)) + propagated
        # nextUp conversion plus full ulp covers binade ties/subnormals conservatively.
        upper = math.nextafter(float(magnitude), math.inf)
        if not math.isfinite(upper): raise ValueError("NONFINITE_ERROR_BOUND")
        return Error(lo, hi, propagated + F(math.ulp(upper)))

    def add(self, b): return self.finish(self.lo+b.lo, self.hi+b.hi, self.e+b.e)
    def sub(self, b): return self.finish(self.lo-b.hi, self.hi-b.lo, self.e+b.e)
    def mul(self, b):
        products = [self.lo*b.lo, self.lo*b.hi, self.hi*b.lo, self.hi*b.hi]
        e = max(abs(self.lo), abs(self.hi))*b.e + max(abs(b.lo), abs(b.hi))*self.e + self.e*b.e
        return self.finish(min(products), max(products), e)
    def restrict(self, lo, hi): return Error(F(lo), F(hi), self.e)


def constant(v): return Error(F(v), F(v))


def fade_error(length):
    world = 2*M["domain"]["world_border_abs"] + 184
    coord = F(math.ulp(float(world)))
    # subtraction p-c, then u-a, then division; integer lengths are exactly represented.
    quotient = world/length
    e = (2*coord)/F(length) + F(math.ulp(math.nextafter(quotient, math.inf)))
    t = Error(F(0), F(1), e)  # clamp is nonexpansive
    return t.mul(t).mul(t).mul(constant(10).add(t.mul(constant(-15).add(constant(6).mul(t))))).restrict(0, 1)


def program_error_bounds():
    h = Error(F(71.5), F(133.5))
    q = fade_error(128)
    r = h.add(Error(F(-8), F(8)).mul(q))
    q = fade_error(24)
    hp = Error(F(-22), F(185), F(M["reference"]["error_cap"]))
    end = Error(F(63.5), F(141.5))
    difference = hp.sub(end).restrict(-.5, .5) # only AFTER terminal range certificate
    t = end.add(q.mul(difference))
    return r.e, t.e


def trilinear(corners, tx, ty, tz):
    # Synthetic linear interpolation, NOT Minecraft NoiseChunk.
    return sum(corners[i,j,k] * (tx if i else 1-tx) * (ty if j else 1-ty)
               * (tz if k else 1-tz) for i in (0,1) for j in (0,1) for k in (0,1))


def check_locks():
    data = (DIRECTORY/"EXPERIMENT_MANIFEST.json").read_bytes().replace(b"\r\n", b"\n")
    digest = hashlib.sha256(data).hexdigest()
    if digest != (DIRECTORY/"FROZEN_SHA256.txt").read_text().strip():
        raise ValueError("R2_FREEZE_MISMATCH")
    for name, expected in M["locks"].items():
        actual = hashlib.sha256((ROOT/name).read_bytes().replace(b"\r\n", b"\n")).hexdigest()
        if actual != expected: raise ValueError("HISTORICAL_INPUT_CHANGED: "+name)
    return digest


def report():
    digest = check_locks()
    r, t = program_error_bounds()
    cap = F(M["physical_certificate"]["height_program_error_cap"])
    if r > cap or t > cap: raise ValueError("FROZEN_ERROR_BUDGET_NOT_PROVED")
    ids = target_ids()
    if len(ids) != M["preparation"]["target_ids_exact"]: raise ValueError("TARGET_COUNT_MISMATCH")
    seeds = []
    for seed in M["seeds"]["development"]:
        # Dry-all is an explicitly synthetic upper scenario, not actual continentalness.
        chosen = shortlist(seed, ids)
        seeds.append(dict(seed=seed, targetIds=len(ids), syntheticAllDryShortlist=len(chosen),
                          shortlistIds=chosen, realAnchors="NOT_SAMPLED", realYield="UNKNOWN"))
    return dict(status="PURE_MODEL_AND_ERROR_ONLY_NOT_NATIVE_OR_FULL", manifestSha256=digest,
                modelGradient=dict(core=0, ramp=15/128, terminal=13/128),
                rationalProgramErrorUpper=dict(ramp=str(r), terminal=str(t)),
                errorBlocks=dict(ramp=float(r), terminal=float(t), frozenCap=float(cap)),
                finitePhysicalSlope=dict(ramp=float(finite_slope(15/128, float(cap))),
                                         terminal=float(finite_slope(13/128, float(cap)))),
                developmentLayouts=seeds, rawContinentalnessCalls=0, minecraftWorldsCreated=0,
                nativeInterpolation="NOT_RUN", interpolationOracle="SYNTHETIC_ONLY", fullSafety="NOT_RUN",
                shiftFeasibility="NOT_RUN", completedRoutes=0, holdouts="SEALED_NOT_SAMPLED",
                cpuTickWall="NOT_MEASURED_IN_MINECRAFT", generationAuthorized=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-report", action="store_true")
    parser.add_argument("--require-native", action="store_true")
    args = parser.parse_args()
    try:
        data = report()
        if args.require_native: raise ValueError("R2_NATIVE_FULL_GENERATION_GATE_NOT_APPROVED")
        text = json.dumps(data, indent=2)
        if args.write_report:
            path = ROOT/"build/reports/shared-nodes-r2/math.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text+"\n", encoding="utf-8")
        print(text)
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("R2_MATH_FAIL: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
