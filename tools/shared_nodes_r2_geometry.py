"""Synthetic 2D implementation of the already frozen B2 formulas; not a DF or collision engine."""
import math
import shared_nodes_r2 as r2

DIRECTIONS={"E":(1,0),"W":(-1,0),"N":(0,-1),"S":(0,1)}


def port_mask(x,z,direction):
    dx,dz=DIRECTIONS[direction]
    u,v=x*dx+z*dz,-x*dz+z*dx
    p=r2.M["ports"]
    a,b=p["longitudinal_fade_in"]
    c,d=p["longitudinal_fade_out"]
    lo,hi=p["transverse_plateau_half_width"],p["transverse_outer_half_width"]
    return r2.quintic((u-a)/(b-a))*(1-r2.quintic((u-c)/(d-c)))*(1-r2.quintic((abs(v)-lo)/(hi-lo)))


class NodeModel:
    def __init__(self,h,external,port_evidence):
        if not math.isfinite(h): raise ValueError("NONFINITE_HEIGHT")
        self.h,self.external,self.evidence=h,external,port_evidence
        valid={phase:{d for d,args in ports.items() if r2.port_certificate(h,*args)=="PORT_MODEL_READY_NOT_FULL_SAFE"}
               for phase,ports in port_evidence.items()}
        self.pair=r2.common_pair(valid)
        # Only the frozen first common opposite pair may modify geometry. Other
        # passing ports remain diagnostic evidence, never extra ramps or fallbacks.
        self.active_dirs=set(self.pair) if self.pair else set()

    def height(self,phase,x,z):
        if phase not in r2.M["scope"]["prototype_phases"]: raise ValueError("UNMODELED_PHASE")
        if any(not math.isfinite(v) or abs(v)>2*r2.M["domain"]["world_border_abs"] for v in (x,z)):
            raise ValueError("NONFINITE_OR_OUT_OF_DOMAIN")
        external=self.external(phase,x,z)
        if not math.isfinite(external): raise ValueError("NONFINITE_EXTERNAL_HEIGHT")
        if not self.pair: return external # rejected model is never installed as an isolated plateau
        n=r2.M["nodes"]
        b=1-r2.quintic((math.hypot(x,z)-n["common_collar_radius"])/(n["background_outer_radius"]-n["common_collar_radius"]))
        background=(1-b)*external+b*self.h
        total,value=0.,0.
        # Fixed canonical order, even though supports are analytically disjoint.
        for direction in r2.M["ports"]["directions"]:
            if direction not in self.active_dirs:continue
            q=port_mask(x,z,direction)
            if q==0:continue
            dx,dz=DIRECTIONS[direction];u=x*dx+z*dz
            delta=self.evidence[phase][direction][0]
            target=r2.ramp(self.h,delta,u) if u<=152 else r2.terminal(self.h+delta,external,u)
            total+=q;value+=q*target
        return (1-total)*background+value
