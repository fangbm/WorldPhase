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

    def background(self,node,phase,x,z):
        n=r2.M["nodes"]
        b=1-r2.quintic((math.hypot(x,z)-n["common_collar_radius"])/(n["background_outer_radius"]-n["common_collar_radius"]))
        return (1-b)*node.external(phase,x,z)+b*node.h

    def test_selected_ports_ew_priority_leaves_north_at_background(self):
        evidence={p:{d:(delta,0,0,0) for d in ("E","W","N")}
                  for p,delta in [("SMOOTH",8),("RIDGE",-8)]}
        node=NodeModel(96.5,lambda p,x,z:104.5 if p=="SMOOTH" else 88.5,evidence)
        self.assertEqual(node.pair,("E","W"))
        self.assertEqual(node.active_dirs,{"E","W"})
        for phase in ("SMOOTH","RIDGE"):
            for x,z in [(0,-80),(2,-100),(-2,-152)]:
                # The geometric mask alone is nonzero here: eligibility must gate it off.
                self.assertGreater(port_mask(x,z,"N"),0)
                background=self.background(node,phase,x,z)
                self.assertEqual(node.height(phase,x,z),background)
            self.assertNotEqual(self.background(node,phase,0,-80),
                                r2.ramp(node.h,evidence[phase]["N"][0],80))

    def test_selected_ports_ns_fallback_leaves_east_west_at_background(self):
        evidence={p:{d:(delta,0,0,0) for d in ("N","S")}
                  for p,delta in [("SMOOTH",8),("RIDGE",-8)]}
        node=NodeModel(96.5,lambda p,x,z:104.5 if p=="SMOOTH" else 88.5,evidence)
        self.assertEqual(node.pair,("N","S"))
        self.assertEqual(node.active_dirs,{"N","S"})
        for phase in ("SMOOTH","RIDGE"):
            for d in ("E","W"):
                dx,dz=DIRECTIONS[d]
                self.assertGreater(port_mask(80*dx,80*dz,d),0)
                self.assertEqual(node.height(phase,80*dx,80*dz),
                                 self.background(node,phase,80*dx,80*dz))
            for d in node.pair:
                dx,dz=DIRECTIONS[d]
                self.assertEqual(node.height(phase,80*dx,80*dz),
                                 r2.ramp(node.h,evidence[phase][d][0],80))

    def test_selected_ports_mismatched_pairs_keep_natural_background(self):
        evidence={"SMOOTH":{d:(8,0,0,0) for d in ("E","W")},
                  "RIDGE":{d:(-8,0,0,0) for d in ("N","S")}}
        external=lambda p,x,z:(104.5 if p=="SMOOTH" else 88.5)+x/64-z/128
        node=NodeModel(96.5,external,evidence)
        self.assertIsNone(node.pair)
        self.assertEqual(node.active_dirs,set())
        for phase in ("SMOOTH","RIDGE"):
            for x,z in [(0,0),(16,0),(80,0),(-80,0),(0,80),(0,-80),(200,-16)]:
                self.assertEqual(node.height(phase,x,z),external(phase,x,z))

    def test_selected_ports_dictionary_and_query_order_are_bitwise_stable(self):
        # Include all four passing ports to test E/W priority over a complete N/S pair.
        orders=[("E","W","N","S"),("S","N","W","E"),("N","E","S","W")]
        points=[(-16.25,-.5),(-16.,0.),(-15.75,.5),(-.25,-16.25),(0.,-16.),
                (.25,-15.75),(15.75,16.5),(16.,16.),(16.25,15.5),
                (0.,-80.),(2.,100.),(-80.,0.),(100.,2.)]
        expected=None
        for order in orders:
            for phases in [("SMOOTH","RIDGE"),("RIDGE","SMOOTH")]:
                evidence={p:{d:(8 if p=="SMOOTH" else -8,0,0,0) for d in order} for p in phases}
                node=NodeModel(96.5,lambda p,x,z:104.5 if p=="SMOOTH" else 88.5,evidence)
                self.assertEqual(node.pair,("E","W"))
                self.assertEqual(node.active_dirs,{"E","W"})
                chunks={}
                for point in points:
                    chunks.setdefault(tuple(math.floor(c/16) for c in point),[]).append(point)
                chunk_order=[point for key in sorted(chunks,reverse=True) for point in reversed(chunks[key])]
                for query_order in [points,list(reversed(points)),chunk_order,points*2]:
                    actual={(p,x,z):node.height(p,x,z).hex() for p in phases for x,z in query_order}
                    if expected is None:expected=actual
                    self.assertEqual(actual,expected)
                # Queries cannot select a different pair or activate diagnostic-only ports.
                self.assertEqual(node.pair,("E","W"))
                self.assertEqual(node.active_dirs,{"E","W"})

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
            for direction in node.pair:
                dx,dz=DIRECTIONS[direction]
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
