"""Cross-language geometry conformance; synthetic inputs, no Minecraft or holdout sampling."""
import json
import math
from pathlib import Path
import unittest
import shared_nodes_r2 as r2
from shared_nodes_r2_geometry import NodeModel, DIRECTIONS, port_mask

class NativeFrozenModelParity(unittest.TestCase):
    def test_native_geometry_matches_frozen_python_selected_pair_model(self):
        file=Path(__file__).resolve().parents[1]/'docs/verification/spatial-retune/v2/r2/b3/synthetic-geometry-probes.json'
        report=json.loads(file.read_text(encoding='utf-8'))
        self.assertEqual(report['kind'],'SYNTHETIC_GEOMETRY_ONLY_NOT_REAL_SEED_YIELD')
        self.assertEqual(len(report['rows']),66)
        for row in report['rows']:
            phase=row['phase'];h=row['h'];hp=row['backgroundHp'];y=row['y'];detail=row['detail']
            x,z=row['x']-row['centerX'],row['z']-row['centerZ']
            evidence={p:{d:(delta,0,0,0) for d in row[key]} for p,key,delta in [('SMOOTH','smoothEligible',8),('RIDGE','ridgeEligible',-8)]}
            node=NodeModel(h,lambda p,x,z:hp,evidence)
            self.assertEqual(list(node.pair or ()),row['selectedPair'])
            expected=node.height(phase,x,z)
            # hypot implementations may differ by a last bit; this is arithmetic conformance,
            # NOT a continuous program-error proof. 8 ulps is far below the frozen 2^-20 cap.
            self.assertLessEqual(abs(expected-row['height']),8*math.ulp(expected))
            if not node.pair:
                density=(hp-y)/16+detail;protection=0;qsum=0
            else:
                b=1-r2.quintic((math.hypot(x,z)-24)/152)
                density=(1-b)*((hp-y)/16+detail)+b*((h-y)/16)
                qsum=weighted=control=0
                delta=8 if phase=='SMOOTH' else -8
                for d in node.pair:
                    q=port_mask(x,z,d)
                    if not q:continue
                    dx,dz=DIRECTIONS[d];u=x*dx+z*dz;t=r2.quintic((u-152)/24)
                    target=r2.ramp(h,delta,u) if u<=152 else r2.terminal(h+delta,hp,u)
                    weighted+=q*((target-y)/16+t*detail);qsum+=q;control+=q*(1-t)
                density=(1-qsum)*density+weighted
                def smoothstep(a,b,v):
                    t=min(1,max(0,(v-a)/(b-a)));return t*t*(3-2*t)
                protection=(1-(1-b)*(1-control))*((1-smoothstep(16,32,expected-y))*(1-smoothstep(4,20,y-expected)))
            self.assertLessEqual(abs(density-row['density']),8*math.ulp(max(1,abs(density))))
            self.assertLessEqual(abs(protection-row['protection']),1e-13)
            self.assertEqual(qsum,row['qsum'])

if __name__=='__main__':unittest.main()
