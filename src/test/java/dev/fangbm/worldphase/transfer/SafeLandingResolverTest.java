package dev.fangbm.worldphase.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Real Vanilla BlockCollisions/VoxelShapes over a tiny block map; no copied collision math. */
class SafeLandingResolverTest {
    private ServerLevel target;
    private ServerPlayer player;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Vec3 original = new Vec3(-16.125, 80.5, 15.9375);

    @BeforeEach void setup() {
        target = mock(ServerLevel.class);
        when(target.getMinY()).thenReturn(-64);
        when(target.getMaxY()).thenReturn(319);
        when(target.getWorldBorder()).thenReturn(new WorldBorder());
        when(target.noEntityCollision(any(), any())).thenReturn(true);
        when(target.getBlockState(any())).thenAnswer(call -> state(call.getArgument(0)));
        var getter = mock(BlockGetter.class);
        when(getter.getBlockState(any())).thenAnswer(call -> state(call.getArgument(0)));
        when(target.getChunkForCollisions(anyInt(), anyInt())).thenReturn(getter);
        player = mock(ServerPlayer.class);
        when(player.position()).thenReturn(original);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        player.setBoundingBox(new AABB(original.x-.3,original.y,original.z-.3,original.x+.3,original.y+1.8,original.z+.3));
    }

    private BlockState state(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    private void layer(int y, BlockState state) {
        for (int x=-18;x<=-15;x++) for (int z=14;z<=17;z++) blocks.put(new BlockPos(x,y,z),state);
    }
    private void floor(int offset) { layer(80+offset, Blocks.STONE_SLAB.defaultBlockState()); }
    private Optional<Vec3> resolve() { return SafeLandingResolver.gameplay().resolve(player,target,original,TransferPolicy.BOUNDED_LANDING); }
    private boolean safe(Vec3 position) { return LandingSafety.isSafe(player,target,position,TransferPolicy.BOUNDED_LANDING); }

    @Test void fixedOrderHasOnlyNineUnscaledFractionPreservingCandidates() {
        var candidates = TransferPolicy.BOUNDED_LANDING.candidates(original);
        assertEquals(List.of(0,1,-1,2,-2,3,-3,4,-4), candidates.stream().map(p -> (int)(p.y-original.y)).toList());
        for (var p : candidates) { assertEquals(original.x,p.x); assertEquals(original.z,p.z); assertEquals(.5,p.y-Math.floor(p.y)); }
        assertFalse(TransferPolicy.BOUNDED_LANDING.permits(original,original.add(0,5,0)));
        assertFalse(TransferPolicy.BOUNDED_LANDING.permits(original,original.add(0,.25,0)));
        assertFalse(TransferPolicy.BOUNDED_LANDING.permits(original,original.add(.00001,0,0)));
        for(var p:TransferPolicy.BOUNDED_LANDING.candidates(new Vec3(-0.0,80.375,-0.0))) {
            assertEquals(Double.doubleToLongBits(-0.0),Double.doubleToLongBits(p.x));
            assertEquals(Double.doubleToLongBits(-0.0),Double.doubleToLongBits(p.z));
            assertEquals(.375,p.y-Math.floor(p.y));
        }
    }
    @Test void exactSafeIsPreferred() { floor(0); assertEquals(Optional.of(original),resolve()); }
    @ParameterizedTest @ValueSource(ints={1,-1,4,-4})
    void resolvesOnlySupportedCandidatePreservingFractions(int offset) {
        floor(offset); assertEquals(Optional.of(original.add(0,offset,0)),resolve());
    }
    @Test void upwardWinsEqualDistanceTie() {
        // Thin closed trapdoors leave both +/-1 full-height landing spaces clear.
        layer(79,Blocks.OAK_TRAPDOOR.defaultBlockState());
        layer(81,Blocks.OAK_TRAPDOOR.defaultBlockState());
        var point = new Vec3(original.x,80.1875,original.z);
        assertTrue(safe(point.add(0,-1,0))); assertTrue(safe(point.add(0,1,0)));
        assertEquals(Optional.of(point.add(0,1,0)),SafeLandingResolver.gameplay().resolve(player,target,point,TransferPolicy.BOUNDED_LANDING));
    }
    @ParameterizedTest @ValueSource(ints={5,-5})
    void rejectsSafeFloorOutsideBound(int offset) { floor(offset); assertTrue(resolve().isEmpty()); }
    @Test void allBlockedAndUnsupportedReject() {
        assertTrue(resolve().isEmpty());
        verify(target,times(9)).noEntityCollision(eq(player),any());
        for(int y=75;y<=86;y++) layer(y,Blocks.STONE.defaultBlockState());
        assertTrue(resolve().isEmpty());
    }
    @Test void insufficientHeadroomRejectsActualGeometry() { floor(0); layer(82,Blocks.STONE.defaultBlockState()); assertFalse(safe(original)); }
    @Test void slabTopIsSupportButAirGapIsNot() {
        floor(0); assertTrue(safe(original)); assertFalse(safe(original.add(0,.01,0)));
        assertFalse(safe(original.add(0,-.01,0)));
    }
    @Test void stairsUseTheirShapeAtFractionalAndFullHeight() {
        layer(80,Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        var low = new Vec3(-16.75,80.5,15.5);
        var high = new Vec3(-16.25,81,15.5);
        player.setBoundingBox(new AABB(original.x-.1,original.y,original.z-.1,original.x+.1,original.y+1.8,original.z+.1));
        assertTrue(safe(low)); assertTrue(safe(high)); assertFalse(safe(high.add(0,-.5,0)));
    }
    @ParameterizedTest @ValueSource(doubles={.2,.6,2.4})
    void bodyWidthAndBothChunkEdgesUseWholeBox(double width) {
        floor(0); player.setBoundingBox(new AABB(original.x-width/2,original.y,original.z-width/2,original.x+width/2,original.y+1.8,original.z+width/2));
        assertTrue(safe(original));
        blocks.put(new BlockPos(-17,81,16),Blocks.STONE.defaultBlockState());
        if(width>=.6) assertFalse(safe(original));
    }
    @Test void entityAndLiquidChecksRejectOtherwiseSupportedBody() {
        floor(0); when(target.noEntityCollision(any(),any())).thenReturn(false); assertTrue(resolve().isEmpty());
        when(target.noEntityCollision(any(),any())).thenReturn(true);
        when(target.containsAnyLiquid(any())).thenReturn(true); assertTrue(resolve().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"fire","soul_fire","magma","cactus","lava","campfire","berry","rose","powder","dripstone"})
    void knownHazardContactRejects(String name) {
        floor(0);
        var hazard = switch(name) {
            case "fire" -> Blocks.FIRE; case "soul_fire" -> Blocks.SOUL_FIRE; case "magma" -> Blocks.MAGMA_BLOCK;
            case "cactus" -> Blocks.CACTUS; case "lava" -> Blocks.LAVA; case "campfire" -> Blocks.CAMPFIRE;
            case "berry" -> Blocks.SWEET_BERRY_BUSH; case "rose" -> Blocks.WITHER_ROSE;
            case "powder" -> Blocks.POWDER_SNOW; default -> Blocks.POINTED_DRIPSTONE;
        };
        blocks.put(new BlockPos(-17,81,15),hazard.defaultBlockState()); assertFalse(safe(original));
    }
    @Test void worldBorderAndEntireBuildHeightAreChecked() {
        floor(0); target.getWorldBorder().setCenter(original.x,original.z); target.getWorldBorder().setSize(.4);
        assertTrue(resolve().isEmpty()); target.getWorldBorder().setSize(100);
        when(target.getMinY()).thenReturn(81); assertFalse(safe(original));
        when(target.getMinY()).thenReturn(-64); when(target.getMaxY()).thenReturn(81); assertFalse(safe(original));
    }
    @Test void hazardousSupportRejectsEvenWhenBodyIsClear() {
        layer(80,Blocks.MAGMA_BLOCK.defaultBlockState()); assertFalse(safe(original.add(0,.5,0)));
    }
    @Test void unlitCampfireSupportsUsingItsRealShape() {
        layer(80,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false));
        assertTrue(safe(new Vec3(original.x,80.4375,original.z)));
        layer(80,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true));
        assertFalse(safe(new Vec3(original.x,80.4375,original.z)));
    }
    @Test void outerEdgeContactAloneIsNotReasonableSupport() {
        blocks.put(new BlockPos(-17,80,15),Blocks.STONE_SLAB.defaultBlockState());
        assertFalse(safe(new Vec3(-15.8,80.5,15.5)));
    }
    @Test void exactPolicyKeepsOldUnsupportedOpenAirContract() {
        when(target.noCollision(any(),any())).thenReturn(true);
        assertTrue(LandingSafety.isSafe(player,target,original,TransferPolicy.EXACT_POSITION));
        assertEquals(List.of(original),TransferPolicy.EXACT_POSITION.candidates(original));
    }
}
