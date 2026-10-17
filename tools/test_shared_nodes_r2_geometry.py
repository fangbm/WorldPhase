"""Frozen-formula geometry on synthetic planes/walls, never Minecraft terrain."""
import math
import unittest
import shared_nodes_r2 as r2
from shared_nodes_r2_geometry import NodeModel,port_mask,DIRECTIONS


class SyntheticCompositeGeometryTests(unittest.TestCase):
    def make(self,delta=8):
        values={"SMOOTH":delta,"RIDGE":-delta}
        evidence={p:{d:(v,0,0,0) for d in DIRECTIONS} for p,v in values.items()}
        return NodeModel(96.5,lambda p,x,z:96.5+values[p],evidence)

    def test_core_is_shared_but_external_terrain_is_not(self):
        node=self.make()
        self.assertIsNotNone(node.pair)
        for x,z in [(0,0),(16,0),(-16,0),(0,16),(8,8)]:
            self.assertEqual(node.height("SMOOTH",x,z),96.5)
            self.assertEqual(node.height("RIDGE",x,z),96.5)
        self.assertEqual(node.height("SMOOTH",200,0),104.5)
        self.assertEqual(node.height("RIDGE",200,0),88.5)

    def test_ramps_leave_core_and_join_natural_strip_both_phases(self):
        node=self.make()
        for phase in ["SMOOTH","RIDGE"]:
            for direction,(dx,dz) in DIRECTIONS.items():
                heights=[node.height(phase,u*dx,u*dz) for u in [i/4 for i in range(0,801)]]
                self.assertEqual(heights[-1],node.external(phase,200*dx,200*dz))
                self.assertLessEqual(max(abs(a-b)/.25 for a,b in zip(heights,heights[1:])),.125)
                # Width guards are part of the synthetic geometry, not proof of native collision.
                for u in [24,64,100,152]:
                    a=node.height(phase,u*dx+2*(-dz),u*dz+2*dx)
                    self.assertAlmostEqual(a,node.height(phase,u*dx,u*dz),places=12)

    def test_ring_or_dead_end_never_installs_model_plateau(self):
        node=self.make(40)
        self.assertIsNone(node.pair)
        self.assertEqual(node.height("SMOOTH",0,0),136.5)
        evidence={p:{"E":(0,0,0,0)} for p in ["SMOOTH","RIDGE"]}
        dead=NodeModel(96.5,lambda p,x,z:120.5,evidence)
        self.assertIsNone(dead.pair)
        self.assertEqual(dead.height("RIDGE",0,0),120.5)

    def test_same_stored_coordinates_across_chunk_edges_and_sampling_order(self):
        node=self.make()
        for cx,cz in [(-16.25,-.5),(0.,0.),(15.75,16.5)]:
            points=[(x/4,z/4) for x,z in [(-65,-1),(-64,0),(-63,1),(-1,0),(0,0),(63,1),(64,0),(65,-1)]]
            def evaluate(order):
                return {(x,z):node.height("SMOOTH",x-cx,z-cz) for x,z in order}
            first=evaluate(points)
            self.assertEqual(first,evaluate(reversed(points)))
            # Splitting identical x/z by chunk ownership cannot change a global-coordinate function.
            chunks={}
            for x,z in points:chunks.setdefault((math.floor(x/16),math.floor(z/16)),[]).append((x,z))
            by_chunk={}
            for key in sorted(chunks,reverse=True):by_chunk.update(evaluate(chunks[key]))
            self.assertEqual(first,by_chunk)

    def test_invalid_geometry_inputs_fail_closed(self):
        node=self.make()
        for x in [math.nan,math.inf,-math.inf,1e308]:
            with self.assertRaises(ValueError):node.height("SMOOTH",x,0)
        with self.assertRaises(ValueError):NodeModel(math.nan,lambda p,x,z:96.5,{})
        invalid=NodeModel(96.5,lambda p,x,z:math.nan,{})
        with self.assertRaises(ValueError):invalid.height("SMOOTH",0,0)

    def test_mask_support_nonoverlap_and_bounded_extent(self):
        # Algebraic proof in protocol; these samples only catch code/transcription errors.
        for x in range(-200,201,4):
            for z in range(-200,201,4):
                q=[port_mask(x,z,d) for d in DIRECTIONS]
                self.assertLessEqual(sum(v>0 for v in q),1)
                self.assertLessEqual(sum(q),1+1e-14)
                if math.hypot(x,z)>192:self.assertEqual(sum(q),0)


if __name__=="__main__":unittest.main()
