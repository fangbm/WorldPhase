package dev.fangbm.worldphase.verification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CoreInvariantLedgerTest {
    @Test void countsOnlySuccessfulMovesAndCompleteCycles() {
        var ledger=new CoreInvariantLedger();
        for(int i=0;i<50;i++) {
            move(ledger,220.5,220.5); move(ledger,220.5,221.5);
            move(ledger,221.5,220.5); move(ledger,220.5,220.5); ledger.cycleCompleted();
        }
        assertEquals(200,ledger.successfulTransfers()); assertEquals(50,ledger.completedCycles());
        assertEquals(0,ledger.maxXzDeviation()); assertEquals(0,ledger.maxRotationDeviation());
        assertEquals(1,ledger.maxYCorrection());
    }
    @Test void horizontalDriftNamesInvariantAndIterationWithoutCountingSuccess() {
        var ledger=new CoreInvariantLedger(); ledger.scenario("cycle=17 target=CELL");
        var error=assertThrows(IllegalStateException.class,()->ledger.transferred(-16.125,15.9375,220.5,123.25f,-37.5f,
                -16,15.9375,220.5,123.25f,-37.5f));
        assertTrue(error.getMessage().contains("[transfer.xz] scenario=cycle=17 target=CELL"));
        assertEquals("transfer.xz",ledger.failedInvariant()); assertEquals(.125,ledger.maxXzDeviation());
        assertEquals(0,ledger.successfulTransfers());
    }
    @Test void signedZeroIsNotMistakenForExactCoordinatePreservation() {
        var ledger=new CoreInvariantLedger();
        assertThrows(IllegalStateException.class,()->ledger.transferred(-0.0,0,70,0,0,0.0,0,70,0,0));
        assertEquals("transfer.xz",ledger.failedInvariant());
    }
    @Test void rotationDriftIsReportedWithMeasuredDeviation() {
        var ledger=new CoreInvariantLedger();
        assertThrows(IllegalStateException.class,()->ledger.transferred(0,0,70,0,0,0,0,70,1,0));
        assertEquals("transfer.rotation",ledger.failedInvariant()); assertEquals(1,ledger.maxRotationDeviation());
    }
    @Test void outOfBoundYDoesNotCountAsSuccessfulTransfer() {
        var ledger=new CoreInvariantLedger(); assertThrows(IllegalStateException.class,()->move(ledger,70,75));
        assertEquals("transfer.bounded_y",ledger.failedInvariant()); assertEquals(0,ledger.successfulTransfers());
    }
    @Test void finiteResultsAreRequiredBeforeStatistics() {
        var ledger=new CoreInvariantLedger(); assertThrows(IllegalStateException.class,()->move(ledger,70,Double.NaN));
        assertEquals("transfer.finite",ledger.failedInvariant()); assertEquals(0,ledger.maxYCorrection());
    }
    @Test void rejectionNamesAndFirstFailureArePreserved() {
        var ledger=new CoreInvariantLedger(); ledger.scenario("after:blocked"); ledger.rejected("NO_SAFE_POSITION");
        assertEquals(java.util.List.of("after:blocked: NO_SAFE_POSITION"),ledger.rejectedScenarios());
        assertThrows(UnsupportedOperationException.class,()->ledger.rejectedScenarios().add("fake"));
        assertThrows(IllegalStateException.class,()->ledger.require(false,"inventory.slot","slot10"));
        assertThrows(IllegalStateException.class,()->ledger.require(false,"other","later"));
        assertEquals("inventory.slot",ledger.failedInvariant());
    }
    private static void move(CoreInvariantLedger ledger,double y,double destinationY) {
        ledger.transferred(-16.125,15.9375,y,123.25f,-37.5f,-16.125,15.9375,destinationY,123.25f,-37.5f);
    }
}
