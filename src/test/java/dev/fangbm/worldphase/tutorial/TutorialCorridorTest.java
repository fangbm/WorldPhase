package dev.fangbm.worldphase.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TutorialCorridorTest {
    private static int[] flat(int h) { var result=new int[33]; Arrays.fill(result,h); return result; }
    private static TutorialCorridor score(int[] f,int[] s) { return TutorialCorridor.evaluate(0,0,f,s,0,512,63,-64,319); }
    @Test void aDirectionalObstacleAndHeightCompatibleEntryAreIndependentOfCenterContrast() {
        var f=flat(80); var s=flat(80);
        for(int i=0;i<33;i++) if(TutorialCorridor.OFFSETS.get(i).x()>=4) f[i]=84;
        var result=score(f,s); assertTrue(result.interesting()); assertTrue(result.heightCompatiblePoints()>0);
        assertEquals(4,result.heightContrast()); assertEquals(0,result.candidate().score().heightContrast());
        assertFalse(result.candidate().score().usefulHeuristic(512)); // Old threshold is NOT rewritten.
        assertEquals("INTERESTING_BUT_SHIFT_UNVERIFIED",result.status()); assertEquals(9,result.shiftProbePoints().size());
    }
    @Test void orthogonalSmoothSteepnessDoesNotHideAnEasyAxis() {
        var f=flat(80); var s=flat(80);
        for(int i=0;i<33;i++) { var p=TutorialCorridor.OFFSETS.get(i);
            if(p.x()>=4) f[i]=84; if(p.z()!=0) s[i]=100; }
        var result=score(f,s); assertEquals(1,result.dx()); assertEquals(0,result.dz());
        assertEquals(0,result.smoothStep()); assertTrue(result.interesting());
        assertTrue(result.candidate().score().smoothSlope()>.25);
    }
    @Test void aLargeFlatHeightGapIsNotAnObstacleOrProofOfShift() {
        var result=score(flat(150),flat(80)); assertFalse(result.interesting());
        assertEquals(0,result.heightCompatiblePoints()); assertEquals("INSUFFICIENT_DIRECTIONAL_CONTRAST",result.status());
    }
    @Test void seaHintsAreUncertainAndEmptyColumnsInvalidNeitherIsCertifiedSafe() {
        assertEquals(TutorialCorridor.Risk.LOW_RISK,score(flat(80),flat(80)).risk());
        assertEquals(TutorialCorridor.Risk.REQUIRES_VALIDATION,score(flat(63),flat(63)).risk());
        var invalid=score(flat(-65),flat(80)); assertEquals(TutorialCorridor.Risk.INVALID,invalid.risk());
        assertFalse(invalid.interesting());
    }
    @Test void tieBreakingFootprintBoundsAndFiniteExtremeArithmeticAreStable() {
        var flat=score(flat(80),flat(80)); assertEquals(1,flat.dx()); assertEquals(0,flat.dz());
        assertEquals(33,TutorialCorridor.OFFSETS.size()); assertEquals(33,TutorialCorridor.OFFSETS.stream().distinct().count());
        assertTrue(TutorialCorridor.OFFSETS.stream().allMatch(p->Math.abs(p.x())<=16 && Math.abs(p.z())<=16));
        assertTrue(Double.isFinite(score(flat(Integer.MIN_VALUE),flat(Integer.MAX_VALUE)).score()));
        assertThrows(IllegalArgumentException.class,()->score(new int[32],flat(80)));
        assertThrows(IllegalArgumentException.class,()->TutorialCorridor.evaluate(0,0,flat(80),flat(80),Double.NaN,512,63,-64,319));
        assertThrows(UnsupportedOperationException.class,()->flat.shiftProbePoints().clear());
    }
}
