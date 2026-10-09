package dev.fangbm.worldphase.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TutorialScoreTest {
    private static int[] flat(int height) { int[] result=new int[9]; Arrays.fill(result,height); return result; }
    private static int[] obstacle(int center,int rise) { var h=flat(center); h[1]=center+rise; return h; }
    private static TutorialScore score(int[] f,int[] s,double distance) { return TutorialScore.evaluate(f,s,distance,512,63,-64,319); }
    @Test void fractureObstacleAndSmoothEaseBeatLargeHeightDifferenceOnFlatGround() {
        var useful=score(obstacle(80,12),obstacle(72,2),128);
        var flat=score(flat(160),flat(72),128);
        assertTrue(useful.totalScore()>flat.totalScore()); assertTrue(useful.usefulHeuristic(512));
        assertFalse(flat.usefulHeuristic(512)); assertEquals(.75,useful.fractureSlope()); assertEquals(.125,useful.smoothSlope());
    }
    @Test void swappingDifficultyMakesScoreWorseAndContrastSigned() {
        var a=score(obstacle(80,12),obstacle(72,2),0); var b=score(obstacle(72,2),obstacle(80,12),0);
        assertTrue(a.totalScore()>b.totalScore()); assertEquals(-a.slopeContrast(),b.slopeContrast());
    }
    @Test void smoothSteepnessIsPenalizedEvenWithPositiveImprovement() {
        var easy=score(obstacle(90,16),obstacle(80,2),0); var hard=score(obstacle(90,16),obstacle(80,8),0);
        assertTrue(easy.totalScore()>hard.totalScore()); assertFalse(hard.usefulHeuristic(512));
    }
    @Test void fartherCandidatesPayKnownLinearDistancePenalty() {
        var near=score(obstacle(80,12),flat(72),0); var far=score(obstacle(80,12),flat(72),512);
        assertEquals(12,far.distancePenalty()); assertEquals(12,near.totalScore()-far.totalScore());
        assertFalse(score(obstacle(80,12),flat(72),513).usefulHeuristic(512));
    }
    @Test void waterCoastAndEmptyColumnsAreConservativeHints() {
        assertEquals(0,score(obstacle(80,12),flat(72),0).hazardPenalty());
        assertEquals(60,score(obstacle(80,12),flat(64),0).hazardPenalty());
        assertTrue(score(flat(-65),flat(72),0).hazardPenalty()>=180);
        assertFalse(score(obstacle(80,12),flat(63),0).usefulHeuristic(512));
    }
    @Test void diagonalDistancesAndAllDirectionsAreUsed() {
        var f=flat(80); f[8]=96;
        assertEquals(16/StrictMath.hypot(16,16),score(f,flat(72),0).fractureSlope());
    }
    @Test void intExtremeHeightsDoNotOverflowSlopeOrTotal() {
        var extreme=obstacle(Integer.MIN_VALUE,0); extreme[1]=Integer.MAX_VALUE;
        var result=score(extreme,flat(Integer.MAX_VALUE),512);
        assertTrue(Double.isFinite(result.totalScore())); assertTrue(result.fractureSlope()>0);
        assertTrue(result.hazardPenalty()>0); assertFalse(result.usefulHeuristic(512));
    }
    @Test void malformedAndNonFiniteInputsFail() {
        assertThrows(IllegalArgumentException.class,()->score(new int[8],flat(72),0));
        assertThrows(IllegalArgumentException.class,()->score(flat(80),flat(72),Double.NaN));
        assertThrows(IllegalArgumentException.class,()->score(flat(80),flat(72),Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->score(flat(80),flat(72),-1));
        assertThrows(IllegalArgumentException.class,()->new TutorialScore(0,0,0,0,0,Double.MAX_VALUE,Double.MAX_VALUE,0,0));
    }
}
