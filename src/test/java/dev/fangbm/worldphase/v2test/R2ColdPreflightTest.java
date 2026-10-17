package dev.fangbm.worldphase.v2test;
import dev.fangbm.worldphase.verification.v2.*;
import dev.fangbm.worldphase.verification.v2.r2.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
/** Fresh JVM for EACH seed; no Level, chunks or worlds. Timing outcomes are evidence, not speed assertions. */
@EnabledIfSystemProperty(named="worldphase.r2.coldSeed",matches="[012]")
class R2ColdPreflightTest {
    @Test void recordActualColdBudgetOutcomeWithoutRerunningFailedProfile()throws Exception{
        Path root=Path.of(System.getProperty("worldphase.projectRoot"));R2.verify(root);
        long registryWall=System.nanoTime(),registryCpu=R2Context.cpu();var fixture=new NativeV2Fixture();
        registryWall=System.nanoTime()-registryWall;registryCpu=R2Context.cpu()-registryCpu;
        long seed=R2.SEEDS[Integer.parseInt(System.getProperty("worldphase.r2.coldSeed"))];
        long wall=System.nanoTime(),cpu=R2Context.cpu(),allocation=R2Context.allocated();
        var state=fixture.state(fixture.vanilla,seed);var c=new R2Context(seed,R1.rawReference(state.router().continents()));
        long iw=System.nanoTime()-wall,ic=R2Context.cpu()-cpu,ia=R2Context.allocated()-allocation;c.chargeInitialization(ic,ia,iw);
        if(iw>25_000_000L)c.abort("BUDGET_EXCEEDED:INITIALIZATION_HARD_TICK_25MS");
        while(c.state==R2Context.State.NEW||c.state==R2Context.State.PREPARING){try{c.measuredStep();}catch(RuntimeException e){assertEquals(R2Context.State.FAILED,c.state);}}
        var report=R2Evidence.report(c,"fresh Gradle worker per development seed; registry bootstrap separate; no server tick gaps",iw,ic,ia);
        report.addProperty("registryBootstrapWallNanos",registryWall);report.addProperty("registryBootstrapCpuNanos",registryCpu);
        Path file=root.resolve("build/reports/r2-native-preflight/cold/seed-"+seed+".json");
        if(Files.exists(file))throw new IllegalStateException("Archive previous cold evidence before rerun");
        R2Evidence.write(file,report);
        assertFalse(report.get("worldGenerationAuthorized").getAsBoolean());c.close();assertEquals(0,c.rawEntries);
    }
}
